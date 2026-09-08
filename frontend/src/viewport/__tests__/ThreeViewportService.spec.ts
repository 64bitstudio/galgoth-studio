import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { MobProjectModel } from '../../domain/MobProjectModel'

// jsdom no implementa un contexto WebGL real (`HTMLCanvasElement.getContext('webgl')`
// devuelve null) -- `new THREE.WebGLRenderer()` lanza fuera de un navegador
// real. Se reemplaza SOLO el renderer por un fake mínimo (domElement +
// setSize + render espiables); todo lo demás de 'three' (Scene, Group,
// PerspectiveCamera, Quaternion, matrices...) se usa REAL, sin mock --
// así se prueba la lógica real del servicio (attach/detach/setModel/loop),
// no una simulación de Three.js completa.
vi.mock('three', async (importOriginal) => {
  const actual = await importOriginal<typeof import('three')>()
  class FakeWebGLRenderer {
    domElement = document.createElement('canvas')
    setSize = vi.fn()
    render = vi.fn()
  }
  return { ...actual, WebGLRenderer: FakeWebGLRenderer }
})

const { ThreeViewportService, threeViewportService } = await import('../ThreeViewportService')

function emptyModel(name: string): MobProjectModel {
  return {
    mobId: 'test-mob',
    projectId: 'test-project',
    name,
    baseType: 'humanoid',
    units: 'minecraft_pixels',
    bones: [],
    cuboids: [],
    texture: { width: 64, height: 64, storageKey: null },
    uv: { textureWidth: 64, textureHeight: 64, regions: [] },
    animations: [],
    exportSettings: { preferredFormatVersion: 'v5' },
    referenceImages: [],
  }
}

describe('ThreeViewportService', () => {
  let service: InstanceType<typeof ThreeViewportService>

  beforeEach(() => {
    service = new ThreeViewportService()
  })

  afterEach(() => {
    service.stopRenderLoop()
    vi.restoreAllMocks()
  })

  it('exporta una instancia singleton compartida', () => {
    expect(threeViewportService).toBeInstanceOf(ThreeViewportService)
  })

  it('attachTo mueve el canvas compartido al contenedor dado', () => {
    const container = document.createElement('div')
    service.attachTo(container)
    expect(container.contains(service.canvas)).toBe(true)
  })

  it('attachTo reutiliza el MISMO canvas al reatachear a otro contenedor (nunca crea uno nuevo)', () => {
    const containerA = document.createElement('div')
    const containerB = document.createElement('div')
    service.attachTo(containerA)
    const canvasRef = service.canvas

    service.attachTo(containerB)

    expect(service.canvas).toBe(canvasRef)
    expect(containerB.contains(canvasRef)).toBe(true)
    expect(containerA.contains(canvasRef)).toBe(false)
  })

  it('detach quita el canvas de su contenedor y detiene el render loop', () => {
    const container = document.createElement('div')
    const rafSpy = vi.spyOn(globalThis, 'requestAnimationFrame').mockReturnValue(7)
    const cafSpy = vi.spyOn(globalThis, 'cancelAnimationFrame').mockImplementation(() => {})
    service.attachTo(container)
    service.startRenderLoop()

    service.detach()

    expect(container.contains(service.canvas)).toBe(false)
    expect(cafSpy).toHaveBeenCalledWith(7)
    rafSpy.mockRestore()
  })

  it('setModel reemplaza el mob anterior en la escena -- nunca acumula grupos viejos', () => {
    service.setModel(emptyModel('mob-uno'))
    service.setModel(emptyModel('mob-dos'))

    const mobGroups = service.scene.children.filter((child) => child.name === 'mob-uno' || child.name === 'mob-dos')
    expect(mobGroups).toHaveLength(1)
    expect(mobGroups[0]!.name).toBe('mob-dos')
  })

  it('startRenderLoop no arranca un segundo loop si ya hay uno corriendo', () => {
    const rafSpy = vi.spyOn(globalThis, 'requestAnimationFrame').mockReturnValue(1)

    service.startRenderLoop()
    service.startRenderLoop()

    expect(rafSpy).toHaveBeenCalledTimes(1)
  })

  it('resizeToContainer ajusta el tamaño del renderer y el aspect ratio de la cámara', () => {
    const container = document.createElement('div')
    Object.defineProperty(container, 'clientWidth', { value: 800, configurable: true })
    Object.defineProperty(container, 'clientHeight', { value: 400, configurable: true })

    service.resizeToContainer(container)

    expect(service.renderer.setSize).toHaveBeenCalledWith(800, 400, false)
    expect(service.camera.aspect).toBe(2)
  })
})
