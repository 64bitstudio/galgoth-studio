import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { nextTick } from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { Cuboid, MobProjectModel } from '../../domain/MobProjectModel'
import { useDraftModelStore } from '../../editor/draftModelStore'
import { useSelectionStore } from '../../editor/selectionStore'

// Ver nota en ThreeViewportService.spec.ts -- jsdom no tiene WebGL real.
vi.mock('three', async (importOriginal) => {
  const actual = await importOriginal<typeof import('three')>()
  class FakeWebGLRenderer {
    domElement = document.createElement('canvas')
    setSize = vi.fn()
    render = vi.fn()
  }
  return { ...actual, WebGLRenderer: FakeWebGLRenderer }
})

const { threeViewportService } = await import('../ThreeViewportService')
const { default: ThreeViewport } = await import('../ThreeViewport.vue')

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

const EMPTY_FACES = {
  north: { uv: [0, 0, 0, 0] as [number, number, number, number], texture: null },
  south: { uv: [0, 0, 0, 0] as [number, number, number, number], texture: null },
  east: { uv: [0, 0, 0, 0] as [number, number, number, number], texture: null },
  west: { uv: [0, 0, 0, 0] as [number, number, number, number], texture: null },
  up: { uv: [0, 0, 0, 0] as [number, number, number, number], texture: null },
  down: { uv: [0, 0, 0, 0] as [number, number, number, number], texture: null },
}

function cuboid(id: string): Cuboid {
  return { id, name: id, boneId: 'bone-1', from: [-1, -1, -1], to: [1, 1, 1], origin: [0, 0, 0], rotation: [0, 0, 0], faces: EMPTY_FACES }
}

describe('ThreeViewport.vue', () => {
  // ThreeViewport.vue registra sus listeners de selección/gizmo sobre el
  // `transformControls` SINGLETON de `threeViewportService` en `onMounted`
  // y los remueve en `onBeforeUnmount`. Si un test monta el componente y
  // nunca lo desmonta, ese listener queda vivo para SIEMPRE (el singleton
  // sobrevive entre tests) y se dispara también en tests posteriores con
  // su propio store (de una Pinia ya reemplazada) -- contaminación cruzada
  // real que se manifestaba como "moveCuboid rechazado: no existe cube-1"
  // en tests que nunca tocaban ese cuboid. Se centraliza el mount aquí para
  // garantizar que TODO wrapper se desmonte en `afterEach`.
  let wrapper: ReturnType<typeof mount> | null = null

  function mountViewport(): ReturnType<typeof mount> {
    wrapper = mount(ThreeViewport)
    return wrapper
  }

  beforeEach(() => {
    // ThreeViewport.vue usa useSelectionStore()/useDraftModelStore()
    // (tickets 017/018) -- necesita una Pinia activa incluso montado
    // fuera de una app real.
    setActivePinia(createPinia())
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = null
    threeViewportService.stopRenderLoop()
    vi.restoreAllMocks()
  })

  it('al montarse, adjunta el canvas compartido dentro de su contenedor y carga el modelo del draft store', () => {
    useDraftModelStore().load(emptyModel('mob-uno'))
    const w = mountViewport()

    expect(w.element.contains(threeViewportService.canvas)).toBe(true)
    expect(threeViewportService.scene.children.some((c) => c.name === 'mob-uno')).toBe(true)
  })

  it('al desmontarse, libera (detach) el canvas compartido', () => {
    useDraftModelStore().load(emptyModel('mob-uno'))
    const w = mountViewport()
    const canvas = threeViewportService.canvas

    w.unmount()
    wrapper = null // ya desmontado explícitamente -- que afterEach no lo repita

    expect(canvas.parentElement).toBeNull()
  })

  it('al cambiar el modelo del draft store, actualiza el mob en escena sin duplicarlo', async () => {
    const draft = useDraftModelStore()
    draft.load(emptyModel('mob-uno'))
    mountViewport()

    draft.load(emptyModel('mob-dos'))
    await nextTick()

    const mobGroups = threeViewportService.scene.children.filter(
      (c) => c.name === 'mob-uno' || c.name === 'mob-dos',
    )
    expect(mobGroups).toHaveLength(1)
    expect(mobGroups[0]!.name).toBe('mob-dos')
  })

  // VTU's trigger() no puede asignar clientX/clientY a un MouseEvent
  // sintético (son getters de solo lectura en jsdom) -- se despacha el
  // evento nativo directo sobre el elemento para controlar la posición.
  function dispatch(element: Element, type: string, clientX: number, clientY: number): void {
    element.dispatchEvent(new MouseEvent(type, { clientX, clientY, bubbles: true }))
  }

  it('ticket 017 AC #3: un click (sin arrastre) en el canvas selecciona el cuboid bajo el cursor en el store compartido', () => {
    useDraftModelStore().load(emptyModel('mob-uno'))
    const w = mountViewport()
    const selection = useSelectionStore()
    vi.spyOn(threeViewportService, 'pickCuboidIdAt').mockReturnValue('cube-1')

    dispatch(w.element, 'pointerdown', 100, 100)
    dispatch(w.element, 'click', 100, 100)

    expect(selection.selectedCuboidId).toBe('cube-1')
  })

  it('un click en vacío (pickCuboidIdAt devuelve null) deselecciona', () => {
    useDraftModelStore().load(emptyModel('mob-uno'))
    const w = mountViewport()
    const selection = useSelectionStore()
    selection.select('cube-previo')
    vi.spyOn(threeViewportService, 'pickCuboidIdAt').mockReturnValue(null)

    dispatch(w.element, 'pointerdown', 100, 100)
    dispatch(w.element, 'click', 100, 100)

    expect(selection.selectedCuboidId).toBeNull()
  })

  it('un arrastre de órbita (pointerdown lejos del click) NO dispara selección', () => {
    useDraftModelStore().load(emptyModel('mob-uno'))
    const w = mountViewport()
    const selection = useSelectionStore()
    const pickSpy = vi.spyOn(threeViewportService, 'pickCuboidIdAt').mockReturnValue('cube-1')

    dispatch(w.element, 'pointerdown', 0, 0)
    dispatch(w.element, 'click', 200, 200) // se movió >5px -> fue un drag de órbita

    expect(pickSpy).not.toHaveBeenCalled()
    expect(selection.selectedCuboidId).toBeNull()
  })

  // -- Ticket 018: gizmos de transformación --------------------------------

  describe('gizmos de transformación (ticket 018)', () => {
    function setupSelectedCuboid() {
      const draft = useDraftModelStore()
      draft.load({ ...emptyModel('mob-uno'), cuboids: [cuboid('cube-1')] })
      const selection = useSelectionStore()
      selection.select('cube-1')
      mountViewport()
      const controls = threeViewportService.transformControls
      return { draft, controls }
    }

    it('translate: al soltar el drag, mueve el cuboid por el delta MUNDIAL convertido a local', () => {
      const { draft, controls } = setupSelectedCuboid()
      controls.setMode('translate')
      controls.dispatchEvent({ type: 'mouseDown', mode: controls.mode }) // captura la posición inicial (0,0,0) del mesh
      controls.object!.position.set(5, 0, 0) // el mesh no tiene rotación de cadena -> delta local = delta mundial = (5,0,0)
      controls.dispatchEvent({ type: 'objectChange' })
      controls.dispatchEvent({ type: 'mouseUp', mode: controls.mode })

      // from=[-1,-1,-1] + delta[5,0,0] = [4,-1,-1]
      const moved = draft.model!.cuboids[0]!
      expect(moved.from[0]).toBeCloseTo(4, 9)
      expect(moved.to[0]).toBeCloseTo(6, 9)
    })

    it('scale: al soltar el drag, redimensiona el cuboid con el scale del gizmo', () => {
      const { draft, controls } = setupSelectedCuboid()
      controls.setMode('scale')
      controls.dispatchEvent({ type: 'mouseDown', mode: controls.mode })
      controls.object!.scale.set(2, 1, 1)
      controls.dispatchEvent({ type: 'objectChange' })
      controls.dispatchEvent({ type: 'mouseUp', mode: controls.mode })

      const resized = draft.model!.cuboids[0]!
      // from=[-1,-1,-1] to=[1,1,1] centro=[0,0,0] tamaño=[2,2,2] -> scale.x=2 => nuevo tamaño x=4
      expect(resized.from[0]).toBeCloseTo(-2, 9)
      expect(resized.to[0]).toBeCloseTo(2, 9)
    })

    it('rotate: al soltar el drag, suma el ángulo del eje dominante a la rotación del cuboid', () => {
      const { draft, controls } = setupSelectedCuboid()
      const typedControls = controls as unknown as { rotationAxis: { set: (x: number, y: number, z: number) => void }; rotationAngle: number }
      controls.setMode('rotate')
      controls.dispatchEvent({ type: 'mouseDown', mode: controls.mode })
      typedControls.rotationAxis.set(0, 0, 1)
      typedControls.rotationAngle = Math.PI / 2 // 90°
      controls.dispatchEvent({ type: 'objectChange' })
      controls.dispatchEvent({ type: 'mouseUp', mode: controls.mode })

      const rotated = draft.model!.cuboids[0]!
      expect(rotated.rotation[2]).toBeCloseTo(90, 6)
      expect(rotated.rotation[0]).toBe(0)
      expect(rotated.rotation[1]).toBe(0)
    })

    it('una operación rechazada (ej. scale a 0) revierte el mesh a los datos reales sin cambiar el modelo', () => {
      const { draft, controls } = setupSelectedCuboid()
      controls.setMode('scale')
      controls.dispatchEvent({ type: 'mouseDown', mode: controls.mode })
      controls.object!.scale.set(0, 1, 1) // scale 0 -> resizeCuboid lo rechaza
      controls.dispatchEvent({ type: 'objectChange' })
      controls.dispatchEvent({ type: 'mouseUp', mode: controls.mode })

      expect(draft.lastError).not.toBeNull()
      expect(draft.model!.cuboids[0]!.from).toEqual([-1, -1, -1]) // sin cambios
    })
  })
})
