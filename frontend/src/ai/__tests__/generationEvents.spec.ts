import { describe, expect, it } from 'vitest'
import type { Bone, Cuboid, CuboidFaces } from '../../domain/MobProjectModel'
import { applyPreviewDelta, emptyPreviewModel, type PreviewOperationsPayload } from '../generationEvents'

const EMPTY_FACES: CuboidFaces = {
  north: { uv: [0, 0, 0, 0], texture: null },
  south: { uv: [0, 0, 0, 0], texture: null },
  east: { uv: [0, 0, 0, 0], texture: null },
  west: { uv: [0, 0, 0, 0], texture: null },
  up: { uv: [0, 0, 0, 0], texture: null },
  down: { uv: [0, 0, 0, 0], texture: null },
}

function bone(id: string): Bone {
  return { id, name: id, parentId: null, pivot: [0, 0, 0], rotation: [0, 0, 0] }
}

function cuboid(id: string, boneId: string): Cuboid {
  return { id, name: id, boneId, from: [0, 0, 0], to: [4, 4, 4], origin: [0, 0, 0], rotation: [0, 0, 0], faces: EMPTY_FACES }
}

function delta(overrides: Partial<PreviewOperationsPayload> = {}): PreviewOperationsPayload {
  return { type: 'preview_operations', addedOrUpdatedBones: [], addedOrUpdatedCuboids: [], removedCuboidIds: [], ...overrides }
}

describe('generationEvents', () => {
  it('emptyPreviewModel arranca sin bones ni cuboids, con el atlas 128x128 (mismo default que el backend)', () => {
    const model = emptyPreviewModel('mob-1', 'project-1', 'Carcomido', 'humanoid')

    expect(model.bones).toEqual([])
    expect(model.cuboids).toEqual([])
    expect(model.texture).toEqual({ width: 128, height: 128, storageKey: null })
  })

  it('applyPreviewDelta agrega un bone/cuboid nuevo sin mutar el modelo original', () => {
    const model = emptyPreviewModel('mob-1', 'project-1', 'Carcomido', 'humanoid')
    const b1 = bone('b1')
    const c1 = cuboid('c1', 'b1')

    const next = applyPreviewDelta(model, delta({ addedOrUpdatedBones: [b1], addedOrUpdatedCuboids: [c1] }))

    expect(next.bones).toEqual([b1])
    expect(next.cuboids).toEqual([c1])
    expect(model.bones).toEqual([]) // el original no se tocó
  })

  it('applyPreviewDelta reemplaza (no duplica) un cuboid cuyo id ya existía', () => {
    const c1 = cuboid('c1', 'b1')
    const model = { ...emptyPreviewModel('mob-1', 'project-1', 'Carcomido', 'humanoid'), cuboids: [c1] }
    const c1Resized: Cuboid = { ...c1, to: [8, 8, 8] }

    const next = applyPreviewDelta(model, delta({ addedOrUpdatedCuboids: [c1Resized] }))

    expect(next.cuboids).toEqual([c1Resized])
  })

  it('applyPreviewDelta quita los cuboids listados en removedCuboidIds', () => {
    const c1 = cuboid('c1', 'b1')
    const c2 = cuboid('c2', 'b1')
    const model = { ...emptyPreviewModel('mob-1', 'project-1', 'Carcomido', 'humanoid'), cuboids: [c1, c2] }

    const next = applyPreviewDelta(model, delta({ removedCuboidIds: ['c1'] }))

    expect(next.cuboids).toEqual([c2])
  })
})
