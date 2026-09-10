import { DataTexture, Mesh, MeshStandardMaterial, Raycaster, Vector3 } from 'three'
import type { Face, Intersection } from 'three'
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest'
import type { Cuboid, MobProjectModel } from '../../domain/MobProjectModel'

// jsdom no implementa `HTMLCanvasElement.toBlob()` (requiere el paquete
// nativo `canvas`, no instalado aquí) -- se agrega un polyfill mínimo
// SOLO para estos tests, igual que el patrón ya usado para
// `HTMLDialogElement.showModal()` en los specs de modales.
beforeAll(() => {
  HTMLCanvasElement.prototype.toBlob = function fakeToBlob(callback: BlobCallback): void {
    callback(new Blob(['fake-png-bytes'], { type: 'image/png' }))
  }
})

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
    uv: { textureWidth: 64, textureHeight: 64, regions: [], reservations: [] },
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

  it('ticket 047: setModel forwardea atlasTexture a buildMobGroup -- el mesh usa esa textura como map', () => {
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
    const atlasTexture = new DataTexture(new Uint8ClampedArray(64 * 64 * 4), 64, 64)

    service.setModel(modelWithCuboid, null, atlasTexture)

    const mobGroup = service.scene.children.find((c) => c.name === 'mob-uno')!
    const cubeMesh = mobGroup.children.find((c) => c.name === 'cube-1') as InstanceType<typeof Mesh>
    // Ticket 049: `mesh.material` es un array de 6 slots (misma instancia
    // repetida) -- ver docstring de `buildMobScene.ts`.
    const material = (cubeMesh.material as InstanceType<typeof MeshStandardMaterial>[])[0]!
    expect(material.map).toBe(atlasTexture)
  })

  describe('ticket 049 (HU-25, Diseño técnico §14): pickCuboidFaceAt determinista', () => {
    const FACE_EMPTY_FACES = {
      north: { uv: [0, 0, 0, 0] as [number, number, number, number], texture: null },
      south: { uv: [0, 0, 0, 0] as [number, number, number, number], texture: null },
      east: { uv: [0, 0, 0, 0] as [number, number, number, number], texture: null },
      west: { uv: [0, 0, 0, 0] as [number, number, number, number], texture: null },
      up: { uv: [0, 0, 0, 0] as [number, number, number, number], texture: null },
      down: { uv: [0, 0, 0, 0] as [number, number, number, number], texture: null },
    }

    function cuboidFixture(rotation: [number, number, number] = [0, 0, 0]): Cuboid {
      return {
        id: 'cube-1',
        name: 'cube-1',
        boneId: 'bone-1',
        from: [-1, -1, -1],
        to: [1, 1, 1],
        origin: [0, 0, 0],
        rotation,
        faces: FACE_EMPTY_FACES,
      }
    }

    function fakeIntersection(mesh: Mesh, materialIndex: number, normal: Vector3): Intersection {
      return {
        distance: 1,
        point: new Vector3(),
        object: mesh,
        face: { a: 0, b: 1, c: 2, materialIndex, normal } as Face,
      } as Intersection
    }

    /** Construye el mesh real (vía `setModel`/`buildMobGroup`, sin mocks) para tener un `userData.faceNamesByGroup` real -- solo `Raycaster.intersectObjects` se mockea, nunca la resolución de la cara. */
    function buildCubeMesh(rotation: [number, number, number] = [0, 0, 0]): Mesh {
      const model = emptyModel('mob-uno')
      const modelWithCuboid = { ...model, cuboids: [cuboidFixture(rotation)] }
      service.setModel(modelWithCuboid, null)
      const mobGroup = service.scene.children.find((c) => c.name === 'mob-uno')!
      return mobGroup.children.find((c) => c.name === 'cube-1') as Mesh
    }

    it('resuelve {cuboidId, face} vía materialIndex -- índice 2 de BOX_GEOMETRY_FACE_ORDER es "up"', () => {
      const mesh = buildCubeMesh()
      vi.spyOn(Raycaster.prototype, 'intersectObjects').mockReturnValue([fakeIntersection(mesh, 2, new Vector3(0, 1, 0))])

      expect(service.pickCuboidFaceAt(10, 10)).toEqual({ cuboidId: 'cube-1', face: 'up' })
    })

    it('el resultado es el MISMO sin importar la rotación mundial del cuboid -- materialIndex es agnóstico a la rotación (AC: "incluyendo uno rotado")', () => {
      const mesh = buildCubeMesh([0, 90, 45])
      vi.spyOn(Raycaster.prototype, 'intersectObjects').mockReturnValue([fakeIntersection(mesh, 4, new Vector3(0, 0, 1))])

      expect(service.pickCuboidFaceAt(10, 10)).toEqual({ cuboidId: 'cube-1', face: 'south' })
    })

    it('AC del ticket: una normal reportada DELIBERADAMENTE discrepante (materialIndex resuelve "up", la normal reportada es la de "east") NUNCA cambia el resultado -- la normal es solo assert/validación dev-only', () => {
      const mesh = buildCubeMesh()
      const warnSpy = vi.spyOn(console, 'warn').mockImplementation(() => {})
      // materialIndex=2 ("up", normal local esperada (0,1,0)) pero la normal reportada es (1,0,0) -- la de "east". Discrepancia forzada a propósito.
      vi.spyOn(Raycaster.prototype, 'intersectObjects').mockReturnValue([fakeIntersection(mesh, 2, new Vector3(1, 0, 0))])

      const pick = service.pickCuboidFaceAt(10, 10)

      expect(pick).toEqual({ cuboidId: 'cube-1', face: 'up' }) // el resultado NO cambia por la discrepancia de normal
      expect(warnSpy).toHaveBeenCalledTimes(1)
      expect(warnSpy.mock.calls[0]![0]).toContain("'up'")
    })

    it('con un cuboid rotado en el MUNDO y una normal LOCALMENTE correcta, no dispara ninguna discrepancia -- Three.js reporta `face.normal` en espacio LOCAL, nunca compuesto con matrixWorld (ver FACE_LOCAL_NORMALS)', () => {
      const mesh = buildCubeMesh([0, 90, 0]) // cuboid rotado 90° en el mundo
      const warnSpy = vi.spyOn(console, 'warn').mockImplementation(() => {})
      // Normal LOCAL real de "up" (materialIndex=2) es SIEMPRE (0,1,0), sin importar la rotación mundial del mesh.
      vi.spyOn(Raycaster.prototype, 'intersectObjects').mockReturnValue([fakeIntersection(mesh, 2, new Vector3(0, 1, 0))])

      const pick = service.pickCuboidFaceAt(10, 10)

      expect(pick).toEqual({ cuboidId: 'cube-1', face: 'up' })
      expect(warnSpy).not.toHaveBeenCalled()
    })

    it('sin intersección (click en vacío), devuelve null -- igual criterio que pickCuboidIdAt', () => {
      buildCubeMesh()
      vi.spyOn(Raycaster.prototype, 'intersectObjects').mockReturnValue([])

      expect(service.pickCuboidFaceAt(10, 10)).toBeNull()
    })

    it('un hit sobre un objeto sin userData.cuboidId (p. ej. un marcador de pivote de bone) se descarta, igual que pickCuboidIdAt', () => {
      const mesh = buildCubeMesh()
      const pivotMarker = new Mesh()
      vi.spyOn(Raycaster.prototype, 'intersectObjects').mockReturnValue([
        { distance: 0.5, point: new Vector3(), object: pivotMarker, face: { a: 0, b: 1, c: 2, materialIndex: 0, normal: new Vector3(1, 0, 0) } as Face },
        fakeIntersection(mesh, 2, new Vector3(0, 1, 0)),
      ])

      expect(service.pickCuboidFaceAt(10, 10)).toEqual({ cuboidId: 'cube-1', face: 'up' })
    })
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

  it('ticket 023: captureThumbnail devuelve un PNG y restaura la cámara del usuario al terminar', async () => {
    service.camera.position.set(100, 5, -30)
    service.controls.target.set(9, 9, 9)
    service.camera.lookAt(service.controls.target)

    const renderPositions: { x: number; y: number; z: number }[] = []
    vi.mocked(service.renderer.render).mockImplementation(() => {
      const { x, y, z } = service.camera.position
      renderPositions.push({ x, y, z })
    })

    const blob = await service.captureThumbnail()

    expect(blob).toBeInstanceOf(Blob)
    expect(blob.type).toBe('image/png')
    // El primer render (la captura en sí) ocurre con la cámara en el
    // ángulo fijo de referencia, NUNCA con la del usuario.
    expect(renderPositions[0]!.x).toBeCloseTo(40, 9)
    expect(renderPositions[0]!.y).toBeCloseTo(40, 9)
    expect(renderPositions[0]!.z).toBeCloseTo(40, 9)
    // La vista del usuario no debe quedar "saltada" tras capturar.
    expect(service.camera.position.x).toBeCloseTo(100, 9)
    expect(service.camera.position.y).toBeCloseTo(5, 9)
    expect(service.camera.position.z).toBeCloseTo(-30, 9)
    expect(service.controls.target.x).toBeCloseTo(9, 9)
    expect(service.controls.target.y).toBeCloseTo(9, 9)
    expect(service.controls.target.z).toBeCloseTo(9, 9)
  })

  it('ticket 023: captureThumbnail restaura la cámara incluso si toBlob falla', async () => {
    service.camera.position.set(100, 5, -30)
    service.controls.target.set(9, 9, 9)
    const originalToBlob = HTMLCanvasElement.prototype.toBlob
    HTMLCanvasElement.prototype.toBlob = function failingToBlob(callback: BlobCallback): void {
      callback(null)
    }

    await expect(service.captureThumbnail()).rejects.toThrow()

    expect(service.camera.position.x).toBeCloseTo(100, 9)
    expect(service.controls.target.x).toBeCloseTo(9, 9)
    HTMLCanvasElement.prototype.toBlob = originalToBlob
  })
})
