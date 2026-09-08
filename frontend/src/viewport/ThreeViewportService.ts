/**
 * Único renderer/canvas Three.js del proceso -- las pantallas que
 * necesiten el viewport (editor de modelo, resultado, dev harness de
 * este ticket) reutilizan esta misma instancia moviendo el canvas entre
 * contenedores (`attachTo`) en vez de instanciar un `WebGLRenderer`
 * nuevo cada vez: los navegadores limitan la cantidad de contextos WebGL
 * simultáneos (ver `docs/definiciones/galgoth-studio-mvp.md` Diseño
 * técnico §8, y AC #3 del ticket 008). Sienta la base para 016/023.
 */
import { AmbientLight, DirectionalLight, Group, PerspectiveCamera, Scene, WebGLRenderer } from 'three'
import { buildMobGroup } from './buildMobScene'
import type { MobProjectModel } from '../domain/MobProjectModel'

export class ThreeViewportService {
  readonly renderer: WebGLRenderer
  readonly scene: Scene
  readonly camera: PerspectiveCamera

  private currentMobGroup: Group | null = null
  private animationHandle: number | null = null

  constructor() {
    this.renderer = new WebGLRenderer({ antialias: true })
    this.scene = new Scene()
    this.camera = new PerspectiveCamera(50, 1, 0.1, 1000)

    // Iluminación fija del viewport compartido -- se crea una única vez
    // aquí (no en cada componente que lo consume) para no duplicar luces
    // cada vez que una pantalla se monta/desmonta y reutiliza el singleton.
    this.scene.add(new AmbientLight(0xffffff, 0.6))
    const keyLight = new DirectionalLight(0xffffff, 0.8)
    keyLight.position.set(1, 2, 3)
    this.scene.add(keyLight)
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
    this.canvas.parentElement?.removeChild(this.canvas)
  }

  resizeToContainer(container: HTMLElement): void {
    const width = container.clientWidth
    const height = container.clientHeight || 1
    this.renderer.setSize(width, height, false)
    this.camera.aspect = width / height
    this.camera.updateProjectionMatrix()
  }

  /** Reemplaza el mob actualmente en escena (si lo había) por el modelo dado. */
  setModel(model: MobProjectModel): void {
    if (this.currentMobGroup) {
      this.scene.remove(this.currentMobGroup)
    }
    this.currentMobGroup = buildMobGroup(model)
    this.scene.add(this.currentMobGroup)
  }

  startRenderLoop(): void {
    if (this.animationHandle !== null) {
      return
    }
    const renderFrame = (): void => {
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
