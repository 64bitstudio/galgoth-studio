import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it } from 'vitest'
import type { Bone, Cuboid, MobProjectModel } from '../../domain/MobProjectModel'
import { useDraftModelStore } from '../draftModelStore'

const EMPTY_FACES = {
  north: { uv: [0, 0, 0, 0] as [number, number, number, number], texture: null },
  south: { uv: [0, 0, 0, 0] as [number, number, number, number], texture: null },
  east: { uv: [0, 0, 0, 0] as [number, number, number, number], texture: null },
  west: { uv: [0, 0, 0, 0] as [number, number, number, number], texture: null },
  up: { uv: [0, 0, 0, 0] as [number, number, number, number], texture: null },
  down: { uv: [0, 0, 0, 0] as [number, number, number, number], texture: null },
}

function bone(id: string, parentId: string | null): Bone {
  return { id, name: id, parentId, pivot: [0, 0, 0], rotation: [0, 0, 0] }
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
    uv: { textureWidth: 64, textureHeight: 64, regions: [] },
    animations: [],
    exportSettings: { preferredFormatVersion: 'v5' },
    referenceImages: [],
  }
}

describe('useDraftModelStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('load() reemplaza el modelo y limpia lastError', () => {
    const store = useDraftModelStore()
    store.load(modelWith([bone('b', null)], []))
    expect(store.model?.bones).toHaveLength(1)
    expect(store.lastError).toBeNull()
  })

  it('una operación válida actualiza el modelo sin dejar lastError', () => {
    const store = useDraftModelStore()
    store.load(modelWith([bone('b', null)], [cuboid('c', 'b')]))

    store.moveSelectedCuboid('c', [1, 0, 0])

    expect(store.model?.cuboids[0]!.from).toEqual([-3, 0, -4])
    expect(store.lastError).toBeNull()
  })

  it('una operación rechazada NO cambia el modelo y sí guarda lastError', () => {
    const store = useDraftModelStore()
    const original = modelWith([bone('b', null)], [cuboid('c', 'b')])
    store.load(original)

    store.resizeSelectedCuboid('c', [1, 0, 1]) // scale.y = 0 -> inválido

    expect(store.model?.cuboids[0]!.from).toEqual(original.cuboids[0]!.from)
    expect(store.lastError).not.toBeNull()
  })

  it('addCuboid devuelve el id creado y lo agrega al modelo', () => {
    const store = useDraftModelStore()
    store.load(modelWith([bone('b', null)], []))

    const newId = store.addCuboid('b', 'nuevo', [-1, -1, -1], [1, 1, 1], [0, 0, 0])

    expect(newId).toBeTruthy()
    expect(store.model?.cuboids.map((c) => c.id)).toContain(newId)
  })

  it('addCuboid con un bone inexistente devuelve null y guarda lastError', () => {
    const store = useDraftModelStore()
    store.load(modelWith([], []))

    const newId = store.addCuboid('no-existe', 'x', [0, 0, 0], [1, 1, 1], [0, 0, 0])

    expect(newId).toBeNull()
    expect(store.lastError).not.toBeNull()
  })

  it('boneRemovalImpact + deleteBoneCascade eliminan bone, descendientes y sus cuboids', () => {
    const store = useDraftModelStore()
    const root = bone('root', null)
    const child = bone('child', 'root')
    store.load(modelWith([root, child], [cuboid('onChild', 'child')]))

    const impact = store.boneRemovalImpact('child')
    expect(impact?.affectedCuboidIds).toEqual(['onChild'])

    store.deleteBoneCascade('child')

    expect(store.model?.bones.map((b) => b.id)).toEqual(['root'])
    expect(store.model?.cuboids).toHaveLength(0)
  })

  it('duplicate crea una copia con id distinto', () => {
    const store = useDraftModelStore()
    store.load(modelWith([bone('b', null)], [cuboid('original', 'b')]))

    const newId = store.duplicate('original')

    expect(newId).not.toBe('original')
    expect(store.model?.cuboids).toHaveLength(2)
  })

  it('ninguna acción hace nada si no hay modelo cargado (sin lanzar)', () => {
    const store = useDraftModelStore()
    expect(() => store.moveSelectedCuboid('c', [1, 0, 0])).not.toThrow()
    expect(store.addCuboid('b', 'x', [0, 0, 0], [1, 1, 1], [0, 0, 0])).toBeNull()
    expect(store.addBone(null, 'x', [0, 0, 0], [0, 0, 0])).toBeNull()
    expect(store.duplicate('c')).toBeNull()
  })

  it('setPivot actualiza el pivote de un bone existente', () => {
    const store = useDraftModelStore()
    store.load(modelWith([bone('b', null)], []))

    store.setPivot('b', [1, 2, 3])

    expect(store.model?.bones[0]!.pivot).toEqual([1, 2, 3])
    expect(store.lastError).toBeNull()
  })

  it('setPivot con un bone inexistente NO cambia el modelo y guarda lastError', () => {
    const store = useDraftModelStore()
    store.load(modelWith([bone('b', null)], []))

    store.setPivot('no-existe', [1, 2, 3])

    expect(store.model?.bones[0]!.pivot).toEqual([0, 0, 0])
    expect(store.lastError).not.toBeNull()
  })

  it('setRotation actualiza la rotación de un bone existente', () => {
    const store = useDraftModelStore()
    store.load(modelWith([bone('b', null)], []))

    store.setRotation('b', [0, 90, 0])

    expect(store.model?.bones[0]!.rotation).toEqual([0, 90, 0])
    expect(store.lastError).toBeNull()
  })

  it('setRotation con un bone inexistente guarda lastError', () => {
    const store = useDraftModelStore()
    store.load(modelWith([bone('b', null)], []))

    store.setRotation('no-existe', [0, 90, 0])

    expect(store.lastError).not.toBeNull()
  })

  it('addBone crea un bone hijo del parentId dado', () => {
    const store = useDraftModelStore()
    store.load(modelWith([bone('padre', null)], []))

    const newId = store.addBone('padre', 'hijo', [0, 0, 0], [0, 0, 0])

    expect(newId).toBeTruthy()
    expect(store.model?.bones.find((b) => b.id === newId)?.parentId).toBe('padre')
  })

  it('addBone con parentId inexistente devuelve null y guarda lastError', () => {
    const store = useDraftModelStore()
    store.load(modelWith([], []))

    const newId = store.addBone('no-existe', 'x', [0, 0, 0], [0, 0, 0])

    expect(newId).toBeNull()
    expect(store.lastError).not.toBeNull()
  })

  it('deleteCuboid elimina el cuboid del modelo', () => {
    const store = useDraftModelStore()
    store.load(modelWith([bone('b', null)], [cuboid('c1', 'b')]))

    store.deleteCuboid('c1')

    expect(store.model?.cuboids).toHaveLength(0)
  })

  it('duplicate con un cuboidId inexistente devuelve null y guarda lastError', () => {
    const store = useDraftModelStore()
    store.load(modelWith([bone('b', null)], []))

    const newId = store.duplicate('no-existe')

    expect(newId).toBeNull()
    expect(store.lastError).not.toBeNull()
  })
})
