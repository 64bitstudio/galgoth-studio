/**
 * Cliente HTTP de la API de CUENTA de auth-core-mc (ticket 093, pantalla
 * "Usuario") -- a diferencia de `authApi.ts` (login/registro/reset,
 * siempre sin sesión), cada función de este archivo exige un `accessToken`
 * real y pasa por `authenticatedFetch` (adjunta el Bearer, reintenta una
 * vez ante un 401 con refresh). Mismo origen distinto que `authApi.ts`
 * (`authCoreMcUrl()`), nunca el backend propio de galgoth-studio.
 */
import { authenticatedFetch } from './authenticatedFetch'
import { authCoreMcUrl } from './authCoreMcConfig'
import { useSessionStore } from './sessionStore'
import { ApiError } from '../api/ApiError'
import type { RegisteredUser } from './authApi'

export interface SessionSummary {
  id: string
  browser: string
  os: string
  createdAt: string
  lastUsedAt: string
  /** `true` solo si el caller mandó `X-Current-Refresh-Token` y coincide con esta fila -- ver `listSessions`. */
  current: boolean
}

/** Ticket 063 de auth-core-mc -- `provider` es el nombre del enum (`"GOOGLE"`/`"FACEBOOK"`). */
export interface ConnectedProviderSummary {
  provider: string
  linked: boolean
}

interface ApiErrorBody {
  error?: string
  message?: string
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const response = await authenticatedFetch(`${authCoreMcUrl()}${path}`, {
    ...init,
    headers: { 'Content-Type': 'application/json', ...init.headers },
  })

  if (!response.ok) {
    const body: ApiErrorBody | null = await response.json().catch(() => null)
    throw new ApiError(body?.message ?? `Error HTTP ${response.status}`, response.status, body?.error)
  }
  if (response.status === 204) {
    return undefined as T
  }
  return (await response.json()) as T
}

export function getProfile(): Promise<RegisteredUser> {
  return request<RegisteredUser>('/api/v1/account/profile')
}

/** `country`/`username` opcionales -- `null`/vacío los borra (mismo criterio que el backend). */
export function updateProfile(
  nombre: string,
  apellidos: string,
  country: string | null,
  username: string | null,
): Promise<RegisteredUser> {
  return request<RegisteredUser>('/api/v1/account/profile', {
    method: 'PATCH',
    body: JSON.stringify({ nombre, apellidos, country, username }),
  })
}

/** Cuenta CON contraseña ya establecida -- confirma la actual (ticket 061). Para una cuenta social-only sin contraseña, ver `setPassword`. */
export function changePassword(currentPassword: string, newPassword: string): Promise<RegisteredUser> {
  return request<RegisteredUser>('/api/v1/account/password', {
    method: 'PATCH',
    body: JSON.stringify({ currentPassword, newPassword }),
  })
}

/** Primera contraseña de una cuenta social-only (nunca tuvo una) -- ticket 041 de auth-core-mc, HU-5. */
export function setPassword(newPassword: string): Promise<RegisteredUser> {
  return request<RegisteredUser>('/api/v1/account/password', {
    method: 'POST',
    body: JSON.stringify({ newPassword }),
  })
}

/**
 * Ticket 062 -- el header `X-Current-Refresh-Token` es lo único que
 * permite marcar cuál fila es "Actual" (el JWT de acceso no lleva
 * ninguna referencia a qué refresh token lo emitió) -- se manda el
 * propio `refreshToken` guardado en la sesión activa.
 */
export function listSessions(): Promise<SessionSummary[]> {
  const refreshToken = useSessionStore().refreshToken
  return request<SessionSummary[]>('/api/v1/account/sessions', {
    headers: refreshToken ? { 'X-Current-Refresh-Token': refreshToken } : {},
  })
}

export function revokeSession(sessionId: string): Promise<void> {
  return request<void>(`/api/v1/account/sessions/${sessionId}`, { method: 'DELETE' })
}

/**
 * "Cerrar sesión en todos los dispositivos" -- a propósito SIN
 * `X-Current-Refresh-Token`: el backend revoca todas las sesiones,
 * incluida la actual (ver docstring de `AccountSessionsController`),
 * que es lo que "todos los dispositivos" debe significar acá. El
 * caller (`UserView.vue`) hace `sessionStore.logout()` + navega a
 * `/login` justo después -- esta sesión también queda cerrada.
 */
export function revokeAllSessions(): Promise<void> {
  return request<void>('/api/v1/account/sessions/revoke-others', { method: 'POST' })
}

export function listConnectedProviders(): Promise<ConnectedProviderSummary[]> {
  return request<ConnectedProviderSummary[]>('/api/v1/account/connected-providers')
}

/**
 * Ticket 063 -- devuelve la URL a la que el navegador COMPLETO debe
 * navegar (`window.location.href = ...`, nunca una llamada XHR más) --
 * el mismo mecanismo `/oauth2/authorization/{registrationId}` que ya usa
 * el login social. El caller vuelve a esta misma pantalla con
 * `?linked={provider}` o `?link_error={...}` en la query.
 */
export async function linkProvider(provider: 'google' | 'facebook'): Promise<string> {
  const result = await request<{ redirectUrl: string }>(`/api/v1/account/link-provider/${provider}`, { method: 'POST' })
  return result.redirectUrl
}

/** Ticket 064 -- irreversible. `confirmIdentifier` es el propio email/teléfono del usuario, reenviado tal cual. */
export function deleteAccount(confirmIdentifier: string): Promise<void> {
  return request<void>('/api/v1/account', {
    method: 'DELETE',
    body: JSON.stringify({ confirmIdentifier }),
  })
}
