import { describe, expect, it } from 'vitest'
import type { AtlasBuffer } from '../pixelTools'
import * as textureImportTools from '../textureImportTools'
import { cropOrPad, describeImportAdjustment } from '../textureImportTools'

function solidAtlas(width: number, height: number, color: [number, number, number, number]): AtlasBuffer {
  const pixels = new Uint8ClampedArray(width * height * 4)
  for (let i = 0; i < pixels.length; i += 4) {
    pixels.set(color, i)
  }
  return { pixels, width, height }
}

function pixelAt(atlas: { pixels: Uint8ClampedArray; width: number }, x: number, y: number): number[] {
  const i = (y * atlas.width + x) * 4
  return [atlas.pixels[i]!, atlas.pixels[i + 1]!, atlas.pixels[i + 2]!, atlas.pixels[i + 3]!]
}

describe('textureImportTools', () => {
  describe('cropOrPad', () => {
    it('AC B (dimensiones distintas, más grande): recorta el excedente por la derecha/abajo -- byte a byte, sin resamplear', () => {
      const source = solidAtlas(4, 4, [255, 0, 0, 255])
      const result = cropOrPad(source, 2, 2)

      expect(result.width).toBe(2)
      expect(result.height).toBe(2)
      for (let y = 0; y < 2; y += 1) {
        for (let x = 0; x < 2; x += 1) {
          expect(pixelAt(result, x, y)).toEqual([255, 0, 0, 255])
        }
      }
    })

    it('AC B (dimensiones distintas, más chico): rellena el margen faltante con transparente (0,0,0,0), preserva el contenido original intacto', () => {
      const source = solidAtlas(2, 2, [0, 255, 0, 255])
      const result = cropOrPad(source, 4, 4)

      expect(result.width).toBe(4)
      expect(result.height).toBe(4)
      // Contenido original preservado en la esquina superior izquierda.
      expect(pixelAt(result, 0, 0)).toEqual([0, 255, 0, 255])
      expect(pixelAt(result, 1, 1)).toEqual([0, 255, 0, 255])
      // Margen agregado: transparente, nunca un estiramiento del verde.
      expect(pixelAt(result, 2, 0)).toEqual([0, 0, 0, 0])
      expect(pixelAt(result, 3, 3)).toEqual([0, 0, 0, 0])
      expect(pixelAt(result, 0, 3)).toEqual([0, 0, 0, 0])
    })

    it('crop en un eje + pad en el otro simultáneamente -- cada eje se resuelve de forma independiente', () => {
      const source = solidAtlas(6, 2, [0, 0, 255, 255]) // más ancho, más bajo que el destino
      const result = cropOrPad(source, 3, 4)

      expect(result.width).toBe(3)
      expect(result.height).toBe(4)
      // Fila 0/1 (dentro del alto original): recortadas a las primeras 3 columnas.
      expect(pixelAt(result, 0, 0)).toEqual([0, 0, 255, 255])
      expect(pixelAt(result, 2, 1)).toEqual([0, 0, 255, 255])
      // Fila 2/3 (fuera del alto original): pad transparente.
      expect(pixelAt(result, 0, 2)).toEqual([0, 0, 0, 0])
      expect(pixelAt(result, 2, 3)).toEqual([0, 0, 0, 0])
    })

    it('dimensiones exactas: copia el contenido sin alterar ni un byte', () => {
      const source = solidAtlas(3, 3, [10, 20, 30, 200])
      const result = cropOrPad(source, 3, 3)

      expect(result.pixels).toEqual(source.pixels)
      expect(result.pixels).not.toBe(source.pixels) // copia nueva, no la misma referencia
    })

    it('nunca muta el buffer de origen', () => {
      const source = solidAtlas(4, 4, [1, 2, 3, 4])
      const before = source.pixels.slice()
      cropOrPad(source, 2, 2)
      expect(source.pixels).toEqual(before)
    })
  })

  describe('describeImportAdjustment', () => {
    it('dimensiones exactas: matches=true, ningún crop/pad', () => {
      expect(describeImportAdjustment(8, 8, 8, 8)).toEqual({ matches: true, cropWidth: 0, cropHeight: 0, padWidth: 0, padHeight: 0 })
    })

    it('origen más grande en ambos ejes: solo crop, nunca pad', () => {
      expect(describeImportAdjustment(10, 12, 8, 8)).toEqual({ matches: false, cropWidth: 2, cropHeight: 4, padWidth: 0, padHeight: 0 })
    })

    it('origen más chico en ambos ejes: solo pad, nunca crop', () => {
      expect(describeImportAdjustment(4, 5, 8, 8)).toEqual({ matches: false, cropWidth: 0, cropHeight: 0, padWidth: 4, padHeight: 3 })
    })

    it('un eje se recorta y el otro se rellena a la vez', () => {
      expect(describeImportAdjustment(10, 4, 8, 8)).toEqual({ matches: false, cropWidth: 2, cropHeight: 0, padWidth: 0, padHeight: 4 })
    })
  })

  it('AC B: el módulo NUNCA expone ninguna operación de escalado/resize -- verificado explícitamente, no solo por ausencia de UI', () => {
    const exportedNames = Object.keys(textureImportTools)
    expect(exportedNames).toEqual(['cropOrPad', 'describeImportAdjustment'])
    for (const name of exportedNames) {
      expect(name).not.toMatch(/scale|resize|resample|stretch/i)
    }
  })
})
