/**
 * Cliente HTTP del CRUD de mobs (ticket 022) -- mismo patrón que
 * `projectsApi.ts` (ticket 021): fetch directo, sin proxy de Vite (CORS
 * es la estrategia elegida, ver `docs/definiciones/galgoth-studio-mvp.md` §9).
 */
import { ApiError } from './projectsApi'

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

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...init,
    headers: { 'Content-Type': 'application/json', ...init?.headers },
  })

  if (!response.ok) {
    const body: { error?: string; message?: string } | null = await response.json().catch(() => null)
    throw new ApiError(body?.message ?? `Error HTTP ${response.status}`, response.status, body?.error)
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
