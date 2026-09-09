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
 */
import { AmbientLight, DirectionalLight, GridHelper, Group, PerspectiveCamera, Scene, Vector3, WebGLRenderer } from 'three'
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js'
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

  private currentMobGroup: Group | null = null
  private animationHandle: number | null = null

  constructor() {
    this.renderer = new WebGLRenderer({ antialias: true })
    this.scene = new Scene()
    this.camera = new PerspectiveCamera(50, 1, 0.1, 1000)
    this.controls = new OrbitControls(this.camera, this.renderer.domElement)
    this.controls.enableDamping = true

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

  /** Reemplaza el mob actualmente en escena (si lo había) por el modelo dado. */
  setModel(model: MobProjectModel, selectedCuboidId?: string | null): void {
    if (this.currentMobGroup) {
      this.scene.remove(this.currentMobGroup)
    }
    this.currentMobGroup = buildMobGroup(model, selectedCuboidId)
    this.scene.add(this.currentMobGroup)
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
}

/** Instancia compartida real -- ver docstring de la clase. */
export const threeViewportService = new ThreeViewportService()
