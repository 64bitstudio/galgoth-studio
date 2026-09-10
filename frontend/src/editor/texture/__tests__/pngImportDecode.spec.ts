import { afterEach, describe, expect, it, vi } from 'vitest'
import { decodePngFileToAtlasBuffer, PngDecodeError } from '../pngImportDecode'

describe('decodePngFileToAtlasBuffer', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  it('lanza PngDecodeError con un mensaje claro cuando el navegador no soporta createImageBitmap -- nunca falla en silencio', async () => {
    vi.stubGlobal('createImageBitmap', undefined)
    const file = new File([new Uint8Array([1, 2, 3])], 'x.png', { type: 'image/png' })

    await expect(decodePngFileToAtlasBuffer(file)).rejects.toBeInstanceOf(PngDecodeError)
    await expect(decodePngFileToAtlasBuffer(file)).rejects.toThrow(/no soporta/i)
  })

  it('lanza PngDecodeError con un mensaje claro cuando createImageBitmap rechaza (archivo no es una imagen válida)', async () => {
    vi.stubGlobal(
      'createImageBitmap',
      vi.fn().mockRejectedValue(new Error('not an image')),
    )
    const file = new File([new Uint8Array([1, 2, 3])], 'x.png', { type: 'image/png' })

    await expect(decodePngFileToAtlasBuffer(file)).rejects.toBeInstanceOf(PngDecodeError)
    await expect(decodePngFileToAtlasBuffer(file)).rejects.toThrow(/no se pudo decodificar/i)
  })

  it('decodifica el bitmap a un AtlasBuffer RGBA de las mismas dimensiones -- 1:1, sin escalar', async () => {
    const close = vi.fn()
    const fakeBitmap = { width: 2, height: 3, close }
    vi.stubGlobal('createImageBitmap', vi.fn().mockResolvedValue(fakeBitmap))

    const fakeImageData = { data: new Uint8ClampedArray(2 * 3 * 4).fill(7), width: 2, height: 3 }
    const drawImage = vi.fn()
    const getImageData = vi.fn().mockReturnValue(fakeImageData)
    vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue({ drawImage, getImageData } as unknown as CanvasRenderingContext2D)

    const file = new File([new Uint8Array([1, 2, 3])], 'x.png', { type: 'image/png' })
    const result = await decodePngFileToAtlasBuffer(file)

    expect(result.width).toBe(2)
    expect(result.height).toBe(3)
    expect(Array.from(result.pixels)).toEqual(Array.from(fakeImageData.data))
    expect(drawImage).toHaveBeenCalledWith(fakeBitmap, 0, 0)
    expect(getImageData).toHaveBeenCalledWith(0, 0, 2, 3)
    expect(close).toHaveBeenCalled() // el ImageBitmap se libera siempre, incluso si algo más falla
  })

  it('libera el ImageBitmap (close()) incluso si falla la lectura de píxeles', async () => {
    const close = vi.fn()
    const fakeBitmap = { width: 2, height: 2, close }
    vi.stubGlobal('createImageBitmap', vi.fn().mockResolvedValue(fakeBitmap))
    vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue(null)

    const file = new File([new Uint8Array([1, 2, 3])], 'x.png', { type: 'image/png' })
    await expect(decodePngFileToAtlasBuffer(file)).rejects.toBeInstanceOf(PngDecodeError)
    expect(close).toHaveBeenCalled()
  })
})
