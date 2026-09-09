/**
 * Cliente HTTP del CRUD de proyectos (ticket 021) -- primera integración
 * real frontend->backend del proyecto. Llamadas directas (fetch), sin
 * proxy de Vite -- `docs/definiciones/galgoth-studio-mvp.md` §9 confirma
 * CORS habilitado como la estrategia elegida para el origen local de
 * desarrollo (ver `backend/.../config/WebConfig.java`), no un proxy.
 */

export interface MobThumbnail {
  mobId: string
  thumbnailKey: string | null
}

export interface ProjectSummary {
  id: string
  name: string
  mobCount: number
  mobThumbnails: MobThumbnail[]
  createdAt: string
  updatedAt: string
}

export interface ProjectDetail {
  id: string
  name: string
  mobCount: number
  createdAt: string
  updatedAt: string
}

interface ApiErrorBody {
  error?: string
  message?: string
  details?: string[] | null
}

export class ApiError extends Error {
  readonly status: number
  readonly code?: string

  constructor(message: string, status: number, code?: string) {
    super(message)
    this.status = status
    this.code = code
  }
}

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

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

export function renameProject(id: string, name: string): Promise<ProjectDetail> {
  return request<ProjectDetail>(`/api/projects/${id}`, { method: 'PATCH', body: JSON.stringify({ name }) })
}

export function deleteProject(id: string): Promise<void> {
  return request<void>(`/api/projects/${id}`, { method: 'DELETE' })
}

export function duplicateProject(id: string): Promise<ProjectDetail> {
  return request<ProjectDetail>(`/api/projects/${id}/duplicate`, { method: 'POST' })
}
