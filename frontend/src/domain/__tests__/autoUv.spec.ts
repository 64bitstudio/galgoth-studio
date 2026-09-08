import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'
import { layoutUv, UvAtlasOverflowError } from '../autoUv'
import type { Cuboid, FaceName } from '../MobProjectModel'

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

function cube(id: string, boneId: string, size: number): Cuboid {
  const half = size / 2
  return {
    id,
    name: `cube-${id}`,
    boneId,
    from: [-half, -half, -half],
    to: [half, half, half],
    origin: [0, 0, 0],
    rotation: [0, 0, 0],
    faces: EMPTY_FACES,
  }
}

describe('AutoUv (TS) -- AlphaAutoPackStrategy', () => {
  it('un solo cuboid recibe el desenvolvimiento de caja estándar de Minecraft', () => {
    const head = cube('head', 'bone-1', 8)

    const result = layoutUv([head], 64, 64)

    const faces = result.cuboids[0]!.faces
    expect(faces.up.uv).toEqual([8, 0, 16, 8])
    expect(faces.down.uv).toEqual([16, 0, 24, 8])
    expect(faces.west.uv).toEqual([0, 8, 8, 16])
    expect(faces.north.uv).toEqual([8, 8, 16, 16])
    expect(faces.east.uv).toEqual([16, 8, 24, 16])
    expect(faces.south.uv).toEqual([24, 8, 32, 16])
    expect(faces.north.texture).toBe(0)
  })

  it('múltiples cuboids se empaquetan fila por fila sin superponerse', () => {
    const a = cube('a', 'bone-1', 8)
    const b = cube('b', 'bone-1', 8)
    const c = cube('c', 'bone-1', 8)

    const result = layoutUv([a, b, c], 64, 64)

    expect(result.cuboids[0]!.faces.up.uv[0]).toBe(8) // fila 1, columna 0
    expect(result.cuboids[1]!.faces.up.uv[0]).toBe(40) // fila 1, columna 1 (offsetX=32)
    expect(result.cuboids[2]!.faces.up.uv[1]).toBe(16) // fila 2 -- envolvió por ancho
  })

  it('modelo que no cabe en el atlas lanza UvAtlasOverflowError con las dimensiones mínimas que sí cabrían', () => {
    const head = cube('head', 'bone-1', 8) // footprint 32x16
    let captured: UvAtlasOverflowError | undefined

    expect(() => {
      try {
        layoutUv([head], 8, 8)
      } catch (e) {
        captured = e as UvAtlasOverflowError
        throw e
      }
    }).toThrow(UvAtlasOverflowError)

    expect(captured?.currentWidth).toBe(8)
    expect(captured?.currentHeight).toBe(8)
    expect(captured?.requiredWidth).toBe(32)
    expect(captured?.requiredHeight).toBe(16)
  })

  it('el mismo modelo de entrada produce exactamente el mismo layout dos veces (determinismo)', () => {
    const input = [cube('head', 'bone-1', 8), cube('body', 'bone-1', 12), cube('arm', 'bone-2', 6)]

    const first = layoutUv(input, 64, 64)
    const second = layoutUv(input, 64, 64)

    expect(first.cuboids).toEqual(second.cuboids)
    expect(first.regions).toEqual(second.regions)
  })

  it('AC de la HU-16: el cálculo es síncrono (sin red) -- se puede llamar directo en un Command', () => {
    // No hay HTTP/fetch en layoutUv -- si lo hubiera, esta llamada sería
    // asíncrona (retornaría una Promise). El tipo de retorno síncrono es
    // en sí la prueba de que no depende de red.
    const result = layoutUv([cube('head', 'bone-1', 8)], 64, 64)
    expect(result.cuboids).toHaveLength(1)
  })
})

describe('AutoUv (TS) -- paridad con el backend (fixture compartida)', () => {
  interface UvLayoutFixture {
    atlas: { textureWidth: number; textureHeight: number }
    cuboids: Cuboid[]
    expectedFaces: Record<string, Record<FaceName, [number, number, number, number]>>
  }

  const fixture: UvLayoutFixture = JSON.parse(
    readFileSync(resolve(REPO_ROOT, 'contracts/fixtures/uv-layout-fixture.json'), 'utf-8'),
  )

  it('produce exactamente la misma UV que el fixture compartido con backend (Java)', () => {
    const result = layoutUv(fixture.cuboids, fixture.atlas.textureWidth, fixture.atlas.textureHeight)

    for (const cuboid of result.cuboids) {
      const expectedFaces = fixture.expectedFaces[cuboid.id]
      expect(expectedFaces).toBeDefined()
      for (const faceName of Object.keys(expectedFaces!) as FaceName[]) {
        expect(cuboid.faces[faceName].uv).toEqual(expectedFaces![faceName])
      }
    }
  })
})
