import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../../api/ApiError'
import { uploadThumbnail } from '../thumbnailApi'

describe('thumbnailApi', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('uploadThumbnail hace POST a /api/mobs/{mobId}/thumbnail con Content-Type image/png y el blob como body', async () => {
    const fetchMock = vi.fn<typeof fetch>(async () => new Response(null, { status: 204 }))
    vi.stubGlobal('fetch', fetchMock)
    const png = new Blob(['fake-png-bytes'], { type: 'image/png' })

    await uploadThumbnail('mob-1', png)

    const [url, init] = fetchMock.mock.calls[0]!
    expect(String(url)).toContain('/api/mobs/mob-1/thumbnail')
    expect(init?.method).toBe('POST')
    expect((init?.headers as Record<string, string>)['Content-Type']).toBe('image/png')
    expect(init?.body).toBe(png)
  })

  it('una respuesta no exitosa lanza un ApiError', async () => {
    vi.stubGlobal('fetch', vi.fn<typeof fetch>(async () => new Response(null, { status: 500 })))

    await expect(uploadThumbnail('mob-1', new Blob())).rejects.toBeInstanceOf(ApiError)
    await expect(uploadThumbnail('mob-1', new Blob())).rejects.toMatchObject({ status: 500 })
  })
})
