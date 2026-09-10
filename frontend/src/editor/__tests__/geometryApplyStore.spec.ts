import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { Cuboid, MobProjectModel } from '../../domain/MobProjectModel'
import { useDraftModelStore } from '../draftModelStore'

// Ticket 043: se mockea el cliente HTTP, nunca `fetch` real -- misma
// convención que ThreeViewport.spec.ts/EditorToolbar.spec.ts/InspectorPanel.spec.ts.
vi.mock('../geometryApplyApi', () => {
  class PaintedRegionResizeConfirmationRequiredError extends Error {
    affectedFaces: { cuboidId: string; face: string }[]
    constructor(message: string, affectedFaces: { cuboidId: string; face: string }[]) {
      super(message)
      this.affectedFaces = affectedFaces
    }
  }
  return { applyGeometry: vi.fn(), PaintedRegionResizeConfirmationRequiredError }
})

const { useGeometryApplyStore } = await import('../geometryApplyStore')
const { applyGeometry, PaintedRegionResizeConfirmationRequiredError } = await import('../geometryApplyApi')

const EMPTY_FACES = {
  north: { uv: [0, 0, 0, 0] as [number, number, number, number], texture: null },
  south: { uv: [0, 0, 0, 0] as [number, number, number, number], texture: null },
  east: { uv: [0, 0, 0, 0] as [number, number, number, number], texture: null },
  west: { uv: [0, 0, 0, 0] as [number, number, number, number], texture: null },
  up: { uv: [0, 0, 0, 0] as [number, number, number, number], texture: null },
  down: { uv: [0, 0, 0, 0] as [number, number, number, number], texture: null },
}

function cuboid(id: string, boneId = 'bone-1'): Cuboid {
  return { id, name: id, boneId, from: [-1, -1, -1], to: [1, 1, 1], origin: [0, 0, 0], rotation: [0, 0, 0], faces: EMPTY_FACES }
}

function modelWith(cuboids: Cuboid[]): MobProjectModel {
  return {
    mobId: 'mob-1',
    projectId: 'test-project',
    name: 'Test',
    baseType: 'humanoid',
    units: 'minecraft_pixels',
    bones: [{ id: 'bone-1', name: 'bone-1', parentId: null, pivot: [0, 0, 0], rotation: [0, 0, 0] }],
    cuboids,
    texture: { width: 64, height: 64, storageKey: null },
    uv: { textureWidth: 64, textureHeight: 64, regions: [], reservations: [] },
    animations: [],
    exportSettings: { preferredFormatVersion: 'v5' },
    referenceImages: [],
  }
}

describe('geometryApplyStore (ticket 043)', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  afterEach(() => {
    vi.mocked(applyGeometry).mockReset()
  })

  it('resizeCuboid exitoso aplica el resultado real del backend vía commitExternalModel', async () => {
    const draft = useDraftModelStore()
    draft.load(modelWith([cuboid('c1')]))
    const geometryApply = useGeometryApplyStore()
    const resized = { ...cuboid('c1'), to: [2, 1, 1] as [number, number, number] }
    vi.mocked(applyGeometry).mockResolvedValue({ model: modelWith([resized]), draftVersion: 2 })

    await geometryApply.resizeCuboid('mob-1', 'c1', [2, 1, 1])

    expect(applyGeometry).toHaveBeenCalledWith('mob-1', [{ op: 'resizeCuboid', target: 'c1', scale: [2, 1, 1] }])
    expect(draft.model!.cuboids[0]!.to).toEqual([2, 1, 1])
    expect(geometryApply.lastError).toBeNull()
    expect(draft.canUndo).toBe(true) // pasó por commitExternalModel -- mismo mecanismo de Command que 040.
  })

  it('resizeCuboid que exige confirmación NO toca el draft -- guarda el pendiente para el modal', async () => {
    const draft = useDraftModelStore()
    const original = modelWith([cuboid('c1')])
    draft.load(original)
    const geometryApply = useGeometryApplyStore()
    vi.mocked(applyGeometry).mockRejectedValue(
      new PaintedRegionResizeConfirmationRequiredError('confirmación requerida', [{ cuboidId: 'c1', face: 'north' }]),
    )

    await geometryApply.resizeCuboid('mob-1', 'c1', [2, 1, 1])

    expect(draft.model).toEqual(original) // sin cambios
    expect(geometryApply.pendingResizeConfirmation).toEqual({
      cuboidId: 'c1', scale: [2, 1, 1], affectedFaces: [{ cuboidId: 'c1', face: 'north' }],
    })
  })

  it('confirmPendingResize reenvía la MISMA operación con confirmPaintLoss:true y aplica el resultado', async () => {
    const draft = useDraftModelStore()
    draft.load(modelWith([cuboid('c1')]))
    const geometryApply = useGeometryApplyStore()
    vi.mocked(applyGeometry).mockRejectedValueOnce(
      new PaintedRegionResizeConfirmationRequiredError('confirmación requerida', [{ cuboidId: 'c1', face: 'north' }]),
    )
    await geometryApply.resizeCuboid('mob-1', 'c1', [2, 1, 1])
    const resized = { ...cuboid('c1'), to: [2, 1, 1] as [number, number, number] }
    vi.mocked(applyGeometry).mockResolvedValueOnce({ model: modelWith([resized]), draftVersion: 3 })

    await geometryApply.confirmPendingResize('mob-1')

    expect(applyGeometry).toHaveBeenLastCalledWith('mob-1', [{ op: 'resizeCuboid', target: 'c1', scale: [2, 1, 1] }], true)
    expect(draft.model!.cuboids[0]!.to).toEqual([2, 1, 1])
    expect(geometryApply.pendingResizeConfirmation).toBeNull()
  })

  it('cancelPendingResize limpia el pendiente sin reenviar nada ni tocar el draft', async () => {
    const draft = useDraftModelStore()
    const original = modelWith([cuboid('c1')])
    draft.load(original)
    const geometryApply = useGeometryApplyStore()
    vi.mocked(applyGeometry).mockRejectedValue(
      new PaintedRegionResizeConfirmationRequiredError('confirmación requerida', [{ cuboidId: 'c1', face: 'north' }]),
    )
    await geometryApply.resizeCuboid('mob-1', 'c1', [2, 1, 1])
    vi.mocked(applyGeometry).mockClear()

    geometryApply.cancelPendingResize()

    expect(geometryApply.pendingResizeConfirmation).toBeNull()
    expect(draft.model).toEqual(original)
    expect(applyGeometry).not.toHaveBeenCalled()
  })

  it('createCuboid devuelve el id REAL asignado por el backend (nunca el tempId enviado)', async () => {
    const draft = useDraftModelStore()
    draft.load(modelWith([]))
    const geometryApply = useGeometryApplyStore()
    vi.mocked(applyGeometry).mockImplementation(async (_mobId, operations) => {
      const op = operations[0] as { op: 'createCuboid'; tempId: string; boneId: string }
      expect(op.tempId).not.toBe('backend-assigned-id') // el tempId es solo un correlation id del batch
      return { model: modelWith([cuboid('backend-assigned-id', op.boneId)]), draftVersion: 1 }
    })

    const newId = await geometryApply.createCuboid('mob-1', 'bone-1', 'nuevo', [-1, 0, -1], [1, 2, 1], [0, 1, 0])

    expect(newId).toBe('backend-assigned-id')
    expect(draft.model!.cuboids).toHaveLength(1)
  })

  it('createCuboid rechazado (p.ej. UV_ATLAS_OVERFLOW) devuelve null y reporta lastError sin tocar el draft', async () => {
    const draft = useDraftModelStore()
    const original = modelWith([])
    draft.load(original)
    const geometryApply = useGeometryApplyStore()
    vi.mocked(applyGeometry).mockRejectedValue(Object.assign(new Error('No cabe en el atlas.'), { status: 400, code: 'UV_ATLAS_OVERFLOW' }))

    const newId = await geometryApply.createCuboid('mob-1', 'bone-1', 'nuevo', [-1, 0, -1], [1, 2, 1], [0, 1, 0])

    expect(newId).toBeNull()
    expect(geometryApply.lastError).toContain('No cabe en el atlas.')
    expect(draft.model).toEqual(original)
  })

  it('removeCuboid exitoso aplica el resultado real del backend', async () => {
    const draft = useDraftModelStore()
    draft.load(modelWith([cuboid('c1')]))
    const geometryApply = useGeometryApplyStore()
    vi.mocked(applyGeometry).mockResolvedValue({ model: modelWith([]), draftVersion: 2 })

    await geometryApply.removeCuboid('mob-1', 'c1')

    expect(applyGeometry).toHaveBeenCalledWith('mob-1', [{ op: 'removeCuboid', target: 'c1' }])
    expect(draft.model!.cuboids).toHaveLength(0)
  })
})
