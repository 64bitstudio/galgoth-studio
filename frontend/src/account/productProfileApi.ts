/**
 * Cliente HTTP del "perfil de producto" (ticket 091) -- API PROPIA de
 * galgoth-studio (`authenticatedFetch` + `API_BASE_URL`, no auth-core-mc),
 * mismo origen que `projectsApi.ts`/`mobsApi.ts`. Avatar y preferencias
 * de notificación viven acá porque son datos "de producto", no de
 * identidad -- auth-core-mc no tiene almacenamiento de archivos.
 */
import { authenticatedFetch } from '../auth/authenticatedFetch'
import { API_BASE_URL } from '../api/apiConfig'
import { ApiError } from '../api/ApiError'

export interface ProductProfile {
  /** Ruta relativa servible (`/api/account/avatar/{userId}`), `null` si no hay avatar subido. Usar `avatarUrl()` de `apiConfig.ts` para resolverla a una URL completa. */
  avatarUrl: string | null
  notifyEmail: boolean
  notifyProductNews: boolean
  notifySaveReminders: boolean
}

interface ApiErrorBody {
  error?: string
  message?: string
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const response = await authenticatedFetch(`${API_BASE_URL}${path}`, {
    ...init,
    headers: { 'Content-Type': 'application/json', ...init.headers },
  })
  if (!response.ok) {
    const body: ApiErrorBody | null = await response.json().catch(() => null)
    throw new ApiError(body?.message ?? `Error HTTP ${response.status}`, response.status, body?.error)
  }
  return (await response.json()) as T
}

export function getProductProfile(): Promise<ProductProfile> {
  return request<ProductProfile>('/api/account/profile')
}

/** Las 3 preferencias siempre explícitas, nunca parciales -- mismo criterio que el backend (`PreferencesRequest`). */
export function updatePreferences(
  notifyEmail: boolean,
  notifyProductNews: boolean,
  notifySaveReminders: boolean,
): Promise<ProductProfile> {
  return request<ProductProfile>('/api/account/preferences', {
    method: 'PATCH',
    body: JSON.stringify({ notifyEmail, notifyProductNews, notifySaveReminders }),
  })
}

/**
 * Ticket 091 -- body crudo (sin multipart), el content-type real viaja
 * en el header HTTP `Content-Type` (mismo estilo que
 * `referenceImagesApi.ts`/`thumbnailApi.ts`). Solo PNG/JPEG, máx. 5MB
 * (validado también del lado del backend, este límite es solo un
 * mensaje más rápido sin esperar el roundtrip).
 */
export async function uploadAvatar(file: File): Promise<string> {
  const response = await authenticatedFetch(`${API_BASE_URL}/api/account/avatar`, {
    method: 'POST',
    headers: { 'Content-Type': file.type },
    body: file,
  })
  if (!response.ok) {
    const body: ApiErrorBody | null = await response.json().catch(() => null)
    throw new ApiError(body?.message ?? `Error HTTP ${response.status}`, response.status, body?.error)
  }
  const result = (await response.json()) as { avatarUrl: string }
  return result.avatarUrl
}
