import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../../api/ApiError'
import {
  confirmPasswordReset,
  exchangeSocialCode,
  isTwoFactorRequired,
  login,
  refreshAccessToken,
  register,
  requestPasswordReset,
  socialLoginUrl,
} from '../authApi'

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

  it('requestPasswordReset (ticket 080) hace POST a /api/v1/password-reset/request con X-Client-Id', async () => {
    const fetchMock = vi.fn<typeof fetch>(async () => jsonResponse(null, 202))
    vi.stubGlobal('fetch', fetchMock)

    await requestPasswordReset('ada@example.com')

    const [url, init] = fetchMock.mock.calls[0]!
    expect(String(url)).toContain('/api/v1/password-reset/request')
    expect((init?.headers as Record<string, string>)['X-Client-Id']).toBe('galgoth-studio')
    expect(JSON.parse(init?.body as string)).toEqual({ identifier: 'ada@example.com' })
  })

  it('confirmPasswordReset (ticket 080) hace POST a /api/v1/password-reset/confirm SIN X-Client-Id', async () => {
    const fetchMock = vi.fn<typeof fetch>(async () => jsonResponse(null, 200))
    vi.stubGlobal('fetch', fetchMock)

    await confirmPasswordReset('the-token', 'newpass123')

    const [url, init] = fetchMock.mock.calls[0]!
    expect(String(url)).toContain('/api/v1/password-reset/confirm')
    expect((init?.headers as Record<string, string>)['X-Client-Id']).toBeUndefined()
    expect(JSON.parse(init?.body as string)).toEqual({ token: 'the-token', newPassword: 'newpass123' })
  })

  it('un token de reset inválido/expirado propaga un ApiError con el código real', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn<typeof fetch>(async () => jsonResponse({ error: 'invalid_token', message: 'Password reset link is invalid or has expired' }, 400)),
    )

    await expect(confirmPasswordReset('bad-token', 'newpass123')).rejects.toMatchObject({ status: 400, code: 'invalid_token' })
  })

  // Ticket 072 de auth-core-mc -- login social real.
  it('socialLoginUrl hace GET a /api/v1/oauth2/login-url/{provider} con X-Client-Id y devuelve redirectUrl', async () => {
    const fetchMock = vi.fn<typeof fetch>(async () => jsonResponse({ redirectUrl: 'https://auth-dev.example.com/oauth2/authorization/x::google' }))
    vi.stubGlobal('fetch', fetchMock)

    const result = await socialLoginUrl('google')

    expect(result).toBe('https://auth-dev.example.com/oauth2/authorization/x::google')
    const [url, init] = fetchMock.mock.calls[0]!
    expect(String(url)).toContain('/api/v1/oauth2/login-url/google')
    expect((init?.headers as Record<string, string>)['X-Client-Id']).toBe('galgoth-studio')
  })

  it('socialLoginUrl propaga un ApiError si el proveedor no está soportado', async () => {
    vi.stubGlobal('fetch', vi.fn<typeof fetch>(async () => jsonResponse({ error: 'unsupported_provider', message: 'Unknown provider' }, 400)))

    await expect(socialLoginUrl('google')).rejects.toMatchObject({ status: 400, code: 'unsupported_provider' })
  })

  it('exchangeSocialCode hace POST a /api/v1/oauth2/social-exchange con X-Client-Id y devuelve tokens+user en éxito', async () => {
    const success = {
      user: { id: 'u1', email: 'ada@example.com', phone: null, nombre: 'Ada', apellidos: 'Lovelace', emailVerified: true, phoneVerified: false, hasPassword: false },
      tokens: { accessToken: 'a.b.c', refreshToken: 'r1', tokenType: 'Bearer', expiresInSeconds: 900 },
    }
    const fetchMock = vi.fn<typeof fetch>(async () => jsonResponse(success))
    vi.stubGlobal('fetch', fetchMock)

    const result = await exchangeSocialCode('code-abc')

    expect(result).toEqual(success)
    expect(isTwoFactorRequired(result)).toBe(false)
    const [url, init] = fetchMock.mock.calls[0]!
    expect(String(url)).toContain('/api/v1/oauth2/social-exchange')
    expect((init?.headers as Record<string, string>)['X-Client-Id']).toBe('galgoth-studio')
    expect(JSON.parse(init?.body as string)).toEqual({ code: 'code-abc' })
  })

  it('exchangeSocialCode con 2FA activo devuelve twoFactorRequired en vez de tokens', async () => {
    vi.stubGlobal('fetch', vi.fn<typeof fetch>(async () => jsonResponse({ twoFactorRequired: true, pendingToken: 'p1', method: 'TOTP' }, 202)))

    const result = await exchangeSocialCode('code-abc')

    expect(isTwoFactorRequired(result)).toBe(true)
  })

  it('un código de un solo uso inválido/expirado propaga un ApiError', async () => {
    vi.stubGlobal('fetch', vi.fn<typeof fetch>(async () => jsonResponse({ error: 'invalid_token', message: 'The exchange code is invalid, expired, or already used' }, 400)))

    await expect(exchangeSocialCode('bad-code')).rejects.toMatchObject({ status: 400, code: 'invalid_token' })
  })
})
