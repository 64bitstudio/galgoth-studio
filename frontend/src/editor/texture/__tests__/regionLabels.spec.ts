import { describe, expect, it } from 'vitest'
import type { Cuboid, UvRegion } from '../../../domain/MobProjectModel'
import { buildSelectableRegions, regionKey } from '../regionLabels'

function cuboid(id: string, name: string): Cuboid {
  return {
    id,
    name,
    boneId: 'bone-1',
    from: [0, 0, 0],
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
}

function region(cuboidId: string, face: UvRegion['face'], status: UvRegion['status'] = 'unpainted'): UvRegion {
  return { cuboidId, face, rect: [0, 0, 8, 8], status }
}

describe('buildSelectableRegions', () => {
  it('etiqueta cada región como "${cuboid.name} (${face})" -- misma convención ya usada en MobEditor.vue (043)', () => {
    const cuboids = [cuboid('c1', 'Cabeza')]
    const regions = [region('c1', 'north')]

    const selectable = buildSelectableRegions(regions, cuboids)

    expect(selectable).toEqual([{ cuboidId: 'c1', face: 'north', label: 'Cabeza (north)', rect: [0, 0, 8, 8] }])
  })

  it('excluye regiones ORPHAN -- su cuboid ya no existe, nada que enfocar', () => {
    const cuboids = [cuboid('c1', 'Cabeza')]
    const regions = [region('c1', 'north'), region('deleted-cuboid', 'south', 'orphan')]

    const selectable = buildSelectableRegions(regions, cuboids)

    expect(selectable).toHaveLength(1)
    expect(selectable[0]!.cuboidId).toBe('c1')
  })

  it('excluye regiones cuyo cuboidId no resuelve a ningún cuboid real (defensivo, aunque no sea ORPHAN)', () => {
    const cuboids = [cuboid('c1', 'Cabeza')]
    const regions = [region('c-inexistente', 'north')]

    expect(buildSelectableRegions(regions, cuboids)).toHaveLength(0)
  })

  it('regionKey() es estable y única por (cuboidId, face)', () => {
    expect(regionKey({ cuboidId: 'c1', face: 'north' })).toBe('c1:north')
    expect(regionKey({ cuboidId: 'c1', face: 'south' })).not.toBe(regionKey({ cuboidId: 'c1', face: 'north' }))
  })
})
