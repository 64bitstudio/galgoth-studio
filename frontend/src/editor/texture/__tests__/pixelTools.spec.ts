import { describe, expect, it } from 'vitest'
import {
  boundsToRect,
  computeFloodFill,
  createEmptyBounds,
  getPixelColor,
  pickColorAt,
  stampLine,
  stampSquare,
  TRANSPARENT,
  type AtlasBuffer,
  type RgbaColor,
} from '../pixelTools'

const RED: RgbaColor = [255, 0, 0, 255]
const BLUE: RgbaColor = [0, 0, 255, 255]

function blankAtlas(width: number, height: number): AtlasBuffer {
  return { pixels: new Uint8ClampedArray(width * height * 4), width, height }
}

describe('stampSquare (Pincel/Borrador)', () => {
  it('pinta el color exacto, sin antialiasing -- cada byte RGBA coincide exactamente con el color pedido', () => {
    const atlas = blankAtlas(8, 8)
    const bounds = createEmptyBounds()

    stampSquare(atlas, { x: 4, y: 4 }, 1, RED, bounds)

    expect(getPixelColor(atlas, 4, 4)).toEqual(RED)
    // Ningún píxel vecino se tocó -- nada de "sangrado"/blend.
    expect(getPixelColor(atlas, 3, 4)).toEqual(TRANSPARENT)
    expect(getPixelColor(atlas, 5, 4)).toEqual(TRANSPARENT)
  })

  it('el tamaño del trazo es EXACTAMENTE sizePx x sizePx píxeles del atlas -- verificado con size=3', () => {
    const atlas = blankAtlas(10, 10)
    const bounds = createEmptyBounds()

    stampSquare(atlas, { x: 5, y: 5 }, 3, RED, bounds)
    const rect = boundsToRect(bounds)!

    expect(rect).toEqual({ x: 4, y: 4, width: 3, height: 3 })
    for (let y = 4; y < 7; y += 1) {
      for (let x = 4; x < 7; x += 1) {
        expect(getPixelColor(atlas, x, y)).toEqual(RED)
      }
    }
    // Fuera del cuadrado 3x3, nada se pintó.
    expect(getPixelColor(atlas, 3, 5)).toEqual(TRANSPARENT)
    expect(getPixelColor(atlas, 7, 5)).toEqual(TRANSPARENT)
  })

  it('recorta contra los límites del atlas sin lanzar ni escribir fuera de rango', () => {
    const atlas = blankAtlas(4, 4)
    const bounds = createEmptyBounds()

    expect(() => stampSquare(atlas, { x: 0, y: 0 }, 5, RED, bounds)).not.toThrow()
    const rect = boundsToRect(bounds)!
    // El cuadrado de 5x5 centrado en (0,0) se recorta a los 4x4 reales.
    expect(rect.x).toBeGreaterThanOrEqual(0)
    expect(rect.y).toBeGreaterThanOrEqual(0)
    expect(rect.x + rect.width).toBeLessThanOrEqual(4)
    expect(rect.y + rect.height).toBeLessThanOrEqual(4)
  })

  it('boundsToRect() es null si nunca se extendió (ej. el trazo completo cayó fuera del atlas)', () => {
    expect(boundsToRect(createEmptyBounds())).toBeNull()
  })
})

describe('stampLine (trazo continuo entre pointermove)', () => {
  it('conecta dos puntos sin huecos -- cada píxel de la línea recibe el color exacto', () => {
    const atlas = blankAtlas(10, 10)
    const bounds = createEmptyBounds()

    stampLine(atlas, { x: 1, y: 1 }, { x: 5, y: 1 }, 1, RED, bounds)

    for (let x = 1; x <= 5; x += 1) {
      expect(getPixelColor(atlas, x, 1)).toEqual(RED)
    }
  })

  it('Borrador reutiliza la misma función con TRANSPARENT -- dos herramientas, cero duplicación de lógica de trazo', () => {
    const atlas = blankAtlas(10, 10)
    stampSquare(atlas, { x: 5, y: 5 }, 3, RED, createEmptyBounds())

    const eraseBounds = createEmptyBounds()
    stampLine(atlas, { x: 4, y: 5 }, { x: 6, y: 5 }, 1, TRANSPARENT, eraseBounds)

    expect(getPixelColor(atlas, 5, 5)).toEqual(TRANSPARENT)
    // Las filas 4 y 6 (fuera de la línea del borrador) siguen rojas.
    expect(getPixelColor(atlas, 5, 4)).toEqual(RED)
    expect(getPixelColor(atlas, 5, 6)).toEqual(RED)
  })
})

describe('computeFloodFill (Cubeta)', () => {
  it('rellena la región de color contiguo, nunca fuera de sus límites de color', () => {
    // Región azul rectangular exacta 2x2 en (1,1)-(2,2), resto transparente.
    const region = blankAtlas(6, 6)
    for (let y = 1; y <= 2; y += 1) {
      for (let x = 1; x <= 2; x += 1) {
        const i = (y * 6 + x) * 4
        region.pixels.set(BLUE, i)
      }
    }

    const result = computeFloodFill(region, { x: 1, y: 1 }, RED)!
    expect(result).not.toBeNull()
    expect(result.rect).toEqual({ x: 1, y: 1, width: 2, height: 2 })

    // Aplicar el resultado sobre una copia y verificar que SOLO la región azul cambió.
    const applied = blankAtlas(6, 6)
    applied.pixels.set(region.pixels)
    for (let row = 0; row < result.rect.height; row += 1) {
      const destStart = ((result.rect.y + row) * 6 + result.rect.x) * 4
      applied.pixels.set(result.afterPixels.subarray(row * result.rect.width * 4, (row + 1) * result.rect.width * 4), destStart)
    }
    expect(getPixelColor(applied, 1, 1)).toEqual(RED)
    expect(getPixelColor(applied, 2, 2)).toEqual(RED)
    // Fuera de la región azul original (transparente), nada cambió.
    expect(getPixelColor(applied, 0, 0)).toEqual(TRANSPARENT)
    expect(getPixelColor(applied, 4, 4)).toEqual(TRANSPARENT)
  })

  it('no cruza un borde de color distinto -- dos regiones separadas por una línea no se mezclan', () => {
    const atlas = blankAtlas(5, 1)
    // [BLUE, BLUE, RED(borde), BLUE, BLUE] en una fila -- dos regiones azules separadas.
    atlas.pixels.set(BLUE, 0)
    atlas.pixels.set(BLUE, 4)
    atlas.pixels.set(RED, 8)
    atlas.pixels.set(BLUE, 12)
    atlas.pixels.set(BLUE, 16)

    const result = computeFloodFill(atlas, { x: 0, y: 0 }, [0, 255, 0, 255])!
    expect(result.rect).toEqual({ x: 0, y: 0, width: 2, height: 1 })
  })

  it('es un no-op explícito (null) si el color de relleno ya es el color vigente -- nunca un Command vacío', () => {
    const atlas = blankAtlas(4, 4)
    stampSquare(atlas, { x: 1, y: 1 }, 1, RED, createEmptyBounds())

    expect(computeFloodFill(atlas, { x: 1, y: 1 }, RED)).toBeNull()
  })

  it('null si el punto de partida cae fuera del atlas', () => {
    const atlas = blankAtlas(4, 4)
    expect(computeFloodFill(atlas, { x: -1, y: 0 }, RED)).toBeNull()
    expect(computeFloodFill(atlas, { x: 4, y: 0 }, RED)).toBeNull()
  })
})

describe('pickColorAt (Eyedropper)', () => {
  it('toma el color exacto del píxel clickeado', () => {
    const atlas = blankAtlas(4, 4)
    stampSquare(atlas, { x: 2, y: 2 }, 1, RED, createEmptyBounds())

    expect(pickColorAt(atlas, 2, 2)).toEqual(RED)
    expect(pickColorAt(atlas, 0, 0)).toEqual(TRANSPARENT)
  })

  it('null fuera de los límites del atlas', () => {
    const atlas = blankAtlas(4, 4)
    expect(pickColorAt(atlas, -1, 0)).toBeNull()
    expect(pickColorAt(atlas, 0, 4)).toBeNull()
  })
})
