import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../ApiError'
import { cancelGeneration, eventsUrl, startGeneration } from '../generationApi'

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

describe('generationApi', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('startGeneration hace POST a /api/mobs/{mobId}/generate y devuelve el jobId real', async () => {
    const fetchMock = vi.fn<typeof fetch>(async () => jsonResponse({ jobId: 'job-1' }, 202))
    vi.stubGlobal('fetch', fetchMock)

    const result = await startGeneration('mob-1')

    const [url, init] = fetchMock.mock.calls[0]!
    expect(String(url)).toContain('/api/mobs/mob-1/generate')
    expect(init?.method).toBe('POST')
    expect(result).toEqual({ jobId: 'job-1' })
  })

  it('startGeneration sobre una respuesta no exitosa lanza un ApiError con el código real', async () => {
    vi.stubGlobal('fetch', vi.fn<typeof fetch>(async () => jsonResponse({ error: 'NO_REFERENCE_IMAGE', message: 'Sin referencia.' }, 400)))

    await expect(startGeneration('mob-1')).rejects.toBeInstanceOf(ApiError)
    await expect(startGeneration('mob-1')).rejects.toMatchObject({ status: 400, code: 'NO_REFERENCE_IMAGE' })
  })

  it('cancelGeneration hace POST a /api/jobs/{jobId}/cancel', async () => {
    const fetchMock = vi.fn<typeof fetch>(async () => new Response(null, { status: 202 }))
    vi.stubGlobal('fetch', fetchMock)

    await cancelGeneration('job-1')

    const [url, init] = fetchMock.mock.calls[0]!
    expect(String(url)).toContain('/api/jobs/job-1/cancel')
    expect(init?.method).toBe('POST')
  })

  it('cancelGeneration sobre una respuesta no exitosa lanza un ApiError', async () => {
    vi.stubGlobal('fetch', vi.fn<typeof fetch>(async () => jsonResponse({ error: 'INVALID_JOB_STATE', message: 'Ya terminó.' }, 409)))

    await expect(cancelGeneration('job-1')).rejects.toBeInstanceOf(ApiError)
    await expect(cancelGeneration('job-1')).rejects.toMatchObject({ status: 409, code: 'INVALID_JOB_STATE' })
  })

  it('eventsUrl arma la URL del stream SSE sin hacer ningún fetch', () => {
    expect(eventsUrl('job-1')).toContain('/api/jobs/job-1/events')
  })
})
