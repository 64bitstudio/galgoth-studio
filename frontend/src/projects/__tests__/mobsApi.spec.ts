import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../projectsApi'
import { createMob, getMob, listMobs } from '../mobsApi'

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

describe('mobsApi', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('listMobs hace GET /api/projects/{id}/mobs y devuelve el array', async () => {
    const mobs = [{ id: 'm1', name: 'Carcomido', baseType: 'humanoid', status: 'draft', thumbnailKey: null, updatedAt: '' }]
    const fetchMock = vi.fn<typeof fetch>(async () => jsonResponse(mobs))
    vi.stubGlobal('fetch', fetchMock)

    const result = await listMobs('p1')

    expect(result).toEqual(mobs)
    expect(String(fetchMock.mock.calls[0]![0])).toContain('/api/projects/p1/mobs')
  })

  it('createMob hace POST con name y baseType en el body', async () => {
    const mob = { id: 'm1', name: 'Augur', baseType: 'flying', status: 'draft', thumbnailKey: null, updatedAt: '' }
    const fetchMock = vi.fn<typeof fetch>(async () => jsonResponse(mob, 201))
    vi.stubGlobal('fetch', fetchMock)

    const result = await createMob('p1', 'Augur', 'flying')

    expect(result).toEqual(mob)
    const [url, init] = fetchMock.mock.calls[0]!
    expect(String(url)).toContain('/api/projects/p1/mobs')
    expect(init?.method).toBe('POST')
    expect(init?.body).toBe(JSON.stringify({ name: 'Augur', baseType: 'flying' }))
  })

  it('getMob hace GET /api/mobs/{mobId}, ticket 034', async () => {
    const mob = { id: 'm1', name: 'Carcomido', baseType: 'humanoid', status: 'draft', thumbnailKey: null, updatedAt: '' }
    const fetchMock = vi.fn<typeof fetch>(async () => jsonResponse(mob))
    vi.stubGlobal('fetch', fetchMock)

    const result = await getMob('m1')

    expect(result).toEqual(mob)
    expect(String(fetchMock.mock.calls[0]![0])).toContain('/api/mobs/m1')
  })

  it('getMob sobre un mob inexistente lanza un ApiError con MOB_NOT_FOUND', async () => {
    vi.stubGlobal('fetch', vi.fn<typeof fetch>(async () => jsonResponse({ error: 'MOB_NOT_FOUND', message: 'No existe.' }, 404)))

    await expect(getMob('m1')).rejects.toMatchObject({ status: 404, code: 'MOB_NOT_FOUND' })
  })

  it('un error real propaga un ApiError con el mensaje y código del backend', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn<typeof fetch>(async () => jsonResponse({ error: 'INVALID_MOB_REQUEST', message: 'El nombre no puede estar vacío.' }, 400)),
    )

    await expect(createMob('p1', '', 'humanoid')).rejects.toMatchObject({
      message: 'El nombre no puede estar vacío.',
      status: 400,
      code: 'INVALID_MOB_REQUEST',
    })
    await expect(createMob('p1', '', 'humanoid')).rejects.toBeInstanceOf(ApiError)
  })
})
