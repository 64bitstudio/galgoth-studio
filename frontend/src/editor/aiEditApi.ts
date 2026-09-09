/**
 * Cliente HTTP de la edición conversacional por IA sobre un mob ya
 * existente (ticket 031, `AiEditController` en el backend). Síncrono --
 * a diferencia del pipeline de generación (029), acá no hay SSE: una
 * sola llamada rápida al `StructuredReasoningProvider`.
 */
import { API_BASE_URL } from '../api/apiConfig'
import { ApiError } from '../api/ApiError'
import type { MobProjectModel } from '../domain/MobProjectModel'

export type ChangedElementType = 'bone' | 'cuboid'
export type ChangeKind = 'added' | 'modified' | 'removed'

export interface ChangedElement {
  type: ChangedElementType
  id: string
  name: string
  changeKind: ChangeKind
}

export interface EditGeometryPlan {
  jobId: string
  summary: string
  beforeCuboidCount: number
  beforeBoneCount: number
  afterCuboidCount: number
  afterBoneCount: number
  changedElements: ChangedElement[]
  beforeModel: MobProjectModel
  afterModel: MobProjectModel
}

export interface ApplyEditResponse {
  revisionNumber: number
  draftVersion: number
}

async function requestJson<T>(url: string, init?: RequestInit): Promise<T> {
  const response = await fetch(url, init)
  const body = await response.json().catch(() => null)
  if (!response.ok) {
    throw new ApiError(body?.message ?? `Error HTTP ${response.status}`, response.status, body?.error)
  }
  return body as T
}

export function requestEditPlan(mobId: string, instruction: string): Promise<EditGeometryPlan> {
  return requestJson<EditGeometryPlan>(`${API_BASE_URL}/api/mobs/${mobId}/ai/edit-geometry`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ instruction }),
  })
}

export function applyEdit(jobId: string): Promise<ApplyEditResponse> {
  return requestJson<ApplyEditResponse>(`${API_BASE_URL}/api/jobs/${jobId}/apply-edit`, { method: 'POST' })
}
