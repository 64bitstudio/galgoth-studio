import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { nextTick } from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { Cuboid, MobProjectModel } from '../../domain/MobProjectModel'
import { useDraftModelStore } from '../../editor/draftModelStore'
import { useGeometryApplyStore } from '../../editor/geometryApplyStore'
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

// Ticket 043: Resize (modo 'scale' del gizmo) pasa a ser server-side
// (`POST /geometry/apply`, vía `geometryApplyStore`) -- se mockea el
// cliente HTTP, nunca `fetch` real. Move/Rotate NO se tocan (siguen
// 100% client-side, sin mock necesario para ellos). La clase de error va
// DEFINIDA DENTRO del factory (nunca afuera) -- `geometryApplyStore.ts` se
// importa estáticamente arriba, y ESE import se evalúa antes que
// cualquier statement posterior del archivo (incluida una clase externa),
// así que una referencia externa dispara un `ReferenceError` real de TDZ.
vi.mock('../../editor/geometryApplyApi', () => {
  class PaintedRegionResizeConfirmationRequiredError extends Error {
    affectedFaces: { cuboidId: string; face: string }[]
    constructor(message: string, affectedFaces: { cuboidId: string; face: string }[]) {
      super(message)
      this.affectedFaces = affectedFaces
    }
  }
  return { applyGeometry: vi.fn(), PaintedRegionResizeConfirmationRequiredError }
})

const { threeViewportService } = await import('../ThreeViewportService')
const { default: ThreeViewport } = await import('../ThreeViewport.vue')
const { applyGeometry } = await import('../../editor/geometryApplyApi')

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
    vi.mocked(applyGeometry).mockReset()
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
      // Ticket 043, test de regresión: moveCuboid NUNCA dispara POST /geometry/apply -- 100% client-side.
      expect(applyGeometry).not.toHaveBeenCalled()
    })

    // -- Ticket 043, Diseño técnico §15: Resize deja de commitear localmente --

    it('scale: al soltar el drag, dispara UNA sola llamada a POST /geometry/apply y aplica el resultado real del backend', async () => {
      const { draft, controls } = setupSelectedCuboid()
      const resizedByBackend: Cuboid = { ...draft.model!.cuboids[0]!, from: [-2, -1, -1], to: [2, 1, 1] }
      vi.mocked(applyGeometry).mockResolvedValue({
        model: { ...draft.model!, cuboids: [resizedByBackend] },
        draftVersion: 2,
      })
      controls.setMode('scale')
      controls.dispatchEvent({ type: 'mouseDown', mode: controls.mode })
      controls.object!.scale.set(2, 1, 1)
      controls.dispatchEvent({ type: 'objectChange' })
      controls.dispatchEvent({ type: 'mouseUp', mode: controls.mode })
      await flushPromises()

      expect(applyGeometry).toHaveBeenCalledTimes(1)
      expect(applyGeometry).toHaveBeenCalledWith('test-mob', [{ op: 'resizeCuboid', target: 'cube-1', scale: [2, 1, 1] }])
      // Ticket 040: el resultado se aplica vía commitExternalModel -- geometría real del backend, no un cálculo local.
      const resized = draft.model!.cuboids[0]!
      expect(resized.from[0]).toBeCloseTo(-2, 9)
      expect(resized.to[0]).toBeCloseTo(2, 9)
    })

    it('scale: durante pointermove (objectChange) no se dispara ninguna llamada de red -- solo al soltar', async () => {
      const { draft, controls } = setupSelectedCuboid()
      vi.mocked(applyGeometry).mockResolvedValue({ model: draft.model!, draftVersion: 1 })
      controls.setMode('scale')
      controls.dispatchEvent({ type: 'mouseDown', mode: controls.mode })

      // N eventos de "arrastre" simulados (equivalente a pointermove) --
      // el preview es 100% local (Three.js escala el mesh en vivo), cero red.
      for (let i = 1; i <= 5; i++) {
        controls.object!.scale.set(1 + i * 0.1, 1, 1)
        controls.dispatchEvent({ type: 'objectChange' })
      }
      expect(applyGeometry).not.toHaveBeenCalled()

      controls.dispatchEvent({ type: 'mouseUp', mode: controls.mode })
      await flushPromises()

      expect(applyGeometry).toHaveBeenCalledTimes(1) // exactamente 1, al soltar
    })

    it('scale: si el backend exige confirmación de pérdida de pintura, el preview visual se mantiene y NO se toca el store hasta resolver el modal', async () => {
      const { draft, controls } = setupSelectedCuboid()
      const geometryApply = useGeometryApplyStore()
      const { PaintedRegionResizeConfirmationRequiredError } = await import('../../editor/geometryApplyApi')
      vi.mocked(applyGeometry).mockRejectedValue(
        new PaintedRegionResizeConfirmationRequiredError('confirmación requerida', [{ cuboidId: 'cube-1', face: 'north' }]),
      )
      const modelBeforeResize = draft.model
      controls.setMode('scale')
      controls.dispatchEvent({ type: 'mouseDown', mode: controls.mode })
      controls.object!.scale.set(2, 1, 1)
      controls.dispatchEvent({ type: 'objectChange' })
      controls.dispatchEvent({ type: 'mouseUp', mode: controls.mode })
      await flushPromises()

      expect(geometryApply.pendingResizeConfirmation).toEqual({
        cuboidId: 'cube-1', scale: [2, 1, 1], affectedFaces: [{ cuboidId: 'cube-1', face: 'north' }],
      })
      // El store NUNCA se tocó -- sigue siendo el modelo de antes del drag.
      expect(draft.model).toBe(modelBeforeResize)

      // "Cancelar": el store sigue intacto, geometryApply limpia el pendiente.
      geometryApply.cancelPendingResize()
      await nextTick()
      expect(draft.model!.cuboids[0]!.to[0]).toBe(1) // tamaño original, sin cambios
    })

    it('una operación rechazada (ej. GeometryValidationException del backend) muestra el error sin cambiar el modelo', async () => {
      const { draft, controls } = setupSelectedCuboid()
      vi.mocked(applyGeometry).mockRejectedValue(new Error('resizeCuboid: dimensión inválida'))
      controls.setMode('scale')
      controls.dispatchEvent({ type: 'mouseDown', mode: controls.mode })
      controls.object!.scale.set(0, 1, 1)
      controls.dispatchEvent({ type: 'objectChange' })
      controls.dispatchEvent({ type: 'mouseUp', mode: controls.mode })
      await flushPromises()

      const geometryApply = useGeometryApplyStore()
      expect(geometryApply.lastError).not.toBeNull()
      expect(draft.model!.cuboids[0]!.from).toEqual([-1, -1, -1]) // sin cambios
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
      // Ticket 043, test de regresión: rotateCuboid NUNCA dispara POST /geometry/apply -- 100% client-side.
      expect(applyGeometry).not.toHaveBeenCalled()
      expect(rotated.rotation[0]).toBe(0)
      expect(rotated.rotation[1]).toBe(0)
    })

  })
})
