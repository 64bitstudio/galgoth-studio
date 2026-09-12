/**
 * Cliente HTTP del CRUD de proyectos (ticket 021) -- primera integración
 * real frontend->backend del proyecto. Llamadas directas (fetch), sin
 * proxy de Vite -- `docs/definiciones/galgoth-studio-mvp.md` §9 confirma
 * CORS habilitado como la estrategia elegida para el origen local de
 * desarrollo (ver `backend/.../config/WebConfig.java`), no un proxy.
 */
import { API_BASE_URL } from '../api/apiConfig'
import { ApiError } from '../api/ApiError'

export interface MobThumbnail {
  mobId: string
  thumbnailKey: string | null
}

/** Ticket 072 -- derivado en el backend a partir de los mobs del proyecto, no una columna real: "active" si tiene algún mob fuera de draft, "draft" si todos son draft o no tiene ninguno. */
export type ProjectStatus = 'active' | 'draft'

export interface ProjectSummary {
  id: string
  name: string
  /** Ticket 073 -- opcional, `null` si el proyecto no tiene. Viaja también acá (no solo en `ProjectDetail`) para que "Renombrar" desde `ProjectsDashboard.vue` pueda reenviarla sin cambios (ver `renameProject`). */
  description: string | null
  mobCount: number
  mobThumbnails: MobThumbnail[]
  status: ProjectStatus
  createdAt: string
  updatedAt: string
}

export interface ProjectDetail {
  id: string
  name: string
  description: string | null
  mobCount: number
  createdAt: string
  updatedAt: string
}

interface ApiErrorBody {
  error?: string
  message?: string
  details?: string[] | null
}

// Re-exportado por compatibilidad -- los consumidores existentes (ticket
// 021/022) importan ApiError desde aquí; el tipo en sí ahora vive en
// `src/api/ApiError.ts` (compartido con `mobsApi.ts` y los clientes
// nuevos del ticket 023, en vez de que cada uno declare el suyo).
export { ApiError }

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...init,
    headers: { 'Content-Type': 'application/json', ...init?.headers },
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

export function listProjects(): Promise<ProjectSummary[]> {
  return request<ProjectSummary[]>('/api/projects')
}

export function getProject(id: string): Promise<ProjectDetail> {
  return request<ProjectDetail>(`/api/projects/${id}`)
}

export function createProject(name: string): Promise<ProjectDetail> {
  return request<ProjectDetail>('/api/projects', { method: 'POST', body: JSON.stringify({ name }) })
}

/** Ticket 073 -- `description` siempre explícita (nunca omitida): el caller reenvía el valor actual tal cual si no lo está cambiando (ver docstring del backend, `RenameProjectRequest`) -- omitirla la borraría. */
export function renameProject(id: string, name: string, description: string | null): Promise<ProjectDetail> {
  return request<ProjectDetail>(`/api/projects/${id}`, { method: 'PATCH', body: JSON.stringify({ name, description }) })
}

export function deleteProject(id: string): Promise<void> {
  return request<void>(`/api/projects/${id}`, { method: 'DELETE' })
}

export function duplicateProject(id: string): Promise<ProjectDetail> {
  return request<ProjectDetail>(`/api/projects/${id}/duplicate`, { method: 'POST' })
}
