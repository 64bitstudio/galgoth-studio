import { describe, expect, it } from 'vitest'
import { hexToRgba, rgbaToHex } from '../colorHex'

describe('colorHex', () => {
  it('hexToRgba: alfa siempre 255 (opaco) -- ningún control de opacidad parcial en este ticket', () => {
    expect(hexToRgba('#ff0000')).toEqual([255, 0, 0, 255])
    expect(hexToRgba('#00ff00')).toEqual([0, 255, 0, 255])
  })

  it('rgbaToHex ignora el alfa -- round-trip con hexToRgba preserva RGB', () => {
    expect(rgbaToHex([18, 52, 86, 255])).toBe('#123456')
    expect(hexToRgba(rgbaToHex([200, 10, 5, 255]))).toEqual([200, 10, 5, 255])
  })
})
