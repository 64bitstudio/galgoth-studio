/**
 * Único renderer/canvas Three.js del proceso -- las pantallas que
 * necesiten el viewport (editor de modelo, resultado, dev harness de
 * este ticket) reutilizan esta misma instancia moviendo el canvas entre
 * contenedores (`attachTo`) en vez de instanciar un `WebGLRenderer`
 * nuevo cada vez: los navegadores limitan la cantidad de contextos WebGL
 * simultáneos (ver `docs/definiciones/galgoth-studio-mvp.md` Diseño
 * técnico §8, y AC #3 del ticket 008). Sienta la base para 016/023.
 *
 * Ticket 016: cámara orbital (`OrbitControls`), grid de piso y reset de
 * cámara -- esta lógica vive en el servicio (no en el componente Vue)
 * para que cualquier pantalla que reutilice el singleton herede el mismo
 * comportamiento de cámara sin reconfigurarlo.
 *
 * Ticket 018: gizmos de transformación (`TransformControls`) sobre el
 * cuboid seleccionado. Como `setModel` RECONSTRUYE el grupo del mob
 * completo (mismos meshes nunca se reutilizan entre llamadas), cada
 * `setModel` reatachea `transformControls` al mesh NUEVO que corresponda
 * al cuboid seleccionado -- si se dejara el mesh viejo, el gizmo quedaría
 * apuntando a un objeto huérfano ya removido de la escena.
 *
 * Ticket 047: `setModel` gana un 3er parámetro opcional `atlasTexture`
 * (HU-26) -- forwardeado tal cual a `buildMobGroup`. Los callers que no
 * lo pasan (`ThreeViewport.vue`/`GenerationPreviewViewport.vue`, tab
 * Modelo) siguen viendo el gris plano de siempre, cero cambio de
 * comportamiento.
 */
import {
  AmbientLight,
  DirectionalLight,
  GridHelper,
  Group,
  Object3D,
  PerspectiveCamera,
  Raycaster,
  Scene,
  type Texture,
  Vector2,
  Vector3,
  WebGLRenderer,
} from 'three'
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js'
import { TransformControls } from 'three/examples/jsm/controls/TransformControls.js'
import { buildMobGroup } from './buildMobScene'
import type { MobProjectModel } from '../domain/MobProjectModel'

const DEFAULT_CAMERA_POSITION = new Vector3(40, 40, 40)
const DEFAULT_CAMERA_TARGET = new Vector3(0, 16, 0)
const GRID_SIZE = 64
const GRID_DIVISIONS = 16

export class ThreeViewportService {
  readonly renderer: WebGLRenderer
  readonly scene: Scene
  readonly camera: PerspectiveCamera
  readonly controls: OrbitControls
  readonly transformControls: TransformControls

  private currentMobGroup: Group | null = null
  private animationHandle: number | null = null
  private readonly raycaster = new Raycaster()

  constructor() {
    this.renderer = new WebGLRenderer({ antialias: true })
    this.scene = new Scene()
    this.camera = new PerspectiveCamera(50, 1, 0.1, 1000)
    this.controls = new OrbitControls(this.camera, this.renderer.domElement)
    this.controls.enableDamping = true

    this.transformControls = new TransformControls(this.camera, this.renderer.domElement)
    this.transformControls.setSpace('local') // ejes del propio cuboid, no ejes globales -- ver docstring de la clase.
    this.scene.add(this.transformControls.getHelper())
    // Mientras se arrastra un gizmo, OrbitControls NO debe orbitar la
    // cámara con el mismo drag -- competirían por el mismo puntero.
    this.transformControls.addEventListener('dragging-changed', (event) => {
      this.controls.enabled = !event.value
    })

    // Iluminación y grid fijos del viewport compartido -- se crean una
    // única vez aquí (no en cada componente que lo consume) para no
    // duplicarlos cada vez que una pantalla se monta/desmonta y reutiliza
    // el singleton.
    this.scene.add(new AmbientLight(0xffffff, 0.6))
    const keyLight = new DirectionalLight(0xffffff, 0.8)
    keyLight.position.set(1, 2, 3)
    this.scene.add(keyLight)
    this.scene.add(new GridHelper(GRID_SIZE, GRID_DIVISIONS))

    this.resetCamera()
  }

  /** 'translate' | 'rotate' | 'scale' -- ver `TransformControls.setMode`. */
  setTransformMode(mode: 'translate' | 'rotate' | 'scale'): void {
    this.transformControls.setMode(mode)
  }

  get canvas(): HTMLCanvasElement {
    return this.renderer.domElement
  }

  /** Mueve el canvas compartido al contenedor dado (nunca crea uno nuevo). */
  attachTo(container: HTMLElement): void {
    container.appendChild(this.canvas)
    this.resizeToContainer(container)
  }

  detach(): void {
    this.stopRenderLoop()
    this.canvas.remove()
  }

  resizeToContainer(container: HTMLElement): void {
    const width = container.clientWidth
    const height = container.clientHeight || 1
    this.renderer.setSize(width, height, false)
    this.camera.aspect = width / height
    this.camera.updateProjectionMatrix()
  }

  /** Vuelve la cámara a la posición/ángulo inicial por defecto (AC del ticket 016). */
  resetCamera(): void {
    this.camera.position.copy(DEFAULT_CAMERA_POSITION)
    this.controls.target.copy(DEFAULT_CAMERA_TARGET)
    this.camera.lookAt(this.controls.target)
    this.controls.update()
  }

  /**
   * Reemplaza el mob actualmente en escena (si lo había) por el modelo
   * dado, y reatachea `transformControls` al mesh nuevo del cuboid
   * seleccionado (o lo desatachea si no hay selección) -- ver docstring
   * de la clase sobre por qué esto es necesario en cada llamada.
   */
  setModel(model: MobProjectModel, selectedCuboidId?: string | null, atlasTexture?: Texture | null): void {
    if (this.currentMobGroup) {
      this.scene.remove(this.currentMobGroup)
    }
    this.currentMobGroup = buildMobGroup(model, selectedCuboidId, atlasTexture)
    this.scene.add(this.currentMobGroup)

    const selectedMesh = selectedCuboidId ? this.findCuboidMesh(selectedCuboidId) : undefined
    if (selectedMesh) {
      this.transformControls.attach(selectedMesh)
    } else {
      this.transformControls.detach()
    }
  }

  private findCuboidMesh(cuboidId: string): Object3D | undefined {
    return this.currentMobGroup?.children.find((child) => child.userData.cuboidId === cuboidId)
  }

  /**
   * Ticket 017: raycasting desde coordenadas de pantalla (`event.clientX/Y`)
   * contra los cuboids actualmente en escena. Devuelve el `cuboid.id` del
   * más cercano bajo el cursor, o `null` si no hay ninguno (click en vacío
   * -- el caller lo interpreta como "deseleccionar"). Solo mira hijos
   * DIRECTOS del grupo del mob (no recursivo): así nunca compite con el
   * outline de selección (hijo del mesh, un nivel más profundo) ni con
   * nada que no sea un cuboid real -- los marcadores de pivote de bone no
   * tienen `userData.cuboidId`, se descartan aunque el rayo los toque.
   */
  pickCuboidIdAt(clientX: number, clientY: number): string | null {
    if (!this.currentMobGroup) {
      return null
    }
    const rect = this.canvas.getBoundingClientRect()
    const ndc = new Vector2(
      ((clientX - rect.left) / rect.width) * 2 - 1,
      -((clientY - rect.top) / rect.height) * 2 + 1,
    )
    this.raycaster.setFromCamera(ndc, this.camera)
    const hit = this.raycaster
      .intersectObjects(this.currentMobGroup.children, false)
      .find((intersection) => typeof intersection.object.userData.cuboidId === 'string')
    return hit ? (hit.object.userData.cuboidId as string) : null
  }

  startRenderLoop(): void {
    if (this.animationHandle !== null) {
      return
    }
    const renderFrame = (): void => {
      this.controls.update() // requerido por enableDamping
      this.renderer.render(this.scene, this.camera)
      this.animationHandle = requestAnimationFrame(renderFrame)
    }
    renderFrame()
  }

  stopRenderLoop(): void {
    if (this.animationHandle !== null) {
      cancelAnimationFrame(this.animationHandle)
      this.animationHandle = null
    }
  }

  /**
   * Ticket 023: captura un PNG del mob actualmente en escena desde el
   * ángulo fijo de referencia (misma posición/target que `resetCamera`,
   * ver diseño "vista fija (ángulo isométrico, iluminación estándar)" en
   * `docs/definiciones/galgoth-studio-mvp.md`). No usa una cámara/escena
   * offscreen aparte: reutiliza el mismo renderer/escena singleton
   * (docstring de la clase) para no abrir un segundo contexto WebGL,
   * moviendo la cámara al ángulo fijo solo por el instante del render y
   * restaurándola después para que la vista del usuario no salte.
   */
  async captureThumbnail(): Promise<Blob> {
    const previousPosition = this.camera.position.clone()
    const previousTarget = this.controls.target.clone()

    try {
      this.camera.position.copy(DEFAULT_CAMERA_POSITION)
      this.controls.target.copy(DEFAULT_CAMERA_TARGET)
      this.camera.lookAt(this.controls.target)
      this.controls.update()
      this.renderer.render(this.scene, this.camera)

      return await new Promise<Blob>((resolve, reject) => {
        this.canvas.toBlob((blob) => {
          if (blob) {
            resolve(blob)
          } else {
            reject(new Error('No se pudo generar el thumbnail (toBlob devolvió null).'))
          }
        }, 'image/png')
      })
    } finally {
      this.camera.position.copy(previousPosition)
      this.controls.target.copy(previousTarget)
      this.camera.lookAt(this.controls.target)
      this.controls.update()
      this.renderer.render(this.scene, this.camera)
    }
  }
}

/** Instancia compartida real -- ver docstring de la clase. */
export const threeViewportService = new ThreeViewportService()
