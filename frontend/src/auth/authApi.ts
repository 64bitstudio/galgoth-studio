/**
 * Cliente HTTP de la API DIRECTA de auth-core-mc (ticket 078,
 * `PROP-GS-AUTH-01` Fig. 02/03/06) — a diferencia de `src/api/*.ts`, que
 * llaman al backend PROPIO de galgoth-studio, este archivo completo
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
  /** Ticket 060 de auth-core-mc -- `null` si no están configurados. Editables desde la pantalla Usuario (ticket 093). */
  country: string | null
  username: string | null
  /** Ticket 065 de auth-core-mc -- "Miembro desde" en la pantalla Usuario. */
  createdAt: string
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

/**
 * Ticket 080: siempre `202`, exista o no `identifier` como cuenta real --
 * auth-core-mc nunca revela esa diferencia aquí (mismo criterio que
 * `/verify-email/request` no sigue: ahí el llamador ya "posee" un
 * userId). No hay nada que inspeccionar en la respuesta.
 */
export function requestPasswordReset(identifier: string): Promise<void> {
  return requestJson<void>('/api/v1/password-reset/request', { identifier })
}

/** `/api/v1/password-reset/confirm` no exige `X-Client-Id` -- el token ya identifica de qué usuario es (mismo criterio que `token/refresh`). */
export function confirmPasswordReset(token: string, newPassword: string): Promise<void> {
  return requestJson<void>('/api/v1/password-reset/confirm', { token, newPassword }, false)
}

/**
 * Ticket 093 -- "Cambiar correo" en la pantalla Usuario. A diferencia de
 * los demás endpoints de cuenta (`accountApi.ts`, Bearer real), este
 * usa el mismo mecanismo "confía en el `userId` que manda el caller" que
 * `/2fa`/`/change-email` en general (ver docs/API.md de auth-core-mc):
 * adivinar el `userId` de otra persona solo alcanza a mandarle un correo
 * de confirmación a SU bandeja real, no a completar el cambio -- exige
 * abrir ese link. Siempre `202`, nunca revela si `newEmail` ya está en
 * uso (auth-core-mc lo valida al confirmar, no aquí).
 */
export function requestEmailChange(userId: string, newEmail: string): Promise<void> {
  return requestJson<void>('/api/v1/change-email/request', { userId, newEmail })
}

/** `/api/v1/change-email/confirm` no exige `X-Client-Id` -- el token ya identifica de qué usuario es (mismo criterio que `token/refresh`/`password-reset/confirm`). */
export function confirmEmailChange(token: string): Promise<void> {
  return requestJson<void>('/api/v1/change-email/confirm', { token }, false)
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
