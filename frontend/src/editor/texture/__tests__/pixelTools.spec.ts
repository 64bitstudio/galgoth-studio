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
  type RgbaColor,
} from '../pixelTools'

const RED: RgbaColor = [255, 0, 0, 255]
const BLUE: RgbaColor = [0, 0, 255, 255]

function blankPixels(width: number, height: number): Uint8ClampedArray {
  return new Uint8ClampedArray(width * height * 4)
}

describe('stampSquare (Pincel/Borrador)', () => {
  it('pinta el color exacto, sin antialiasing -- cada byte RGBA coincide exactamente con el color pedido', () => {
    const pixels = blankPixels(8, 8)
    const bounds = createEmptyBounds()

    stampSquare(pixels, 8, 8, 4, 4, 1, RED, bounds)

    expect(getPixelColor(pixels, 8, 4, 4)).toEqual(RED)
    // Ningún píxel vecino se tocó -- nada de "sangrado"/blend.
    expect(getPixelColor(pixels, 8, 3, 4)).toEqual(TRANSPARENT)
    expect(getPixelColor(pixels, 8, 5, 4)).toEqual(TRANSPARENT)
  })

  it('el tamaño del trazo es EXACTAMENTE sizePx x sizePx píxeles del atlas -- verificado con size=3', () => {
    const pixels = blankPixels(10, 10)
    const bounds = createEmptyBounds()

    stampSquare(pixels, 10, 10, 5, 5, 3, RED, bounds)
    const rect = boundsToRect(bounds)!

    expect(rect).toEqual({ x: 4, y: 4, width: 3, height: 3 })
    for (let y = 4; y < 7; y += 1) {
      for (let x = 4; x < 7; x += 1) {
        expect(getPixelColor(pixels, 10, x, y)).toEqual(RED)
      }
    }
    // Fuera del cuadrado 3x3, nada se pintó.
    expect(getPixelColor(pixels, 10, 3, 5)).toEqual(TRANSPARENT)
    expect(getPixelColor(pixels, 10, 7, 5)).toEqual(TRANSPARENT)
  })

  it('recorta contra los límites del atlas sin lanzar ni escribir fuera de rango', () => {
    const pixels = blankPixels(4, 4)
    const bounds = createEmptyBounds()

    expect(() => stampSquare(pixels, 4, 4, 0, 0, 5, RED, bounds)).not.toThrow()
    const rect = boundsToRect(bounds)!
    // El cuadrado de 5x5 centrado en (0,0) se recorta a los 4x4 reales.
    expect(rect.x).toBeGreaterThanOrEqual(0)
    expect(rect.y).toBeGreaterThanOrEqual(0)
    expect(rect.x + rect.width).toBeLessThanOrEqual(4)
    expect(rect.y + rect.height).toBeLessThanOrEqual(4)
  })

  it('boundsToRect() es null si nunca se extendió (ej. todo el trazo cayó fuera del atlas)', () => {
    expect(boundsToRect(createEmptyBounds())).toBeNull()
  })
})

describe('stampLine (trazo continuo entre pointermove)', () => {
  it('conecta dos puntos sin huecos -- cada píxel de la línea recibe el color exacto', () => {
    const pixels = blankPixels(10, 10)
    const bounds = createEmptyBounds()

    stampLine(pixels, 10, 10, 1, 1, 5, 1, 1, RED, bounds)

    for (let x = 1; x <= 5; x += 1) {
      expect(getPixelColor(pixels, 10, x, 1)).toEqual(RED)
    }
  })

  it('Borrador reutiliza la misma función con TRANSPARENT -- dos herramientas, cero duplicación de lógica de trazo', () => {
    const pixels = blankPixels(10, 10)
    stampSquare(pixels, 10, 10, 5, 5, 3, RED, createEmptyBounds())

    const eraseBounds = createEmptyBounds()
    stampLine(pixels, 10, 10, 4, 5, 6, 5, 1, TRANSPARENT, eraseBounds)

    expect(getPixelColor(pixels, 10, 5, 5)).toEqual(TRANSPARENT)
    // Las filas 4 y 6 (fuera de la línea del borrador) siguen rojas.
    expect(getPixelColor(pixels, 10, 5, 4)).toEqual(RED)
    expect(getPixelColor(pixels, 10, 5, 6)).toEqual(RED)
  })
})

describe('computeFloodFill (Cubeta)', () => {
  it('rellena la región de color contiguo, nunca fuera de sus límites de color', () => {
    const pixels = blankPixels(6, 6) // todo transparente
    // Dibuja un cuadrado azul de 2x2 en el medio -- el resto sigue transparente.
    stampSquare(pixels, 6, 6, 2, 2, 2, BLUE, createEmptyBounds())
    stampSquare(pixels, 6, 6, 3, 2, 1, BLUE, createEmptyBounds())
    // (para simplificar, aseguremos una región azul rectangular exacta 2x2 en (1,1)-(2,2))

    const region = blankPixels(6, 6)
    for (let y = 1; y <= 2; y += 1) {
      for (let x = 1; x <= 2; x += 1) {
        const i = (y * 6 + x) * 4
        region.set(BLUE, i)
      }
    }

    const result = computeFloodFill(region, 6, 6, 1, 1, RED)!
    expect(result).not.toBeNull()
    expect(result.rect).toEqual({ x: 1, y: 1, width: 2, height: 2 })

    // Aplicar el resultado sobre una copia y verificar que SOLO la región azul cambió.
    const applied = region.slice()
    for (let row = 0; row < result.rect.height; row += 1) {
      const destStart = ((result.rect.y + row) * 6 + result.rect.x) * 4
      applied.set(result.afterPixels.subarray(row * result.rect.width * 4, (row + 1) * result.rect.width * 4), destStart)
    }
    expect(getPixelColor(applied, 6, 1, 1)).toEqual(RED)
    expect(getPixelColor(applied, 6, 2, 2)).toEqual(RED)
    // Fuera de la región azul original (transparente), nada cambió.
    expect(getPixelColor(applied, 6, 0, 0)).toEqual(TRANSPARENT)
    expect(getPixelColor(applied, 6, 4, 4)).toEqual(TRANSPARENT)
  })

  it('no cruza un borde de color distinto -- dos regiones separadas por una línea no se mezclan', () => {
    const pixels = blankPixels(5, 1)
    // [BLUE, BLUE, RED(borde), BLUE, BLUE] en una fila -- dos regiones azules separadas.
    pixels.set(BLUE, 0)
    pixels.set(BLUE, 4)
    pixels.set(RED, 8)
    pixels.set(BLUE, 12)
    pixels.set(BLUE, 16)

    const result = computeFloodFill(pixels, 5, 1, 0, 0, [0, 255, 0, 255])!
    expect(result.rect).toEqual({ x: 0, y: 0, width: 2, height: 1 })
  })

  it('es un no-op explícito (null) si el color de relleno ya es el color vigente -- nunca un Command vacío', () => {
    const pixels = blankPixels(4, 4)
    stampSquare(pixels, 4, 4, 1, 1, 1, RED, createEmptyBounds())

    expect(computeFloodFill(pixels, 4, 4, 1, 1, RED)).toBeNull()
  })

  it('null si el punto de partida cae fuera del atlas', () => {
    const pixels = blankPixels(4, 4)
    expect(computeFloodFill(pixels, 4, 4, -1, 0, RED)).toBeNull()
    expect(computeFloodFill(pixels, 4, 4, 4, 0, RED)).toBeNull()
  })
})

describe('pickColorAt (Eyedropper)', () => {
  it('toma el color exacto del píxel clickeado', () => {
    const pixels = blankPixels(4, 4)
    stampSquare(pixels, 4, 4, 2, 2, 1, RED, createEmptyBounds())

    expect(pickColorAt(pixels, 4, 4, 2, 2)).toEqual(RED)
    expect(pickColorAt(pixels, 4, 4, 0, 0)).toEqual(TRANSPARENT)
  })

  it('null fuera de los límites del atlas', () => {
    const pixels = blankPixels(4, 4)
    expect(pickColorAt(pixels, 4, 4, -1, 0)).toBeNull()
    expect(pickColorAt(pixels, 4, 4, 0, 4)).toBeNull()
  })
})
