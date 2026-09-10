import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../ApiError'
import { listReferenceImages, referenceImageUrl, uploadReferenceImage } from '../referenceImagesApi'

function jsonResponse(body: unknown, status = 201): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

describe('referenceImagesApi', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('uploadReferenceImage hace POST a /api/mobs/{mobId}/references con el Content-Type real del archivo', async () => {
    const summary = { id: 'r1', url: '/api/mobs/m1/references/r1', width: 100, height: 100, contentType: 'image/png', createdAt: '' }
    const fetchMock = vi.fn<typeof fetch>(async () => jsonResponse(summary))
    vi.stubGlobal('fetch', fetchMock)
    const file = new Blob(['fake-png'], { type: 'image/png' })

    const result = await uploadReferenceImage('m1', file)

    expect(result).toEqual(summary)
    const [url, init] = fetchMock.mock.calls[0]!
    expect(String(url)).toContain('/api/mobs/m1/references')
    expect(init?.method).toBe('POST')
    expect((init?.headers as Record<string, string>)['Content-Type']).toBe('image/png')
    expect(init?.body).toBe(file)
  })

  it('un formato/tamaño rechazado por el backend propaga un ApiError', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn<typeof fetch>(async () =>
        jsonResponse({ error: 'INVALID_REFERENCE_IMAGE', message: 'Formato no soportado.' }, 400),
      ),
    )

    await expect(uploadReferenceImage('m1', new Blob())).rejects.toBeInstanceOf(ApiError)
    await expect(uploadReferenceImage('m1', new Blob())).rejects.toMatchObject({ status: 400, code: 'INVALID_REFERENCE_IMAGE' })
  })

  it('listReferenceImages hace GET a /api/mobs/{mobId}/references y devuelve la lista', async () => {
    const summaries = [{ id: 'r1', url: '/api/mobs/m1/references/r1', width: 100, height: 100, contentType: 'image/png', createdAt: '' }]
    const fetchMock = vi.fn<typeof fetch>(async () => jsonResponse(summaries, 200))
    vi.stubGlobal('fetch', fetchMock)

    const result = await listReferenceImages('m1')

    expect(result).toEqual(summaries)
    expect(String(fetchMock.mock.calls[0]![0])).toContain('/api/mobs/m1/references')
  })

  it('listReferenceImages propaga un ApiError si el backend responde error', async () => {
    vi.stubGlobal('fetch', vi.fn<typeof fetch>(async () => jsonResponse({ error: 'MOB_NOT_FOUND', message: 'no existe' }, 404)))

    await expect(listReferenceImages('missing')).rejects.toBeInstanceOf(ApiError)
  })

  it('referenceImageUrl antepone API_BASE_URL a la ruta relativa', () => {
    expect(referenceImageUrl('/api/mobs/m1/references/r1')).toContain('/api/mobs/m1/references/r1')
  })
})
