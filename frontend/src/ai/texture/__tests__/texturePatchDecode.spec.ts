import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { TexturePatchDecodeError, decodeTexturePreviewPatch } from '../texturePatchDecode'
import type { TexturePreviewPatchPayload } from '../textureGenerationEvents'

const RECT = { x: 4, y: 8, width: 16, height: 16 }

describe('decodeTexturePreviewPatch', () => {
  let fakeBitmap: ImageBitmap

  beforeEach(() => {
    fakeBitmap = { width: 16, height: 16, close: vi.fn() } as unknown as ImageBitmap
    vi.stubGlobal(
      'createImageBitmap',
      vi.fn(async () => fakeBitmap),
    )
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('decodifica un payload base64 inline sin llamar a fetch', async () => {
    vi.stubGlobal('fetch', vi.fn())
    const payload: TexturePreviewPatchPayload = { type: 'preview_texture_patch', rect: RECT, encoding: 'base64', data: btoa('fake-png-bytes') }

    const bitmap = await decodeTexturePreviewPatch(payload)

    expect(bitmap).toBe(fakeBitmap)
    expect(fetch).not.toHaveBeenCalled()
    expect(createImageBitmap).toHaveBeenCalledTimes(1)
  })

  it('descarga un payload asset_url vía fetch prefijado con API_BASE_URL', async () => {
    const blob = new Blob(['fake'])
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => ({ ok: true, blob: async () => blob })),
    )
    const payload: TexturePreviewPatchPayload = {
      type: 'preview_texture_patch',
      rect: RECT,
      encoding: 'asset_url',
      url: '/api/texture-previews/job-1/3.png',
    }

    const bitmap = await decodeTexturePreviewPatch(payload)

    expect(bitmap).toBe(fakeBitmap)
    expect(fetch).toHaveBeenCalledWith(expect.stringContaining('/api/texture-previews/job-1/3.png'))
  })

  it('un asset_url que responde no-ok lanza TexturePatchDecodeError sin llamar a createImageBitmap', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => ({ ok: false, status: 404 })),
    )
    const payload: TexturePreviewPatchPayload = { type: 'preview_texture_patch', rect: RECT, encoding: 'asset_url', url: '/api/x.png' }

    await expect(decodeTexturePreviewPatch(payload)).rejects.toBeInstanceOf(TexturePatchDecodeError)
    expect(createImageBitmap).not.toHaveBeenCalled()
  })

  it('si createImageBitmap falla, lanza TexturePatchDecodeError en vez de propagar el error crudo', async () => {
    vi.stubGlobal('fetch', vi.fn())
    vi.stubGlobal(
      'createImageBitmap',
      vi.fn(async () => {
        throw new Error('formato inválido')
      }),
    )
    const payload: TexturePreviewPatchPayload = { type: 'preview_texture_patch', rect: RECT, encoding: 'base64', data: btoa('x') }

    await expect(decodeTexturePreviewPatch(payload)).rejects.toBeInstanceOf(TexturePatchDecodeError)
  })
})
