/**
 * Cliente HTTP del CRUD de mobs (ticket 022) -- mismo patrón que
 * `projectsApi.ts` (ticket 021): fetch directo, sin proxy de Vite (CORS
 * es la estrategia elegida, ver `docs/definiciones/galgoth-studio-mvp.md` §9).
 */
import { API_BASE_URL } from '../api/apiConfig'
import { ApiError } from '../api/ApiError'

export type BaseType = 'humanoid' | 'arachnid' | 'quadruped' | 'flying' | 'custom'
export type MobStatus = 'draft' | 'in_progress' | 'ready'

export interface MobSummary {
  id: string
  name: string
  baseType: BaseType
  status: MobStatus
  thumbnailKey: string | null
  updatedAt: string
}

/**
 * Ticket 071 -- "Continuar trabajando" (Inicio) necesita mobs recientes
 * CRUZANDO todos los proyectos; a diferencia de `MobSummary` (siempre
 * dentro de un proyecto ya conocido por la URL), acá el mob puede ser de
 * cualquier proyecto, así que el resumen incluye `projectId` para poder
 * navegar a `/projects/{projectId}/mobs/{mobId}/edit`.
 */
export interface RecentMobSummary extends MobSummary {
  projectId: string
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...init,
    headers: { 'Content-Type': 'application/json', ...init?.headers },
  })

  if (!response.ok) {
    const body: { error?: string; message?: string } | null = await response.json().catch(() => null)
    throw new ApiError(body?.message ?? `Error HTTP ${response.status}`, response.status, body?.error)
  }
  if (response.status === 204) {
    // Ticket 039 -- DELETE responde sin cuerpo; `response.json()` fallaría al parsear un body vacío.
    return undefined as T
  }
  return (await response.json()) as T
}

export function listMobs(projectId: string): Promise<MobSummary[]> {
  return request<MobSummary[]>(`/api/projects/${projectId}/mobs`)
}

export function createMob(projectId: string, name: string, baseType: BaseType): Promise<MobSummary> {
  return request<MobSummary>(`/api/projects/${projectId}/mobs`, {
    method: 'POST',
    body: JSON.stringify({ name, baseType }),
  })
}

/** Ticket 034 -- resumen de un mob por su id solo, sin `projectId` en el path (ruta ya prevista desde el bootstrap del proyecto). */
export function getMob(mobId: string): Promise<MobSummary> {
  return request<MobSummary>(`/api/mobs/${mobId}`)
}

/** Ticket 039 -- Renombrar (menú de acciones de `MobCard.vue`). */
export function renameMob(mobId: string, name: string): Promise<MobSummary> {
  return request<MobSummary>(`/api/mobs/${mobId}`, { method: 'PATCH', body: JSON.stringify({ name }) })
}

/** Ticket 039 -- Eliminar (soft-delete, mismo criterio que `deleteProject`). */
export function deleteMob(mobId: string): Promise<void> {
  return request<void>(`/api/mobs/${mobId}`, { method: 'DELETE' })
}

/** Ticket 071 -- "Continuar trabajando" (Inicio). `limit` opcional, mismo default (3) que el backend. */
export function listRecentMobs(limit?: number): Promise<RecentMobSummary[]> {
  const query = limit !== undefined ? `?limit=${limit}` : ''
  return request<RecentMobSummary[]>(`/api/mobs/recent${query}`)
}
