import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../ApiError'
import { applyGeneration, getGenerationResult } from '../generationResultApi'

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

describe('generationResultApi', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('getGenerationResult hace GET a /api/jobs/{jobId}/result y devuelve el resultado real', async () => {
    const fetchMock = vi.fn<typeof fetch>(async () =>
      jsonResponse({
        jobId: 'job-1',
        mobId: 'mob-1',
        mobName: 'Carcomido',
        cuboidCount: 11,
        boneCount: 6,
        textureWidth: 128,
        textureHeight: 128,
        fmmCompatible: true,
        fmmIssues: [],
      }),
    )
    vi.stubGlobal('fetch', fetchMock)

    const result = await getGenerationResult('job-1')

    const [url, init] = fetchMock.mock.calls[0]!
    expect(String(url)).toContain('/api/jobs/job-1/result')
    expect(init?.method ?? 'GET').toBe('GET')
    expect(result.cuboidCount).toBe(11)
    expect(result.fmmCompatible).toBe(true)
  })

  it('getGenerationResult sobre un job no completado lanza un ApiError con el código real', async () => {
    vi.stubGlobal('fetch', vi.fn<typeof fetch>(async () => jsonResponse({ error: 'JOB_NOT_COMPLETED', message: 'Sin resultado.' }, 409)))

    await expect(getGenerationResult('job-1')).rejects.toBeInstanceOf(ApiError)
    await expect(getGenerationResult('job-1')).rejects.toMatchObject({ status: 409, code: 'JOB_NOT_COMPLETED' })
  })

  it('applyGeneration hace POST a /api/jobs/{jobId}/apply y devuelve revisionNumber/draftVersion', async () => {
    const fetchMock = vi.fn<typeof fetch>(async () => jsonResponse({ revisionNumber: 1, draftVersion: 1 }, 201))
    vi.stubGlobal('fetch', fetchMock)

    const result = await applyGeneration('job-1')

    const [url, init] = fetchMock.mock.calls[0]!
    expect(String(url)).toContain('/api/jobs/job-1/apply')
    expect(init?.method).toBe('POST')
    expect(result).toEqual({ revisionNumber: 1, draftVersion: 1 })
  })

  it('applyGeneration sobre una respuesta no exitosa lanza un ApiError', async () => {
    vi.stubGlobal('fetch', vi.fn<typeof fetch>(async () => jsonResponse({ error: 'INVALID_DRAFT', message: 'Modelo inválido.' }, 400)))

    await expect(applyGeneration('job-1')).rejects.toBeInstanceOf(ApiError)
    await expect(applyGeneration('job-1')).rejects.toMatchObject({ status: 400, code: 'INVALID_DRAFT' })
  })
})
