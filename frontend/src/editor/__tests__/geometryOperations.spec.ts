import { describe, expect, it } from 'vitest'
import type { Bone, Cuboid, MobProjectModel } from '../../domain/MobProjectModel'
import {
  InvalidGeometryError,
  computeBoneRemovalImpact,
  createBone,
  createCuboid,
  duplicateCuboid,
  moveCuboid,
  removeBoneCascade,
  removeCuboid,
  resizeCuboid,
  rotateCuboid,
  setBonePivot,
  setBoneRotation,
} from '../geometryOperations'

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
  return {
    id,
    name: id,
    boneId,
    from: [-4, 12, -2],
    to: [4, 24, 2],
    origin: [0, 18, 0],
    rotation: [0, 0, 0],
    faces: EMPTY_FACES,
  }
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
 * Ticket 119. Un mob generado a densidad X4 tiene sus caras 4 veces más
 * grandes por eje. `refreshUv` recalcula el UV de TODOS los cuboids en cada
 * operación manual del editor, y `autoUv.ts` no conoce la densidad: siempre
 * calculaba a X1. Mover un solo cuboid recalculaba todo a X1, tirando abajo
 * el trabajo del ticket 109 y desalineando la textura ya pintada.
 */
describe('ticket 119: las operaciones manuales no degradan la densidad de téxel', () => {
  /** Cuboid de 8x12x4 unidades. A X4 su cruz de box-unwrap mide 2*(32+16)=96 de ancho y 16+48=64 de alto. */
  function modelAX4(): MobProjectModel {
    const c = cuboid('c', 'b')
    const x4 = {
      // Layout X4 en offset (0,0): up/down arriba (alto z=16), el resto abajo (alto y=48).
      up: { uv: [16, 0, 48, 16] as [number, number, number, number], texture: 0 },
      down: { uv: [48, 0, 80, 16] as [number, number, number, number], texture: 0 },
      west: { uv: [0, 16, 16, 64] as [number, number, number, number], texture: 0 },
      north: { uv: [16, 16, 48, 64] as [number, number, number, number], texture: 0 },
      east: { uv: [48, 16, 64, 64] as [number, number, number, number], texture: 0 },
      south: { uv: [64, 16, 96, 64] as [number, number, number, number], texture: 0 },
    }
    const model = modelWith([bone('b', null)], [{ ...c, faces: x4 }])
    return {
      ...model,
      texture: { width: 256, height: 256, storageKey: 'textures/pintada.png' },
      uv: {
        textureWidth: 256,
        textureHeight: 256,
        regions: (['north', 'south', 'east', 'west', 'up', 'down'] as const).map((face) => ({
          cuboidId: 'c',
          face,
          rect: x4[face].uv,
          status: 'painted' as const,
        })),
        reservations: [],
      },
    }
  }

  /**
   * Precisión que costó un test: `refreshUv` NO corre en mover/redimensionar/
   * rotar (esas van por `replaceCuboid` a secas). Corre solo al **crear** y
   * **duplicar** un cuboid, que son las dos operaciones que cambian el
   * conjunto a empaquetar. Ahí es donde el layout entero se rehace a X1.
   */
  it('crear un cuboid conserva la escala X4 de los cuboids que ya existían', () => {
    const model = modelAX4()

    const { model: result } = createCuboid(model, 'b', 'nuevo', [0, 0, 0], [4, 4, 4], [0, 0, 0])

    const existente = result.cuboids.find((c) => c.id === 'c')!
    const north = existente.faces.north.uv
    // Un cuboid de 8x12 mide 32x48 a X4; a X1 mediría 8x12.
    expect(north[2] - north[0], 'ancho de la cara north en téxels').toBe(32)
    expect(north[3] - north[1], 'alto de la cara north en téxels').toBe(48)
  })

  it('duplicar un cuboid conserva la escala X4 del original', () => {
    const model = modelAX4()

    const { model: result } = duplicateCuboid(model, 'c')

    const original = result.cuboids.find((c) => c.id === 'c')!
    const north = original.faces.north.uv
    expect(north[2] - north[0]).toBe(32)
    expect(north[3] - north[1]).toBe(48)
  })

  it('las operaciones que NO cambian el conjunto (mover) no tocan el UV en absoluto', () => {
    const model = modelAX4()
    const antes = model.cuboids[0]!.faces

    const result = moveCuboid(model, 'c', [3, -2, 1])

    const despues = result.cuboids[0]!.faces
    for (const face of ['north', 'south', 'east', 'west', 'up', 'down'] as const) {
      expect(despues[face].uv, `la cara ${face} no debería moverse en el atlas`).toEqual(antes[face].uv)
    }
  })
})

describe('moveCuboid', () => {
  it('traslada from/to/origin por igual (traslación rígida)', () => {
    const model = modelWith([bone('b', null)], [cuboid('c', 'b')])

    const result = moveCuboid(model, 'c', [1, -2, 0.5])

    const moved = result.cuboids[0]!
    expect(moved.from).toEqual([-3, 10, -1.5])
    expect(moved.to).toEqual([5, 22, 2.5])
    expect(moved.origin).toEqual([1, 16, 0.5])
  })

  it('nunca muta el modelo de entrada', () => {
    const model = modelWith([bone('b', null)], [cuboid('c', 'b')])
    const originalFrom = model.cuboids[0]!.from

    moveCuboid(model, 'c', [1, 1, 1])

    expect(model.cuboids[0]!.from).toBe(originalFrom)
  })

  it('rechaza un cuboidId inexistente', () => {
    const model = modelWith([bone('b', null)], [])
    expect(() => moveCuboid(model, 'no-existe', [1, 0, 0])).toThrow(InvalidGeometryError)
  })
})

describe('resizeCuboid', () => {
  it('escala (to-from) por eje manteniendo el CENTRO fijo', () => {
    // from=[-4,12,-2] to=[4,24,2] -> centro=[0,18,0], tamaño=[8,12,4]
    const model = modelWith([bone('b', null)], [cuboid('c', 'b')])

    const result = resizeCuboid(model, 'c', [1.5, 1, 2])

    const resized = result.cuboids[0]!
    expect(resized.from).toEqual([-6, 12, -4])
    expect(resized.to).toEqual([6, 24, 4])
  })

  it('rechaza scale <= 0 en cualquier eje', () => {
    const model = modelWith([bone('b', null)], [cuboid('c', 'b')])
    expect(() => resizeCuboid(model, 'c', [1, 0, 1])).toThrow(InvalidGeometryError)
    expect(() => resizeCuboid(model, 'c', [-1, 1, 1])).toThrow(InvalidGeometryError)
  })
})

describe('rotateCuboid', () => {
  it('SUMA el delta a la rotación existente (no reemplaza)', () => {
    const model = modelWith([bone('b', null)], [{ ...cuboid('c', 'b'), rotation: [0, 0, 10] }])

    const result = rotateCuboid(model, 'c', [0, 0, 5])

    expect(result.cuboids[0]!.rotation).toEqual([0, 0, 15])
  })
})

describe('setBonePivot / setBoneRotation', () => {
  it('REEMPLAZAN de forma absoluta (no delta)', () => {
    const model = modelWith([{ ...bone('b', null), pivot: [1, 1, 1], rotation: [5, 5, 5] }], [])

    const afterPivot = setBonePivot(model, 'b', [9, 9, 9])
    const afterRotation = setBoneRotation(afterPivot, 'b', [45, 0, 0])

    expect(afterRotation.bones[0]!.pivot).toEqual([9, 9, 9])
    expect(afterRotation.bones[0]!.rotation).toEqual([45, 0, 0])
  })
})

describe('createCuboid', () => {
  it('genera un id real (no vacío, no hardcodeado) y asigna UV válida vía AutoUv', () => {
    const model = modelWith([bone('b', null)], [])

    const result = createCuboid(model, 'b', 'nuevo', [-4, 0, -4], [4, 8, 4], [0, 0, 0])

    expect(result.cuboidId).toBeTruthy()
    const created = result.model.cuboids.find((c) => c.id === result.cuboidId)!
    expect(created.name).toBe('nuevo')
    // AutoUv corrió -- ya no es el placeholder [0,0,0,0]/null.
    expect(created.faces.north.texture).toBe(0)
    expect(created.faces.north.uv).not.toEqual([0, 0, 0, 0])
  })

  it('rechaza dimensiones <= 0', () => {
    const model = modelWith([bone('b', null)], [])
    expect(() => createCuboid(model, 'b', 'malo', [0, 0, 0], [0, 4, 4], [0, 0, 0])).toThrow(InvalidGeometryError)
  })

  it('rechaza un boneId inexistente', () => {
    const model = modelWith([], [])
    expect(() => createCuboid(model, 'no-existe', 'x', [0, 0, 0], [1, 1, 1], [0, 0, 0])).toThrow(InvalidGeometryError)
  })
})

describe('createBone', () => {
  it('genera un id real y soporta parentId null (bone raíz)', () => {
    const model = modelWith([], [])
    const result = createBone(model, null, 'root', [0, 0, 0], [0, 0, 0])
    expect(result.boneId).toBeTruthy()
    expect(result.model.bones[0]!.parentId).toBeNull()
  })

  it('rechaza un parentId inexistente', () => {
    const model = modelWith([], [])
    expect(() => createBone(model, 'no-existe', 'x', [0, 0, 0], [0, 0, 0])).toThrow(InvalidGeometryError)
  })
})

describe('removeCuboid', () => {
  it('elimina el cuboid y hace cascade de sus uv.regions', () => {
    const model: MobProjectModel = {
      ...modelWith([bone('b', null)], [cuboid('c', 'b')]),
      uv: {
        textureWidth: 64,
        textureHeight: 64,
        regions: [{ cuboidId: 'c', face: 'north', rect: [0, 0, 8, 8], status: 'unpainted' }],
        reservations: [],
      },
    }

    const result = removeCuboid(model, 'c')

    expect(result.cuboids).toHaveLength(0)
    expect(result.uv.regions).toHaveLength(0)
  })
})

describe('computeBoneRemovalImpact / removeBoneCascade', () => {
  it('calcula bones y cuboids afectados en cascada (AC #3: advertencia antes de confirmar)', () => {
    const root = bone('root', null)
    const child = bone('child', 'root')
    const grandchild = bone('grandchild', 'child')
    const model = modelWith([root, child, grandchild], [cuboid('onChild', 'child'), cuboid('onRoot', 'root')])

    const impact = computeBoneRemovalImpact(model, 'child')

    expect(impact.affectedBoneIds.sort()).toEqual(['child', 'grandchild'])
    expect(impact.affectedCuboidIds).toEqual(['onChild'])
  })

  it('removeBoneCascade elimina el bone, sus descendientes, y los cuboids de todos ellos', () => {
    const root = bone('root', null)
    const child = bone('child', 'root')
    const model = modelWith([root, child], [cuboid('onChild', 'child'), cuboid('onRoot', 'root')])

    const result = removeBoneCascade(model, 'child')

    expect(result.bones.map((b) => b.id)).toEqual(['root'])
    expect(result.cuboids.map((c) => c.id)).toEqual(['onRoot'])
  })
})

describe('duplicateCuboid', () => {
  it('crea una copia independiente con id nuevo y su propia UV', () => {
    const model = modelWith([bone('b', null)], [cuboid('original', 'b')])

    const result = duplicateCuboid(model, 'original')

    expect(result.cuboidId).not.toBe('original')
    expect(result.model.cuboids).toHaveLength(2)
    const original = result.model.cuboids.find((c) => c.id === 'original')!
    const copy = result.model.cuboids.find((c) => c.id === result.cuboidId)!
    expect(copy.boneId).toBe(original.boneId)
    expect(copy.from).toEqual(original.from)
    // Ambos recibieron UV fresca de AutoUv, en posiciones DISTINTAS del atlas (no se superponen).
    expect(copy.faces.north.uv).not.toEqual(original.faces.north.uv)
  })

  it('rechaza un cuboidId inexistente', () => {
    const model = modelWith([bone('b', null)], [])
    expect(() => duplicateCuboid(model, 'no-existe')).toThrow(InvalidGeometryError)
  })
})
