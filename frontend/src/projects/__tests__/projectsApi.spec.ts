import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useSessionStore } from '../../auth/sessionStore'
import { ApiError, changeProjectVisibility, createProject, deleteProject, duplicateProject, listExploreProjects, listProjects, renameProject } from '../projectsApi'

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

describe('projectsApi', () => {
  // Ticket 089 -- request() ahora pasa por authenticatedFetch (ticket 078),
  // que necesita un Pinia activo para leer la sesión (sin sesión, simplemente
  // no adjunta Authorization -- ver authenticatedFetch.spec.ts).
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('listProjects hace GET /api/projects y devuelve el array', async () => {
    const summaries = [{ id: '1', name: 'Galgoth', mobCount: 0, mobThumbnails: [], createdAt: '', updatedAt: '' }]
    const fetchMock = vi.fn<typeof fetch>(async () => jsonResponse(summaries))
    vi.stubGlobal('fetch', fetchMock)

    const result = await listProjects()

    expect(result).toEqual(summaries)
    const [url, init] = fetchMock.mock.calls[0]!
    expect(String(url)).toContain('/api/projects')
    expect(init?.method).toBeUndefined() // GET por defecto
  })

  // Ticket 088 -- Explorar, HU-5.
  it('listExploreProjects hace GET /api/explore/projects y devuelve el array', async () => {
    const summaries = [{ id: '1', name: 'Galgoth', mobCount: 0, mobThumbnails: [], visibility: 'PUBLIC', ownerDisplayName: 'Ada Lovelace', createdAt: '', updatedAt: '' }]
    const fetchMock = vi.fn<typeof fetch>(async () => jsonResponse(summaries))
    vi.stubGlobal('fetch', fetchMock)

    const result = await listExploreProjects()

    expect(result).toEqual(summaries)
    const [url, init] = fetchMock.mock.calls[0]!
    expect(String(url)).toContain('/api/explore/projects')
    expect(init?.method).toBeUndefined() // GET por defecto
  })

  it('createProject hace POST con el nombre en el body, sin sesión ownerDisplayName es null', async () => {
    const detail = { id: '1', name: 'Nuevo', mobCount: 0, createdAt: '', updatedAt: '' }
    const fetchMock = vi.fn<typeof fetch>(async () => jsonResponse(detail, 201))
    vi.stubGlobal('fetch', fetchMock)

    const result = await createProject('Nuevo')

    expect(result).toEqual(detail)
    const [, init] = fetchMock.mock.calls[0]!
    expect(init?.method).toBe('POST')
    expect(init?.body).toBe(JSON.stringify({ name: 'Nuevo', ownerDisplayName: null }))
  })

  it('createProject manda el nombre completo de la sesión activa como ownerDisplayName', async () => {
    useSessionStore().user = {
      id: 'u1',
      email: 'ada@example.com',
      phone: null,
      nombre: 'Ada',
      apellidos: 'Lovelace',
      emailVerified: true,
      phoneVerified: false,
      hasPassword: true,
      country: null,
      username: null,
      createdAt: '2026-01-01T00:00:00Z',
    }
    const detail = { id: '1', name: 'Nuevo', mobCount: 0, createdAt: '', updatedAt: '' }
    const fetchMock = vi.fn<typeof fetch>(async () => jsonResponse(detail, 201))
    vi.stubGlobal('fetch', fetchMock)

    await createProject('Nuevo')

    const [, init] = fetchMock.mock.calls[0]!
    expect(init?.body).toBe(JSON.stringify({ name: 'Nuevo', ownerDisplayName: 'Ada Lovelace' }))
  })

  it('renameProject hace PATCH a /api/projects/{id}', async () => {
    const detail = { id: '1', name: 'Renombrado', mobCount: 0, createdAt: '', updatedAt: '' }
    const fetchMock = vi.fn<typeof fetch>(async () => jsonResponse(detail))
    vi.stubGlobal('fetch', fetchMock)

    const result = await renameProject('1', 'Renombrado', null)

    expect(result).toEqual(detail)
    const [url, init] = fetchMock.mock.calls[0]!
    expect(String(url)).toContain('/api/projects/1')
    expect(init?.method).toBe('PATCH')
  })

  // Ticket 087 -- toggle de visibilidad sobre el endpoint del ticket 086.
  it('changeProjectVisibility hace PATCH a /api/projects/{id}/visibility con el nuevo valor en el body', async () => {
    const detail = { id: '1', name: 'Galgoth', mobCount: 0, visibility: 'PUBLIC', createdAt: '', updatedAt: '' }
    const fetchMock = vi.fn<typeof fetch>(async () => jsonResponse(detail))
    vi.stubGlobal('fetch', fetchMock)

    const result = await changeProjectVisibility('1', 'PUBLIC')

    expect(result).toEqual(detail)
    const [url, init] = fetchMock.mock.calls[0]!
    expect(String(url)).toContain('/api/projects/1/visibility')
    expect(init?.method).toBe('PATCH')
    expect(init?.body).toBe(JSON.stringify({ visibility: 'PUBLIC' }))
  })

  // Ticket 073 -- `description` siempre explícita (nunca omitida) en el body del PATCH, para no borrarla accidentalmente.
  it('renameProject envía `description` siempre explícita en el body, incluso null', async () => {
    const fetchMock = vi.fn<typeof fetch>(async () => jsonResponse({ id: '1', name: 'Renombrado', description: 'Nueva descripción', mobCount: 0, createdAt: '', updatedAt: '' }))
    vi.stubGlobal('fetch', fetchMock)

    await renameProject('1', 'Renombrado', 'Nueva descripción')

    const [, init] = fetchMock.mock.calls[0]!
    expect(JSON.parse(init?.body as string)).toEqual({ name: 'Renombrado', description: 'Nueva descripción' })
  })

  it('deleteProject hace DELETE y no intenta parsear un body en 204', async () => {
    const fetchMock = vi.fn<typeof fetch>(async () => new Response(null, { status: 204 }))
    vi.stubGlobal('fetch', fetchMock)

    await expect(deleteProject('1')).resolves.toBeUndefined()
    const [, init] = fetchMock.mock.calls[0]!
    expect(init?.method).toBe('DELETE')
  })

  it('duplicateProject hace POST a /api/projects/{id}/duplicate', async () => {
    const detail = { id: '2', name: 'Original (copia)', mobCount: 1, createdAt: '', updatedAt: '' }
    const fetchMock = vi.fn<typeof fetch>(async () => jsonResponse(detail, 201))
    vi.stubGlobal('fetch', fetchMock)

    const result = await duplicateProject('1')

    expect(result).toEqual(detail)
    const [url, init] = fetchMock.mock.calls[0]!
    expect(String(url)).toContain('/api/projects/1/duplicate')
    expect(init?.method).toBe('POST')
  })

  it('una respuesta de error real propaga un ApiError con el mensaje y código del backend', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => jsonResponse({ error: 'INVALID_PROJECT_NAME', message: 'El nombre no puede estar vacío.' }, 400)),
    )

    await expect(createProject('')).rejects.toMatchObject({
      message: 'El nombre no puede estar vacío.',
      status: 400,
      code: 'INVALID_PROJECT_NAME',
    })
    await expect(createProject('')).rejects.toBeInstanceOf(ApiError)
  })

  it('un error sin body JSON parseable igual produce un ApiError legible', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => new Response('not json', { status: 500 })),
    )

    await expect(listProjects()).rejects.toMatchObject({ status: 500 })
  })
})
