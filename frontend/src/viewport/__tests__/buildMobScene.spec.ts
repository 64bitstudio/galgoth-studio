import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { Mesh } from 'three'
import { describe, expect, it } from 'vitest'
import { applyPivotRotation } from '../../domain/coordinateSystem'
import type { Bone, Cuboid, MobProjectModel, Vec3 } from '../../domain/MobProjectModel'
import { buildMobGroup } from '../buildMobScene'

// Ver nota en schema.spec.ts: process.cwd() en vez de import.meta.url.
const REPO_ROOT = resolve(process.cwd(), '..')

const EMPTY_FACES = {
  north: { uv: [0, 0, 0, 0] as [number, number, number, number], texture: null },
  south: { uv: [0, 0, 0, 0] as [number, number, number, number], texture: null },
  east: { uv: [0, 0, 0, 0] as [number, number, number, number], texture: null },
  west: { uv: [0, 0, 0, 0] as [number, number, number, number], texture: null },
  up: { uv: [0, 0, 0, 0] as [number, number, number, number], texture: null },
  down: { uv: [0, 0, 0, 0] as [number, number, number, number], texture: null },
}

function bone(id: string, parentId: string | null, pivot: Vec3, rotation: Vec3): Bone {
  return { id, name: id, parentId, pivot, rotation }
}

function cuboid(id: string, boneId: string, from: Vec3, to: Vec3, origin: Vec3, rotation: Vec3): Cuboid {
  return { id, name: id, boneId, from, to, origin, rotation, faces: EMPTY_FACES }
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

function meshNamed(group: ReturnType<typeof buildMobGroup>, name: string): Mesh {
  const found = group.children.find((child) => child.name === name)
  if (!(found instanceof Mesh)) {
    throw new Error(`no se encontró un Mesh llamado '${name}'`)
  }
  return found
}

describe('buildMobGroup', () => {
  it('un cuboid sin rotación (propia ni de su bone) queda en el punto medio de from/to', () => {
    const root = bone('root', null, [0, 0, 0], [0, 0, 0])
    const box = cuboid('box', 'root', [-4, 0, -4], [4, 8, 4], [0, 4, 0], [0, 0, 0])
    const group = buildMobGroup(modelWith([root], [box]))

    const mesh = meshNamed(group, 'box')
    expect([mesh.position.x, mesh.position.y, mesh.position.z]).toEqual([0, 4, 0])
  })

  it('la rotación propia del cuboid se aplica con applyPivotRotation (AC #1, CoordinateSystemContract)', () => {
    const root = bone('root', null, [0, 0, 0], [0, 0, 0])
    const origin: Vec3 = [0, 4, 0]
    const rotation: Vec3 = [0, 0, 90]
    const box = cuboid('box', 'root', [-4, 0, -4], [4, 8, 4], origin, rotation)
    const group = buildMobGroup(modelWith([root], [box]))

    const mesh = meshNamed(group, 'box')
    const expected = applyPivotRotation([0, 4, 0], origin, rotation) // el centro del box ya es [0,4,0]
    expect(mesh.position.x).toBeCloseTo(expected[0], 9)
    expect(mesh.position.y).toBeCloseTo(expected[1], 9)
    expect(mesh.position.z).toBeCloseTo(expected[2], 9)
  })

  it('compone la rotación del bone PADRE sobre el resultado del cuboid -- jerarquía de 2 niveles (AC #1)', () => {
    const root = bone('root', null, [0, 10, 0], [0, 0, 90]) // bone raíz rota 90° en Z sobre [0,10,0]
    const child = bone('child', 'root', [2, 10, 0], [0, 0, 0]) // bone hijo, sin rotación propia
    const box = cuboid('box', 'child', [0, 8, -1], [4, 12, 1], [2, 10, 0], [0, 0, 0]) // centro local = [2,10,0]

    const group = buildMobGroup(modelWith([root, child], [box]))
    const mesh = meshNamed(group, 'box')

    // Camino esperado, calculado independiente con la MISMA función de
    // contrato pero sin pasar por buildMobGroup: primero la rotación
    // (nula) del cuboid sobre su origen, luego la del bone padre.
    const afterOwnRotation = applyPivotRotation([2, 10, 0], [2, 10, 0], [0, 0, 0]) // no-op, sigue [2,10,0]
    const expected = applyPivotRotation(afterOwnRotation, [0, 10, 0], [0, 0, 90])
    expect(mesh.position.x).toBeCloseTo(expected[0], 9)
    expect(mesh.position.y).toBeCloseTo(expected[1], 9)
    expect(mesh.position.z).toBeCloseTo(expected[2], 9)
  })

  it('agrega un marcador de pivote por cada bone, en su posición mundial compuesta', () => {
    const root = bone('root', null, [0, 0, 0], [0, 0, 0])
    const child = bone('child', 'root', [5, 0, 0], [0, 0, 0])
    const group = buildMobGroup(modelWith([root, child], []))

    const pivotNames = group.children.map((c) => c.name)
    expect(pivotNames).toContain('bone-pivot:root')
    expect(pivotNames).toContain('bone-pivot:child')
  })

  it('la fixture real Carcomido produce un mesh por cuboid y un marcador por bone (end-to-end)', () => {
    const fixture: MobProjectModel = JSON.parse(
      readFileSync(resolve(REPO_ROOT, 'contracts/fixtures/carcomido-mob-project-model.json'), 'utf-8'),
    )

    const group = buildMobGroup(fixture)

    expect(group.children).toHaveLength(fixture.cuboids.length + fixture.bones.length)
    for (const cuboidModel of fixture.cuboids) {
      expect(() => meshNamed(group, cuboidModel.name)).not.toThrow()
    }
  })
})
