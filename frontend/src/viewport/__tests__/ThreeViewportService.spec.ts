import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { Cuboid, MobProjectModel } from '../../domain/MobProjectModel'

// jsdom no implementa un contexto WebGL real (`HTMLCanvasElement.getContext('webgl')`
// devuelve null) -- `new THREE.WebGLRenderer()` lanza fuera de un navegador
// real. Se reemplaza SOLO el renderer por un fake mínimo (domElement +
// setSize + render espiables); el resto de 'three' (Scene, Group,
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

  it('ticket 016: la cámara arranca en la posición/ángulo por defecto', () => {
    // Constructor ya llama resetCamera() -- se verifica el estado inicial,
    // sin llamar resetCamera() de nuevo, para probar el default real.
    expect(service.camera.position.x).toBeCloseTo(40, 9)
    expect(service.camera.position.y).toBeCloseTo(40, 9)
    expect(service.camera.position.z).toBeCloseTo(40, 9)
    expect(service.controls.target.x).toBe(0)
    expect(service.controls.target.y).toBeCloseTo(16, 9)
    expect(service.controls.target.z).toBe(0)
  })

  it('ticket 016: resetCamera() vuelve a la posición/ángulo por defecto tras moverla', () => {
    service.camera.position.set(100, 5, -30)
    service.controls.target.set(9, 9, 9)

    service.resetCamera()

    expect(service.camera.position.x).toBeCloseTo(40, 9)
    expect(service.camera.position.y).toBeCloseTo(40, 9)
    expect(service.camera.position.z).toBeCloseTo(40, 9)
    expect(service.controls.target.x).toBe(0)
    expect(service.controls.target.y).toBeCloseTo(16, 9)
    expect(service.controls.target.z).toBe(0)
  })

  it('ticket 016: setModel propaga selectedCuboidId a buildMobGroup (el mesh seleccionado recibe outline)', () => {
    const model = emptyModel('mob-uno')
    const cuboid: Cuboid = {
      id: 'cube-1',
      name: 'cube-1',
      boneId: 'bone-1',
      from: [-1, -1, -1],
      to: [1, 1, 1],
      origin: [0, 0, 0],
      rotation: [0, 0, 0],
      faces: {
        north: { uv: [0, 0, 0, 0], texture: null },
        south: { uv: [0, 0, 0, 0], texture: null },
        east: { uv: [0, 0, 0, 0], texture: null },
        west: { uv: [0, 0, 0, 0], texture: null },
        up: { uv: [0, 0, 0, 0], texture: null },
        down: { uv: [0, 0, 0, 0], texture: null },
      },
    }
    const modelWithCuboid = { ...model, cuboids: [cuboid] }

    service.setModel(modelWithCuboid, 'cube-1')

    const mobGroup = service.scene.children.find((c) => c.name === 'mob-uno')!
    const cubeMesh = mobGroup.children.find((c) => c.name === 'cube-1')!
    expect(cubeMesh.children.some((c) => c.name === 'selection-outline')).toBe(true)
  })

  it('ticket 018: setModel con selectedCuboidId reatachea transformControls al mesh nuevo de ese cuboid', () => {
    const model = emptyModel('mob-uno')
    const cuboid: Cuboid = {
      id: 'cube-1',
      name: 'cube-1',
      boneId: 'bone-1',
      from: [-1, -1, -1],
      to: [1, 1, 1],
      origin: [0, 0, 0],
      rotation: [0, 0, 0],
      faces: {
        north: { uv: [0, 0, 0, 0], texture: null },
        south: { uv: [0, 0, 0, 0], texture: null },
        east: { uv: [0, 0, 0, 0], texture: null },
        west: { uv: [0, 0, 0, 0], texture: null },
        up: { uv: [0, 0, 0, 0], texture: null },
        down: { uv: [0, 0, 0, 0], texture: null },
      },
    }
    const modelWithCuboid = { ...model, cuboids: [cuboid] }

    service.setModel(modelWithCuboid, 'cube-1')

    const mobGroup = service.scene.children.find((c) => c.name === 'mob-uno')!
    const cubeMesh = mobGroup.children.find((c) => c.name === 'cube-1')!
    expect(service.transformControls.object).toBe(cubeMesh)
  })

  it('ticket 018: setModel sin selección desatachea transformControls', () => {
    const model = emptyModel('mob-uno')
    service.setModel(model, null)
    expect(service.transformControls.object).toBeUndefined()
  })

  it('ticket 018: reatachea correctamente tras una segunda mutación (el mesh viejo queda huérfano)', () => {
    const model = emptyModel('mob-uno')
    const cuboid: Cuboid = {
      id: 'cube-1',
      name: 'cube-1',
      boneId: 'bone-1',
      from: [-1, -1, -1],
      to: [1, 1, 1],
      origin: [0, 0, 0],
      rotation: [0, 0, 0],
      faces: {
        north: { uv: [0, 0, 0, 0], texture: null },
        south: { uv: [0, 0, 0, 0], texture: null },
        east: { uv: [0, 0, 0, 0], texture: null },
        west: { uv: [0, 0, 0, 0], texture: null },
        up: { uv: [0, 0, 0, 0], texture: null },
        down: { uv: [0, 0, 0, 0], texture: null },
      },
    }
    const modelWithCuboid = { ...model, cuboids: [cuboid] }

    service.setModel(modelWithCuboid, 'cube-1')
    const firstMesh = service.transformControls.object
    service.setModel(modelWithCuboid, 'cube-1') // simula la mutación posterior a un drag -- rebuild completo

    const secondMesh = service.transformControls.object
    expect(secondMesh).not.toBe(firstMesh) // buildMobGroup siempre crea meshes nuevos
    expect(secondMesh).toBeDefined()
  })

  it('ticket 018: setTransformMode delega en transformControls.setMode', () => {
    service.setTransformMode('rotate')
    expect(service.transformControls.mode).toBe('rotate')
  })

  it('ticket 018: mientras se arrastra un gizmo (dragging-changed), OrbitControls se deshabilita', () => {
    expect(service.controls.enabled).toBe(true)

    service.transformControls.dispatchEvent({ type: 'dragging-changed', value: true })
    expect(service.controls.enabled).toBe(false)

    service.transformControls.dispatchEvent({ type: 'dragging-changed', value: false })
    expect(service.controls.enabled).toBe(true)
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
