import { describe, expect, it } from 'vitest'
import type { Bone, Cuboid, MobProjectModel } from '../../domain/MobProjectModel'
import { buildHierarchyTree } from '../hierarchyTree'

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
  return { id, name: id, boneId, from: [0, 0, 0], to: [1, 1, 1], origin: [0, 0, 0], rotation: [0, 0, 0], faces: EMPTY_FACES }
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

describe('buildHierarchyTree', () => {
  it('AC #1: muestra bones y sus cuboids hijos en la estructura correcta', () => {
    const root = bone('root', null)
    const child = bone('child', 'root')
    const cuboidOnRoot = cuboid('a', 'root')
    const cuboidOnChild = cuboid('b', 'child')

    const tree = buildHierarchyTree(modelWith([root, child], [cuboidOnRoot, cuboidOnChild]))

    expect(tree).toHaveLength(1)
    expect(tree[0]!.bone.id).toBe('root')
    expect(tree[0]!.cuboids.map((c) => c.id)).toEqual(['a'])
    expect(tree[0]!.children).toHaveLength(1)
    expect(tree[0]!.children[0]!.bone.id).toBe('child')
    expect(tree[0]!.children[0]!.cuboids.map((c) => c.id)).toEqual(['b'])
  })

  it('soporta múltiples bones raíz (forest, no solo un único árbol)', () => {
    const rootA = bone('rootA', null)
    const rootB = bone('rootB', null)

    const tree = buildHierarchyTree(modelWith([rootA, rootB], []))

    expect(tree.map((node) => node.bone.id)).toEqual(['rootA', 'rootB'])
  })

  it('un bone sin cuboids propios tiene la lista vacía, no undefined', () => {
    const root = bone('root', null)

    const tree = buildHierarchyTree(modelWith([root], []))

    expect(tree[0]!.cuboids).toEqual([])
  })
})
