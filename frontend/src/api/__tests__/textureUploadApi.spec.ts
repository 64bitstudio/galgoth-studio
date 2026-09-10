import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../ApiError'
import { uploadTexture } from '../textureUploadApi'

describe('textureUploadApi', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn())
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('hace PUT al endpoint del mob con el PNG crudo en el body y devuelve el storageKey oficial', async () => {
    const png = new Blob(['fake-png'], { type: 'image/png' })
    ;(fetch as unknown as ReturnType<typeof vi.fn>).mockResolvedValue({
      ok: true,
      json: async () => ({ storageKey: 'textures/abc123.png' }),
    })

    const result = await uploadTexture('mob-1', png)

    expect(fetch).toHaveBeenCalledWith(
      expect.stringContaining('/api/mobs/mob-1/texture'),
      expect.objectContaining({
        method: 'PUT',
        headers: { 'Content-Type': 'image/png' },
        body: png,
      }),
    )
    expect(result).toEqual({ storageKey: 'textures/abc123.png' })
  })

  it('propaga un error HTTP como ApiError con el mensaje/code del backend', async () => {
    const png = new Blob(['fake-png'], { type: 'image/png' })
    ;(fetch as unknown as ReturnType<typeof vi.fn>).mockResolvedValue({
      ok: false,
      status: 400,
      json: async () => ({ error: 'INVALID_TEXTURE', message: 'El archivo no se pudo decodificar como un PNG válido.' }),
    })

    await expect(uploadTexture('mob-1', png)).rejects.toMatchObject({
      message: 'El archivo no se pudo decodificar como un PNG válido.',
      status: 400,
      code: 'INVALID_TEXTURE',
    })
    await expect(uploadTexture('mob-1', png)).rejects.toBeInstanceOf(ApiError)
  })
})
