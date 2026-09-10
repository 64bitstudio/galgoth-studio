import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it } from 'vitest'
import type { Bone, Cuboid, MobProjectModel } from '../../../domain/MobProjectModel'
import { useDraftModelStore } from '../../draftModelStore'
import { useTextureEditorStore } from '../textureEditorStore'

const EMPTY_FACES = {
  north: { uv: [0, 0, 0, 0] as [number, number, number, number], texture: null },
  south: { uv: [0, 0, 0, 0] as [number, number, number, number], texture: null },
  east: { uv: [0, 0, 0, 0] as [number, number, number, number], texture: null },
  west: { uv: [0, 0, 0, 0] as [number, number, number, number], texture: null },
  up: { uv: [0, 0, 0, 0] as [number, number, number, number], texture: null },
  down: { uv: [0, 0, 0, 0] as [number, number, number, number], texture: null },
}

function bone(id: string): Bone {
  return { id, name: id, parentId: null, pivot: [0, 0, 0], rotation: [0, 0, 0] }
}

function cuboid(id: string, boneId: string): Cuboid {
  return { id, name: id, boneId, from: [-4, 0, -4], to: [4, 8, 4], origin: [0, 0, 0], rotation: [0, 0, 0], faces: EMPTY_FACES }
}

function modelWith(bones: Bone[], cuboids: Cuboid[]): MobProjectModel {
  return {
    mobId: 'test-mob',
    projectId: 'test-project',
    name: 'Test',
    baseType: 'humanoid',
    units: 'minecraft_pixels',
    bones,
    cuboids,
    texture: { width: 64, height: 64, storageKey: null },
    uv: { textureWidth: 64, textureHeight: 64, regions: [], reservations: [] },
    animations: [],
    exportSettings: { preferredFormatVersion: 'v5' },
    referenceImages: [],
  }
}

/**
 * Test cruzado explícito, AC #5 del ticket 046: la pila de Undo/Redo de
 * `textureEditorStore.ts` es 100% independiente de la de `draftModelStore.ts`
 * (geometría, ticket 019) -- Ctrl+Z en un tab nunca cruza al otro.
 */
describe('independencia entre textureEditorStore y draftModelStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('un undo() en textura no afecta el modelo pendiente de draftModelStore', () => {
    const geometryStore = useDraftModelStore()
    const textureStore = useTextureEditorStore()

    geometryStore.load(modelWith([bone('b')], [cuboid('c', 'b')]))
    geometryStore.moveSelectedCuboid('c', [1, 0, 0])
    const movedFrom = geometryStore.model!.cuboids[0]!.from

    textureStore.loadAtlas(8, 8)
    const rect = { x: 0, y: 0, width: 2, height: 2 }
    const before = textureStore.readRegion(rect)!
    const after = new Uint8ClampedArray(before.length).fill(255)
    textureStore.recordPatch(rect, before, after)

    textureStore.undo()

    expect(textureStore.canUndo).toBe(false)
    expect(geometryStore.canUndo).toBe(true)
    expect(geometryStore.model!.cuboids[0]!.from).toEqual(movedFrom)
  })

  it('un undo() en geometría no afecta la pila pendiente de textureEditorStore', () => {
    const geometryStore = useDraftModelStore()
    const textureStore = useTextureEditorStore()

    geometryStore.load(modelWith([bone('b')], [cuboid('c', 'b')]))
    geometryStore.moveSelectedCuboid('c', [1, 0, 0])

    textureStore.loadAtlas(8, 8)
    const rect = { x: 0, y: 0, width: 2, height: 2 }
    const before = textureStore.readRegion(rect)!
    const after = new Uint8ClampedArray(before.length).fill(128)
    textureStore.recordPatch(rect, before, after)

    geometryStore.undo()

    expect(geometryStore.canUndo).toBe(false)
    expect(textureStore.canUndo).toBe(true)
    expect(textureStore.readRegion(rect)).toEqual(after)
  })
})
