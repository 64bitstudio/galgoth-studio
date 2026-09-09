import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../../api/ApiError'
import { saveRevision } from '../draftPersistenceApi'
import type { MobProjectModel } from '../../domain/MobProjectModel'

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

function emptyModel(mobId: string): MobProjectModel {
  return {
    mobId,
    projectId: 'test-project',
    name: 'Test',
    baseType: 'humanoid',
    units: 'minecraft_pixels',
    bones: [],
    cuboids: [],
    texture: { width: 64, height: 64, storageKey: null },
    uv: { textureWidth: 64, textureHeight: 64, regions: [] },
    animations: [],
    exportSettings: { preferredFormatVersion: 'v5' },
    referenceImages: [],
  }
}

describe('draftPersistenceApi', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('saveRevision hace POST a /api/mobs/{mobId}/revisions con el modelo en el body', async () => {
    const fetchMock = vi.fn<typeof fetch>(async () => jsonResponse({ created: true, revisionNumber: 1, reason: null }, 201))
    vi.stubGlobal('fetch', fetchMock)

    const result = await saveRevision('mob-1', emptyModel('mob-1'))

    expect(result).toEqual({ created: true, revisionNumber: 1, reason: null })
    const [url, init] = fetchMock.mock.calls[0]!
    expect(String(url)).toContain('/api/mobs/mob-1/revisions')
    expect(init?.method).toBe('POST')
    expect(JSON.parse(init?.body as string)).toEqual({ model: emptyModel('mob-1') })
  })

  it('created:false (sin cambios) se resuelve igual, sin lanzar', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn<typeof fetch>(async () => jsonResponse({ created: false, revisionNumber: 3, reason: 'Sin cambios.' })),
    )

    const result = await saveRevision('mob-1', emptyModel('mob-1'))

    expect(result).toEqual({ created: false, revisionNumber: 3, reason: 'Sin cambios.' })
  })

  it('un modelo inválido (400) propaga un ApiError con los detalles del backend', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn<typeof fetch>(async () =>
        jsonResponse({ error: 'INVALID_DRAFT', message: 'El draft no pasa la validación.', details: ['texture requerido'] }, 400),
      ),
    )

    await expect(saveRevision('mob-1', emptyModel('mob-1'))).rejects.toMatchObject({
      message: 'El draft no pasa la validación.',
      status: 400,
      code: 'INVALID_DRAFT',
    })
    await expect(saveRevision('mob-1', emptyModel('mob-1'))).rejects.toBeInstanceOf(ApiError)
  })

  it('un mob inexistente (404) propaga un ApiError', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn<typeof fetch>(async () => jsonResponse({ error: 'MOB_NOT_FOUND', message: 'No existe ese mob.' }, 404)),
    )

    await expect(saveRevision('no-existe', emptyModel('no-existe'))).rejects.toMatchObject({ status: 404 })
  })
})
