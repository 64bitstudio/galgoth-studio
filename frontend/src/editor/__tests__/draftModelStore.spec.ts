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

  describe('Command stack de Undo/Redo (ticket 019)', () => {
    it('recién cargado el modelo, no hay nada para deshacer ni rehacer', () => {
      const store = useDraftModelStore()
      store.load(modelWith([bone('b', null)], [cuboid('c', 'b')]))

      expect(store.canUndo).toBe(false)
      expect(store.canRedo).toBe(false)
    })

    it('Undo revierte la última edición exitosa exactamente, AC #1', () => {
      const store = useDraftModelStore()
      store.load(modelWith([bone('b', null)], [cuboid('c', 'b')]))
      const originalFrom = store.model!.cuboids[0]!.from

      store.moveSelectedCuboid('c', [1, 0, 0])
      expect(store.model?.cuboids[0]!.from).toEqual([-3, 0, -4])

      store.undo()

      expect(store.model?.cuboids[0]!.from).toEqual(originalFrom)
      expect(store.canUndo).toBe(false)
      expect(store.canRedo).toBe(true)
    })

    it('una serie de cambios se deshace paso a paso en el orden inverso exacto, AC #1', () => {
      const store = useDraftModelStore()
      store.load(modelWith([bone('b', null)], [cuboid('c', 'b')]))

      store.moveSelectedCuboid('c', [1, 0, 0]) // from.x: -4 -> -3
      store.moveSelectedCuboid('c', [1, 0, 0]) // from.x: -3 -> -2
      store.moveSelectedCuboid('c', [1, 0, 0]) // from.x: -2 -> -1
      expect(store.model?.cuboids[0]!.from[0]).toBe(-1)

      store.undo()
      expect(store.model?.cuboids[0]!.from[0]).toBe(-2)
      store.undo()
      expect(store.model?.cuboids[0]!.from[0]).toBe(-3)
      store.undo()
      expect(store.model?.cuboids[0]!.from[0]).toBe(-4)
      expect(store.canUndo).toBe(false)
    })

    it('Redo avanza el draft hasta el estado más reciente tras deshacer, AC #2', () => {
      const store = useDraftModelStore()
      store.load(modelWith([bone('b', null)], [cuboid('c', 'b')]))

      store.moveSelectedCuboid('c', [1, 0, 0])
      store.moveSelectedCuboid('c', [1, 0, 0])
      store.undo()
      store.undo()

      store.redo()
      expect(store.model?.cuboids[0]!.from[0]).toBe(-3)
      store.redo()
      expect(store.model?.cuboids[0]!.from[0]).toBe(-2)
      expect(store.canRedo).toBe(false)
    })

    it('un cambio nuevo aplicado después de deshacer descarta la rama de redo pendiente, AC #3', () => {
      const store = useDraftModelStore()
      store.load(modelWith([bone('b', null)], [cuboid('c', 'b')]))

      store.moveSelectedCuboid('c', [1, 0, 0])
      store.moveSelectedCuboid('c', [1, 0, 0])
      store.undo() // hay una rama de redo pendiente ahora

      store.moveSelectedCuboid('c', [0, 1, 0]) // Command nuevo

      expect(store.canRedo).toBe(false)
      expect(store.model?.cuboids[0]!.from).toEqual([-3, 1, -4])
    })

    it('undo/redo nunca crea ni destruye una mob_revision -- ninguna acción de este store lo hace (solo reasignan model)', () => {
      // Este store no conoce el concepto de mob_revision en absoluto (llega
      // en el ticket 020) -- la ausencia total de esa noción aquí ES la
      // prueba de que Undo/Redo jamás puede tocarla.
      const store = useDraftModelStore()
      store.load(modelWith([bone('b', null)], [cuboid('c', 'b')]))
      store.moveSelectedCuboid('c', [1, 0, 0])

      store.undo()
      store.redo()

      expect(store).not.toHaveProperty('createRevision')
      expect(store).not.toHaveProperty('mobRevisions')
    })

    it('una operación rechazada NO genera un Command (nada que deshacer)', () => {
      const store = useDraftModelStore()
      store.load(modelWith([bone('b', null)], [cuboid('c', 'b')]))

      store.resizeSelectedCuboid('c', [1, 0, 1]) // scale.y = 0 -> inválido, rechazado

      expect(store.lastError).not.toBeNull()
      expect(store.canUndo).toBe(false)
    })

    it('addCuboid/addBone/duplicate/deleteCuboid también generan un Command deshacible', () => {
      const store = useDraftModelStore()
      store.load(modelWith([bone('b', null)], [cuboid('c', 'b')]))

      const newCuboidId = store.addCuboid('b', 'x', [0, 0, 0], [1, 1, 1], [0, 0, 0])
      expect(store.model?.cuboids).toHaveLength(2)
      store.undo()
      expect(store.model?.cuboids).toHaveLength(1)
      store.redo()
      expect(store.model?.cuboids.map((c) => c.id)).toContain(newCuboidId)

      store.deleteCuboid('c')
      expect(store.model?.cuboids.map((c) => c.id)).not.toContain('c')
      store.undo()
      expect(store.model?.cuboids.map((c) => c.id)).toContain('c')
    })

    it('undo()/redo() no hacen nada si la pila correspondiente está vacía (sin lanzar)', () => {
      const store = useDraftModelStore()
      store.load(modelWith([bone('b', null)], [cuboid('c', 'b')]))
      const original = store.model

      expect(() => store.undo()).not.toThrow()
      expect(() => store.redo()).not.toThrow()
      expect(store.model).toBe(original)
    })

    it('undo()/redo() no hacen nada si no hay modelo cargado (sin lanzar)', () => {
      const store = useDraftModelStore()
      expect(() => store.undo()).not.toThrow()
      expect(() => store.redo()).not.toThrow()
    })

    it('load() reinicia ambas pilas -- cargar un mob nuevo no hereda historial del anterior', () => {
      const store = useDraftModelStore()
      store.load(modelWith([bone('b', null)], [cuboid('c', 'b')]))
      store.moveSelectedCuboid('c', [1, 0, 0])
      expect(store.canUndo).toBe(true)

      store.load(modelWith([bone('b2', null)], []))

      expect(store.canUndo).toBe(false)
      expect(store.canRedo).toBe(false)
    })
  })
})
