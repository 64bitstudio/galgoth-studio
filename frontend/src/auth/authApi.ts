/**
 * Cliente HTTP de la API DIRECTA de auth-core-mc (ticket 078,
 * `PROP-GS-AUTH-01` Fig. 02/03/06) — a diferencia de `src/api/*.ts`, que
 * llaman al backend PROPIO de galgoth-studio, todo lo de este archivo
 * llama a un origen distinto (`authCoreMcUrl()`) con el header
 * `X-Client-Id` que auth-core-mc exige para resolver el tenant.
 */
import { ApiError } from '../api/ApiError'
import { AUTH_CLIENT_ID, authCoreMcUrl } from './authCoreMcConfig'

export interface RegisteredUser {
  id: string
  email: string | null
  phone: string | null
  nombre: string
  apellidos: string
  emailVerified: boolean
  phoneVerified: boolean
  hasPassword: boolean
}

export interface TokenPair {
  accessToken: string
  refreshToken: string
  tokenType: string
  expiresInSeconds: number
}

export interface LoginSuccess {
  user: RegisteredUser
  tokens: TokenPair
}

/** auth-core-mc#045: el usuario tiene 2FA activo — ticket 078 explícitamente no maneja este caso (PROP-GS-AUTH-01 sección 08). */
export interface TwoFactorRequired {
  twoFactorRequired: true
  pendingToken: string
  method: string
}

export interface RegisterRequest {
  email?: string
  phone?: string
  nombre: string
  apellidos: string
  password: string
}

export function register(request: RegisterRequest): Promise<RegisteredUser> {
  return requestJson<RegisteredUser>('/api/v1/register', request)
}

export function login(identifier: string, password: string): Promise<LoginSuccess | TwoFactorRequired> {
  return requestJson<LoginSuccess | TwoFactorRequired>('/api/v1/login', { identifier, password })
}

/** `/api/v1/token/refresh` no exige `X-Client-Id` -- el refresh token ya identifica de qué cliente/usuario es (auth-core-mc, TokenController). */
export function refreshAccessToken(refreshToken: string): Promise<TokenPair> {
  return requestJson<TokenPair>('/api/v1/token/refresh', { refreshToken }, false)
}

async function requestJson<T>(path: string, body: unknown, withClientId = true): Promise<T> {
  const headers: Record<string, string> = { 'Content-Type': 'application/json' }
  if (withClientId) {
    headers['X-Client-Id'] = AUTH_CLIENT_ID
  }

  const response = await fetch(`${authCoreMcUrl()}${path}`, {
    method: 'POST',
    headers,
    body: JSON.stringify(body),
  })

  const responseBody = await response.json().catch(() => null)
  if (!response.ok) {
    throw new ApiError(responseBody?.message ?? `Error HTTP ${response.status}`, response.status, responseBody?.error)
  }
  return responseBody as T
}

export function isTwoFactorRequired(result: LoginSuccess | TwoFactorRequired): result is TwoFactorRequired {
  return 'twoFactorRequired' in result
}
