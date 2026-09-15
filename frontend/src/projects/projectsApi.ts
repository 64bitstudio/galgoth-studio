/**
 * Cliente HTTP del CRUD de proyectos (ticket 021) -- primera integración
 * real frontend->backend del proyecto. Llamadas directas (fetch), sin
 * proxy de Vite -- `docs/definiciones/galgoth-studio-mvp.md` §9 confirma
 * CORS habilitado como la estrategia elegida para el origen local de
 * desarrollo (ver `backend/.../config/WebConfig.java`), no un proxy.
 *
 * Ticket 089 (hotfix, regresión de auth-core-mc#084) -- `POST`/`GET
 * /api/projects` ya exigen `Authorization: Bearer` en el backend; este
 * cliente usaba `fetch` plano y nunca lo adjuntaba (nadie lo necesitó
 * hasta ahora, ver Javadoc de `authenticatedFetch`). Pasa a usar
 * `authenticatedFetch` (ticket 078), que ya maneja el refresh ante un
 * 401 -- mismo mecanismo, ya construido y probado, solo sin cablear
 * hasta este ticket.
 */
import { authenticatedFetch } from '../auth/authenticatedFetch'
import { useSessionStore } from '../auth/sessionStore'
import { API_BASE_URL } from '../api/apiConfig'
import { ApiError } from '../api/ApiError'

export interface MobThumbnail {
  mobId: string
  thumbnailKey: string | null
}

/** Ticket 072 -- derivado en el backend a partir de los mobs del proyecto, no una columna real: "active" si tiene algún mob fuera de draft, "draft" si todos son draft o no tiene ninguno. */
export type ProjectStatus = 'active' | 'draft'

/** Ticket 084 -- viaja desde entonces, pero el frontend nunca la necesitó hasta el toggle del ticket 087 (hallazgo real: faltaba en ambas interfaces de este archivo). */
export type ProjectVisibility = 'PRIVATE' | 'PUBLIC'

export interface ProjectSummary {
  id: string
  name: string
  /** Ticket 073 -- opcional, `null` si el proyecto no tiene. Viaja también acá (no solo en `ProjectDetail`) para que "Renombrar" desde `ProjectsDashboard.vue` pueda reenviarla sin cambios (ver `renameProject`). */
  description: string | null
  mobCount: number
  mobThumbnails: MobThumbnail[]
  status: ProjectStatus
  visibility: ProjectVisibility
  /** Ticket 086 -- nombre completo capturado al crear el proyecto (`sessionStore.user.nombre`/`apellidos` en ese momento), `null` si no se envió. Usado por Explorar (ticket 088) para mostrar "por Fulano Pérez"; puede quedar desactualizado si el usuario cambia su nombre después (tradeoff aceptado, documento de definición). */
  ownerDisplayName: string | null
  createdAt: string
  updatedAt: string
}

export interface ProjectDetail {
  id: string
  name: string
  description: string | null
  mobCount: number
  visibility: ProjectVisibility
  /** Ticket 086 -- ver docstring de `ProjectSummary.ownerDisplayName`. */
  ownerDisplayName: string | null
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
  const response = await authenticatedFetch(`${API_BASE_URL}${path}`, {
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

/**
 * Ticket 086 -- `ownerDisplayName` viaja al crear porque el frontend ya
 * conoce `sessionStore.user.nombre`/`apellidos` en este momento (auth-core-mc
 * no expone un endpoint público de perfil); se graba tal cual en
 * `projects.owner_display_name` para mostrarlo en Explorar sin una
 * integración nueva. `null` (sesión sin `user`, caso que no debería darse
 * en la práctica ya que crear un proyecto ya exige sesión) se envía tal
 * cual -- el backend lo acepta y lo deja sin autor visible en ese caso.
 */
export function createProject(name: string): Promise<ProjectDetail> {
  const user = useSessionStore().user
  const ownerDisplayName = user ? `${user.nombre} ${user.apellidos}`.trim() : null
  return request<ProjectDetail>('/api/projects', { method: 'POST', body: JSON.stringify({ name, ownerDisplayName }) })
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

/** Ticket 087 -- toggle de "Hacer público"/"Hacer privado" en `ProjectDetail.vue`, sobre el `PATCH` del ticket 086. */
export function changeProjectVisibility(id: string, visibility: ProjectVisibility): Promise<ProjectDetail> {
  return request<ProjectDetail>(`/api/projects/${id}/visibility`, { method: 'PATCH', body: JSON.stringify({ visibility }) })
}
