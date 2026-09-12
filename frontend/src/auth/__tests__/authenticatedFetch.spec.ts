import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import * as authApi from '../authApi'
import { authenticatedFetch } from '../authenticatedFetch'
import { useSessionStore } from '../sessionStore'

vi.mock('../authApi', async () => {
  const actual = await vi.importActual<typeof authApi>('../authApi')
  return { ...actual, login: vi.fn(), refreshAccessToken: vi.fn() }
})

const user: authApi.RegisteredUser = {
  id: 'u1',
  email: 'ada@example.com',
  phone: null,
  nombre: 'Ada',
  apellidos: 'Lovelace',
  emailVerified: false,
  phoneVerified: false,
  hasPassword: true,
}

function okResponse(): Response {
  return new Response('{}', { status: 200 })
}

function unauthorizedResponse(): Response {
  return new Response('{}', { status: 401 })
}

async function loggedInStore() {
  vi.mocked(authApi.login).mockResolvedValue({
    user,
    tokens: { accessToken: 'a1', refreshToken: 'r1', tokenType: 'Bearer', expiresInSeconds: 900 },
  })
  const store = useSessionStore()
  await store.login('ada@example.com', 'abcd1234')
  return store
}

describe('authenticatedFetch', () => {
  beforeEach(() => {
    sessionStorage.clear()
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('adjunta Authorization: Bearer cuando hay sesión', async () => {
    await loggedInStore()
    const fetchMock = vi.fn<typeof fetch>(async () => okResponse())
    vi.stubGlobal('fetch', fetchMock)

    await authenticatedFetch('/api/projects')

    const [, init] = fetchMock.mock.calls[0]!
    expect((init?.headers as Record<string, string>).Authorization).toBe('Bearer a1')
  })

  it('sin sesión, no adjunta ningún header de Authorization', async () => {
    const fetchMock = vi.fn<typeof fetch>(async () => okResponse())
    vi.stubGlobal('fetch', fetchMock)

    await authenticatedFetch('/api/projects')

    const [, init] = fetchMock.mock.calls[0]!
    expect((init?.headers as Record<string, string> | undefined)?.Authorization).toBeUndefined()
  })

  it('ante un 401, refresca la sesión UNA vez y reintenta con el token nuevo', async () => {
    await loggedInStore()
    vi.mocked(authApi.refreshAccessToken).mockResolvedValue({ accessToken: 'a2', refreshToken: 'r1', tokenType: 'Bearer', expiresInSeconds: 900 })
    const fetchMock = vi.fn<typeof fetch>().mockResolvedValueOnce(unauthorizedResponse()).mockResolvedValueOnce(okResponse())
    vi.stubGlobal('fetch', fetchMock)

    const response = await authenticatedFetch('/api/projects')

    expect(response.status).toBe(200)
    expect(fetchMock).toHaveBeenCalledTimes(2)
    const [, secondInit] = fetchMock.mock.calls[1]!
    expect((secondInit?.headers as Record<string, string>).Authorization).toBe('Bearer a2')
  })

  it('si el refresh también falla, devuelve el 401 original sin reintentar', async () => {
    await loggedInStore()
    vi.mocked(authApi.refreshAccessToken).mockRejectedValue(new Error('invalid_token'))
    const fetchMock = vi.fn<typeof fetch>(async () => unauthorizedResponse())
    vi.stubGlobal('fetch', fetchMock)

    const response = await authenticatedFetch('/api/projects')

    expect(response.status).toBe(401)
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })

  it('un 401 sin sesión (nunca hubo refreshToken) no intenta refrescar', async () => {
    const fetchMock = vi.fn<typeof fetch>(async () => unauthorizedResponse())
    vi.stubGlobal('fetch', fetchMock)

    await authenticatedFetch('/api/projects')

    expect(authApi.refreshAccessToken).not.toHaveBeenCalled()
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })
})
