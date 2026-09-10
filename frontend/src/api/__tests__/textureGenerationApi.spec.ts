import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../ApiError'
import { applyTexture, getTextureResult, startTextureGeneration } from '../textureGenerationApi'

describe('textureGenerationApi', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn())
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('startTextureGeneration hace POST al endpoint del mob con el body real y devuelve el jobId', async () => {
    ;(fetch as unknown as ReturnType<typeof vi.fn>).mockResolvedValue({
      ok: true,
      json: async () => ({ jobId: 'job-1' }),
    })

    const result = await startTextureGeneration('mob-1', { style: 'pixel_art', detailLevel: 'high', boneId: null })

    expect(fetch).toHaveBeenCalledWith(
      expect.stringContaining('/api/mobs/mob-1/ai/generate-texture'),
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify({ style: 'pixel_art', detailLevel: 'high', boneId: null }),
      }),
    )
    expect(result).toEqual({ jobId: 'job-1' })
  })

  it('startTextureGeneration propaga un 400 NO_REFERENCE_IMAGE como ApiError con code', async () => {
    ;(fetch as unknown as ReturnType<typeof vi.fn>).mockResolvedValue({
      ok: false,
      status: 400,
      json: async () => ({ error: 'NO_REFERENCE_IMAGE', message: 'El mob no tiene ninguna imagen de referencia.' }),
    })

    await expect(startTextureGeneration('mob-1', { style: 'faithful', detailLevel: 'low', boneId: null })).rejects.toMatchObject({
      message: 'El mob no tiene ninguna imagen de referencia.',
      status: 400,
      code: 'NO_REFERENCE_IMAGE',
    })
  })

  it('getTextureResult hace GET al diff Antes/Después', async () => {
    const resultBody = {
      jobId: 'job-1',
      mobId: 'mob-1',
      wholeModel: true,
      touchedBoneIds: ['b1'],
      touchedFaces: [],
      hasHandPaintedOverwrite: false,
      beforeAtlasPngBase64: 'AAA=',
      afterAtlasPngBase64: 'BBB=',
    }
    ;(fetch as unknown as ReturnType<typeof vi.fn>).mockResolvedValue({ ok: true, json: async () => resultBody })

    const result = await getTextureResult('job-1')

    expect(fetch).toHaveBeenCalledWith(expect.stringContaining('/api/jobs/job-1/texture-result'), undefined)
    expect(result).toEqual(resultBody)
  })

  it('applyTexture hace POST y devuelve revisionNumber/draftVersion', async () => {
    ;(fetch as unknown as ReturnType<typeof vi.fn>).mockResolvedValue({
      ok: true,
      json: async () => ({ revisionNumber: 3, draftVersion: 5 }),
    })

    const result = await applyTexture('job-1')

    expect(fetch).toHaveBeenCalledWith(expect.stringContaining('/api/jobs/job-1/apply-texture'), expect.objectContaining({ method: 'POST' }))
    expect(result).toEqual({ revisionNumber: 3, draftVersion: 5 })
  })

  it('applyTexture propaga un 409 STALE_TEXTURE_BASE como ApiError con code', async () => {
    ;(fetch as unknown as ReturnType<typeof vi.fn>).mockResolvedValue({
      ok: false,
      status: 409,
      json: async () => ({ error: 'STALE_TEXTURE_BASE', message: 'El draft avanzó desde que se generó esta propuesta.' }),
    })

    await expect(applyTexture('job-1')).rejects.toBeInstanceOf(ApiError)
    await expect(applyTexture('job-1')).rejects.toMatchObject({ code: 'STALE_TEXTURE_BASE', status: 409 })
  })
})
