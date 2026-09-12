import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../../api/ApiError'
import { isTwoFactorRequired, login, refreshAccessToken, register } from '../authApi'

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

describe('authApi', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('register hace POST a /api/v1/register con X-Client-Id: galgoth-studio', async () => {
    const user = { id: 'u1', email: 'ada@example.com', phone: null, nombre: 'Ada', apellidos: 'Lovelace', emailVerified: false, phoneVerified: false, hasPassword: true }
    const fetchMock = vi.fn<typeof fetch>(async () => jsonResponse(user, 201))
    vi.stubGlobal('fetch', fetchMock)

    const result = await register({ email: 'ada@example.com', nombre: 'Ada', apellidos: 'Lovelace', password: 'abcd1234' })

    expect(result).toEqual(user)
    const [url, init] = fetchMock.mock.calls[0]!
    expect(String(url)).toContain('/api/v1/register')
    expect((init?.headers as Record<string, string>)['X-Client-Id']).toBe('galgoth-studio')
  })

  it('un registro rechazado propaga un ApiError con el código real', async () => {
    vi.stubGlobal('fetch', vi.fn<typeof fetch>(async () => jsonResponse({ error: 'weak_password', message: 'Password muy débil' }, 400)))

    await expect(register({ email: 'a@example.com', nombre: 'A', apellidos: 'B', password: '123' })).rejects.toMatchObject({
      status: 400,
      code: 'weak_password',
    })
  })

  it('login hace POST a /api/v1/login y devuelve tokens+user en éxito', async () => {
    const success = {
      user: { id: 'u1', email: 'ada@example.com', phone: null, nombre: 'Ada', apellidos: 'Lovelace', emailVerified: false, phoneVerified: false, hasPassword: true },
      tokens: { accessToken: 'a.b.c', refreshToken: 'r1', tokenType: 'Bearer', expiresInSeconds: 900 },
    }
    vi.stubGlobal('fetch', vi.fn<typeof fetch>(async () => jsonResponse(success)))

    const result = await login('ada@example.com', 'abcd1234')

    expect(result).toEqual(success)
    expect(isTwoFactorRequired(result)).toBe(false)
  })

  it('login con 2FA activo devuelve twoFactorRequired en vez de tokens', async () => {
    const pending = { twoFactorRequired: true, pendingToken: 'p1', method: 'OTP_EMAIL' }
    vi.stubGlobal('fetch', vi.fn<typeof fetch>(async () => jsonResponse(pending, 202)))

    const result = await login('ada@example.com', 'abcd1234')

    expect(isTwoFactorRequired(result)).toBe(true)
  })

  it('credenciales inválidas propagan un ApiError', async () => {
    vi.stubGlobal('fetch', vi.fn<typeof fetch>(async () => jsonResponse({ error: 'invalid_credentials', message: 'Credenciales inválidas' }, 401)))

    await expect(login('ada@example.com', 'wrong')).rejects.toBeInstanceOf(ApiError)
  })

  it('refreshAccessToken hace POST a /api/v1/token/refresh SIN X-Client-Id', async () => {
    const tokens = { accessToken: 'a2', refreshToken: 'r1', tokenType: 'Bearer', expiresInSeconds: 900 }
    const fetchMock = vi.fn<typeof fetch>(async () => jsonResponse(tokens))
    vi.stubGlobal('fetch', fetchMock)

    const result = await refreshAccessToken('r1')

    expect(result).toEqual(tokens)
    const [url, init] = fetchMock.mock.calls[0]!
    expect(String(url)).toContain('/api/v1/token/refresh')
    expect((init?.headers as Record<string, string>)['X-Client-Id']).toBeUndefined()
  })

  it('un refresh token vencido/inválido propaga un ApiError', async () => {
    vi.stubGlobal('fetch', vi.fn<typeof fetch>(async () => jsonResponse({ error: 'invalid_token', message: 'Refresh token inválido' }, 401)))

    await expect(refreshAccessToken('expired')).rejects.toBeInstanceOf(ApiError)
  })
})
