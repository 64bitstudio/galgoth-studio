import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { encodeAtlasToPngBlob, PngEncodeError } from '../textureAtlasEncode'
import type { TextureAtlas } from '../textureEditorStore'

function atlas(width: number, height: number): TextureAtlas {
  return { width, height, pixels: new Uint8ClampedArray(width * height * 4).fill(9) }
}

// jsdom (entorno de test) no implementa el constructor real de `ImageData`
// (mismo motivo documentado en `TextureCanvas.vue`/`pngImportDecode.ts`
// para el resto del canvas real) -- se stubea acá un equivalente mínimo
// SOLO para que `putImageData` reciba un objeto con la forma esperada;
// `getContext`/`toBlob` siguen mockeados por test, así que ningún canvas
// real se toca.
class FakeImageData {
  data: Uint8ClampedArray
  width: number
  height: number
  constructor(data: Uint8ClampedArray, width: number, height: number) {
    this.data = data
    this.width = width
    this.height = height
  }
}

describe('encodeAtlasToPngBlob', () => {
  beforeEach(() => {
    vi.stubGlobal('ImageData', FakeImageData)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  it('dibuja el atlas 1:1 sobre un canvas de su mismo tamaño y devuelve el Blob PNG de toBlob', async () => {
    const putImageData = vi.fn()
    const toBlob = vi.fn((callback: BlobCallback) => callback(new Blob(['png-bytes'], { type: 'image/png' })))
    vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue({ putImageData } as unknown as CanvasRenderingContext2D)
    vi.spyOn(HTMLCanvasElement.prototype, 'toBlob').mockImplementation(toBlob)

    const result = await encodeAtlasToPngBlob(atlas(4, 3))

    expect(putImageData).toHaveBeenCalledWith(expect.objectContaining({ width: 4, height: 3 }), 0, 0)
    expect(toBlob).toHaveBeenCalledWith(expect.any(Function), 'image/png')
    expect(result.type).toBe('image/png')
  })

  it('lanza PngEncodeError con un mensaje claro si no hay contexto 2D disponible', async () => {
    vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue(null)

    await expect(encodeAtlasToPngBlob(atlas(2, 2))).rejects.toBeInstanceOf(PngEncodeError)
    await expect(encodeAtlasToPngBlob(atlas(2, 2))).rejects.toThrow(/lienzo/i)
  })

  it('lanza PngEncodeError con un mensaje claro si toBlob devuelve null', async () => {
    vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue({ putImageData: vi.fn() } as unknown as CanvasRenderingContext2D)
    vi.spyOn(HTMLCanvasElement.prototype, 'toBlob').mockImplementation((callback: BlobCallback) => callback(null))

    await expect(encodeAtlasToPngBlob(atlas(2, 2))).rejects.toBeInstanceOf(PngEncodeError)
    await expect(encodeAtlasToPngBlob(atlas(2, 2))).rejects.toThrow(/toBlob devolvió null/i)
  })
})
