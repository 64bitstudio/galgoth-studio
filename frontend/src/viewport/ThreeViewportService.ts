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
 *
 * Ticket 049 (HU-25, Diseño técnico §14): `setModel` gana un 4to
 * parámetro opcional `selectedFace`, forwardeado a `buildMobGroup` (ver
 * su docstring). Además, `pickCuboidFaceAt(clientX, clientY)` resuelve
 * `{cuboidId, face}` de forma DETERMINISTA a partir de
 * `intersection.face.materialIndex` (nunca de la normal) -- comparte el
 * raycasting NDC con `pickCuboidIdAt` (ticket 017, sin cambios de
 * comportamiento) vía `raycastMobChildren`.
 */
import {
  AmbientLight,
  DirectionalLight,
  type Face,
  GridHelper,
  Group,
  type Intersection,
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
import type { FaceName, MobProjectModel } from '../domain/MobProjectModel'
import { type CuboidFaceRef, FACE_LOCAL_NORMALS } from './textureUvMapping'

const DEFAULT_CAMERA_POSITION = new Vector3(40, 40, 40)
const DEFAULT_CAMERA_TARGET = new Vector3(0, 16, 0)
const GRID_SIZE = 64
const GRID_DIVISIONS = 16
// Umbral de similitud (dot product de vectores unitarios) para la
// validación dev-only de la normal -- 1.0 es "idéntica"; un valor alto
// pero no exigente asegura que solo se avisa ante una discrepancia real,
// nunca por el margen de precisión de punto flotante de una intersección
// real (ver `validateFaceNormalMatchesResolvedFace`).
const FACE_NORMAL_VALIDATION_DOT_THRESHOLD = 0.9

/**
 * Ticket 049: valida en modo desarrollo que la normal LOCAL reportada por
 * Three.js (`intersection.face.normal`, espacio de OBJETO -- ver
 * `FACE_LOCAL_NORMALS` en `textureUvMapping.ts`) coincide aproximadamente
 * con la normal esperada del `FaceName` YA resuelto por `materialIndex`.
 * Deliberadamente solo un `console.warn` -- nunca cambia ni descarta el
 * resultado de `pickCuboidFaceAt` (AC del ticket: la normal es
 * fallback/validación, jamás el mecanismo primario que decide la cara).
 */
function validateFaceNormalMatchesResolvedFace(face: FaceName, reportedFace: Face): void {
  const [ex, ey, ez] = FACE_LOCAL_NORMALS[face]
  const expected = new Vector3(ex, ey, ez)
  const dot = expected.dot(reportedFace.normal)
  if (dot < FACE_NORMAL_VALIDATION_DOT_THRESHOLD) {
    console.warn(
      `pickCuboidFaceAt: la normal reportada por Three.js no coincide con la cara '${face}' resuelta por materialIndex (dot=${dot.toFixed(3)}) -- validación dev-only, el resultado devuelto NO cambia.`,
    )
  }
}

export class ThreeViewportService {
  readonly renderer: WebGLRenderer
  readonly scene: Scene
  readonly camera: PerspectiveCamera
  readonly controls: OrbitControls
  readonly transformControls: TransformControls

  private currentMobGroup: Group | null = null
  private animationHandle: number | null = null
  private readonly raycaster = new Raycaster()
  /**
   * Post-074 (hallazgo real del PO -- "si abro el sidebar se rompe todo",
   * reportado sobre la tab Textura): `resizeToContainer` solo se llamaba
   * al montar (`attachTo`) o al arrastrar el splitter del preview 3D --
   * nunca cuando el CONTENEDOR cambiaba de tamaño por otra razón (ej.
   * `GSidebar.vue` expandiéndose/colapsando, que angosta/ensancha
   * `.mob-editor`/`.texture-canvas` sin que nada dentro de este servicio
   * se enterara). El `<canvas>` de Three.js no tiene ningún CSS que lo
   * fuerce a 100% de su contenedor (`resizeToContainer` llama
   * `renderer.setSize(w, h, false)` -- el `false` es a propósito, ver su
   * docstring -- así que sus atributos `width`/`height` HTML quedan
   * fijos al tamaño del contenedor en el momento del último resize real,
   * y el canvas se ve recortado/desalineado en cuanto ese contenedor
   * cambia de tamaño sin que nadie vuelva a llamar `resizeToContainer`.
   *
   * Fix en el servicio compartido (no en cada componente que lo consume)
   * a propósito: TODAS las pantallas que reutilizan este singleton
   * (`ThreeViewport.vue` en Modelo, `TextureCanvas.vue` en Textura,
   * `GenerationPreviewViewport.vue`) quedan cubiertas por el mismo
   * `ResizeObserver`, sin duplicar la lógica de resize en cada una.
   */
  private resizeObserver: ResizeObserver | null = null

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

  /**
   * Mueve el canvas compartido al contenedor dado (nunca crea uno nuevo).
   * Post-074: además observa el tamaño de `container` (`ResizeObserver`)
   * mientras esté attacheado -- cualquier cambio de tamaño futuro (sidebar
   * expandiéndose/colapsando, splitter, resize de ventana) re-sincroniza
   * el renderer/cámara automáticamente, sin depender de que cada pantalla
   * consumidora recuerde llamar `resizeToContainer` a mano.
   */
  attachTo(container: HTMLElement): void {
    container.appendChild(this.canvas)
    this.resizeToContainer(container)
    this.resizeObserver?.disconnect()
    this.resizeObserver = new ResizeObserver(() => this.resizeToContainer(container))
    this.resizeObserver.observe(container)
  }

  detach(): void {
    this.stopRenderLoop()
    this.resizeObserver?.disconnect()
    this.resizeObserver = null
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
  setModel(
    model: MobProjectModel,
    selectedCuboidId?: string | null,
    atlasTexture?: Texture | null,
    selectedFace?: CuboidFaceRef | null,
  ): void {
    if (this.currentMobGroup) {
      this.scene.remove(this.currentMobGroup)
    }
    this.currentMobGroup = buildMobGroup(model, selectedCuboidId, atlasTexture, selectedFace)
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
    const hit = this.raycastMobChildren(clientX, clientY).find(
      (intersection) => typeof intersection.object.userData.cuboidId === 'string',
    )
    return hit ? (hit.object.userData.cuboidId as string) : null
  }

  /**
   * Ticket 049 (HU-25, Diseño técnico §14): resuelve `{cuboidId, face}` de
   * forma DETERMINISTA usando `intersection.face.materialIndex` (índice de
   * grupo nativo de Three.js -- ver `mesh.userData.faceNamesByGroup`,
   * `buildMobScene.ts`) para indexar el `FaceName` correspondiente. Jamás
   * calcula nada a partir de `intersection.face.normal` -- esa normal solo
   * se usa como assert/validación en modo desarrollo
   * (`validateFaceNormalMatchesResolvedFace`), nunca decide el resultado.
   */
  pickCuboidFaceAt(clientX: number, clientY: number): CuboidFaceRef | null {
    const hit = this.raycastMobChildren(clientX, clientY).find(
      (intersection) => typeof intersection.object.userData.cuboidId === 'string',
    )
    if (!hit) {
      return null
    }
    const faceNamesByGroup = hit.object.userData.faceNamesByGroup as FaceName[] | undefined
    const materialIndex = hit.face?.materialIndex
    const face = faceNamesByGroup && materialIndex !== undefined ? faceNamesByGroup[materialIndex] : undefined
    if (!face) {
      return null
    }
    if (import.meta.env.DEV && hit.face) {
      validateFaceNormalMatchesResolvedFace(face, hit.face)
    }
    return { cuboidId: hit.object.userData.cuboidId as string, face }
  }

  /** Raycasting NDC compartido por `pickCuboidIdAt`/`pickCuboidFaceAt` -- mismo cálculo de coordenadas de pantalla a NDC, mismo alcance (hijos directos del grupo del mob, no recursivo). */
  private raycastMobChildren(clientX: number, clientY: number): Intersection[] {
    if (!this.currentMobGroup) {
      return []
    }
    const rect = this.canvas.getBoundingClientRect()
    const ndc = new Vector2(
      ((clientX - rect.left) / rect.width) * 2 - 1,
      -((clientY - rect.top) / rect.height) * 2 + 1,
    )
    this.raycaster.setFromCamera(ndc, this.camera)
    return this.raycaster.intersectObjects(this.currentMobGroup.children, false)
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
