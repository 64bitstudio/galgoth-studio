import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useSessionStore } from '../sessionStore'
import * as accountApi from '../accountApi'

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

const user = {
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

describe('accountApi', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    useSessionStore().accessToken = 'token'
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('getProfile hace GET /api/v1/account/profile con Bearer', async () => {
    const fetchMock = vi.fn<typeof fetch>(async () => jsonResponse(user))
    vi.stubGlobal('fetch', fetchMock)

    const result = await accountApi.getProfile()

    expect(result).toEqual(user)
    const [url, init] = fetchMock.mock.calls[0]!
    expect(String(url)).toContain('/api/v1/account/profile')
    expect((init?.headers as Record<string, string>).Authorization).toBe('Bearer token')
  })

  it('updateProfile hace PATCH con nombre/apellidos/country/username en el body', async () => {
    const fetchMock = vi.fn<typeof fetch>(async () => jsonResponse(user))
    vi.stubGlobal('fetch', fetchMock)

    await accountApi.updateProfile('Ada', 'Lovelace', 'MX', 'ada')

    const [, init] = fetchMock.mock.calls[0]!
    expect(init?.method).toBe('PATCH')
    expect(JSON.parse(init?.body as string)).toEqual({ nombre: 'Ada', apellidos: 'Lovelace', country: 'MX', username: 'ada' })
  })

  it('changePassword hace PATCH a /account/password con currentPassword+newPassword', async () => {
    const fetchMock = vi.fn<typeof fetch>(async () => jsonResponse(user))
    vi.stubGlobal('fetch', fetchMock)

    await accountApi.changePassword('old1234', 'new12345')

    const [url, init] = fetchMock.mock.calls[0]!
    expect(String(url)).toContain('/api/v1/account/password')
    expect(init?.method).toBe('PATCH')
    expect(JSON.parse(init?.body as string)).toEqual({ currentPassword: 'old1234', newPassword: 'new12345' })
  })

  it('setPassword hace POST a /account/password con solo newPassword', async () => {
    const fetchMock = vi.fn<typeof fetch>(async () => jsonResponse(user))
    vi.stubGlobal('fetch', fetchMock)

    await accountApi.setPassword('new12345')

    const [, init] = fetchMock.mock.calls[0]!
    expect(init?.method).toBe('POST')
    expect(JSON.parse(init?.body as string)).toEqual({ newPassword: 'new12345' })
  })

  it('listSessions manda X-Current-Refresh-Token con el refreshToken de la sesión activa', async () => {
    useSessionStore().refreshToken = 'r1'
    const fetchMock = vi.fn<typeof fetch>(async () => jsonResponse([]))
    vi.stubGlobal('fetch', fetchMock)

    await accountApi.listSessions()

    const [url, init] = fetchMock.mock.calls[0]!
    expect(String(url)).toContain('/api/v1/account/sessions')
    expect((init?.headers as Record<string, string>)['X-Current-Refresh-Token']).toBe('r1')
  })

  it('revokeSession hace DELETE a /account/sessions/{id}', async () => {
    const fetchMock = vi.fn<typeof fetch>(async () => new Response(null, { status: 204 }))
    vi.stubGlobal('fetch', fetchMock)

    await accountApi.revokeSession('s1')

    const [url, init] = fetchMock.mock.calls[0]!
    expect(String(url)).toContain('/api/v1/account/sessions/s1')
    expect(init?.method).toBe('DELETE')
  })

  // Ticket 093 -- "Cerrar sesión en todos los dispositivos" revoca TODAS, incluida la propia (sin X-Current-Refresh-Token a propósito).
  it('revokeAllSessions hace POST a revoke-others SIN X-Current-Refresh-Token', async () => {
    useSessionStore().refreshToken = 'r1'
    const fetchMock = vi.fn<typeof fetch>(async () => new Response(null, { status: 204 }))
    vi.stubGlobal('fetch', fetchMock)

    await accountApi.revokeAllSessions()

    const [url, init] = fetchMock.mock.calls[0]!
    expect(String(url)).toContain('/api/v1/account/sessions/revoke-others')
    expect(init?.method).toBe('POST')
    expect((init?.headers as Record<string, string> | undefined)?.['X-Current-Refresh-Token']).toBeUndefined()
  })

  it('listConnectedProviders hace GET /account/connected-providers', async () => {
    const providers = [{ provider: 'GOOGLE', linked: true }]
    const fetchMock = vi.fn<typeof fetch>(async () => jsonResponse(providers))
    vi.stubGlobal('fetch', fetchMock)

    const result = await accountApi.listConnectedProviders()

    expect(result).toEqual(providers)
  })

  it('linkProvider hace POST a /account/link-provider/{provider} y devuelve redirectUrl', async () => {
    const fetchMock = vi.fn<typeof fetch>(async () => jsonResponse({ redirectUrl: '/oauth2/authorization/x' }))
    vi.stubGlobal('fetch', fetchMock)

    const result = await accountApi.linkProvider('google')

    expect(result).toBe('/oauth2/authorization/x')
    const [url, init] = fetchMock.mock.calls[0]!
    expect(String(url)).toContain('/api/v1/account/link-provider/google')
    expect(init?.method).toBe('POST')
    // Hallazgo real (ticket 093): sin esto el navegador descarta la cookie de sesión que correlaciona el vínculo -- ver docstring de `linkProvider`.
    expect(init?.credentials).toBe('include')
  })

  it('unlinkProvider hace DELETE a /account/connected-providers/{provider} en minúscula', async () => {
    const fetchMock = vi.fn<typeof fetch>(async () => new Response(null, { status: 204 }))
    vi.stubGlobal('fetch', fetchMock)

    await accountApi.unlinkProvider('GOOGLE')

    const [url, init] = fetchMock.mock.calls[0]!
    expect(String(url)).toContain('/api/v1/account/connected-providers/google')
    expect(init?.method).toBe('DELETE')
  })

  it('unlinkProvider propaga el 409 de "único método de acceso" como ApiError', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn<typeof fetch>(async () =>
        jsonResponse({ error: 'cannot_unlink_last_login_method', message: 'Cannot unlink your only way to sign in' }, 409),
      ),
    )

    await expect(accountApi.unlinkProvider('google')).rejects.toMatchObject({
      status: 409,
      code: 'cannot_unlink_last_login_method',
    })
  })

  it('deleteAccount hace DELETE a /api/v1/account con confirmIdentifier', async () => {
    const fetchMock = vi.fn<typeof fetch>(async () => new Response(null, { status: 204 }))
    vi.stubGlobal('fetch', fetchMock)

    await accountApi.deleteAccount('ada@example.com')

    const [url, init] = fetchMock.mock.calls[0]!
    expect(String(url)).toContain('/api/v1/account')
    expect(init?.method).toBe('DELETE')
    expect(JSON.parse(init?.body as string)).toEqual({ confirmIdentifier: 'ada@example.com' })
  })

  it('un error real propaga un ApiError con el código del backend', async () => {
    vi.stubGlobal('fetch', vi.fn<typeof fetch>(async () => jsonResponse({ error: 'confirmation_mismatch', message: 'No coincide' }, 400)))

    await expect(accountApi.deleteAccount('wrong')).rejects.toMatchObject({ status: 400, code: 'confirmation_mismatch' })
  })
})
