import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../../api/ApiError'
import { downloadBbmodel, getExportStatus } from '../mobExportApi'

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

describe('mobExportApi', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('getExportStatus hace GET a /api/mobs/{mobId}/export/status y devuelve el estado real', async () => {
    const fetchMock = vi.fn<typeof fetch>(async () =>
      jsonResponse({
        mobId: 'mob-1',
        mobName: 'Carcomido',
        hasSavedRevision: true,
        hasUnsavedChanges: false,
        fmmCompatible: true,
        fmmIssues: [],
      }),
    )
    vi.stubGlobal('fetch', fetchMock)

    const status = await getExportStatus('mob-1')

    const [url] = fetchMock.mock.calls[0]!
    expect(String(url)).toContain('/api/mobs/mob-1/export/status')
    expect(status.hasSavedRevision).toBe(true)
    expect(status.hasUnsavedChanges).toBe(false)
  })

  it('getExportStatus sobre un mob inexistente lanza un ApiError con MOB_NOT_FOUND', async () => {
    vi.stubGlobal('fetch', vi.fn<typeof fetch>(async () => jsonResponse({ error: 'MOB_NOT_FOUND', message: 'No existe.' }, 404)))

    await expect(getExportStatus('mob-1')).rejects.toBeInstanceOf(ApiError)
    await expect(getExportStatus('mob-1')).rejects.toMatchObject({ status: 404, code: 'MOB_NOT_FOUND' })
  })

  it('downloadBbmodel hace GET a /api/mobs/{mobId}/export/bbmodel y devuelve el filename real del header Content-Disposition', async () => {
    const fetchMock = vi.fn<typeof fetch>(
      async () =>
        new Response(new Blob(['{"meta":{}}'], { type: 'application/octet-stream' }), {
          status: 200,
          headers: { 'Content-Disposition': 'attachment; filename="Carcomido.bbmodel"' },
        }),
    )
    vi.stubGlobal('fetch', fetchMock)

    const result = await downloadBbmodel('mob-1')

    const [url] = fetchMock.mock.calls[0]!
    expect(String(url)).toContain('/api/mobs/mob-1/export/bbmodel')
    expect(result.filename).toBe('Carcomido.bbmodel')
    expect(result.blob).toBeInstanceOf(Blob)
  })

  it('downloadBbmodel sobre un mob sin ninguna revisión guardada lanza un ApiError con NO_SAVED_REVISION', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn<typeof fetch>(async () => jsonResponse({ error: 'NO_SAVED_REVISION', message: 'Nada que exportar.' }, 404)),
    )

    await expect(downloadBbmodel('mob-1')).rejects.toBeInstanceOf(ApiError)
    await expect(downloadBbmodel('mob-1')).rejects.toMatchObject({ status: 404, code: 'NO_SAVED_REVISION' })
  })
})
