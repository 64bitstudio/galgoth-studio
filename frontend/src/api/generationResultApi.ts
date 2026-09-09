/**
 * Cliente HTTP del resultado de un job de generación (ticket 030,
 * `GenerationJobController` en el backend). Nunca re-ejecuta el
 * pipeline de IA -- solo lee (`getGenerationResult`) o acepta
 * (`applyGeneration`) una propuesta que `ai_jobs.proposal_jsonb` YA
 * tiene guardada desde 028/029.
 */
import { API_BASE_URL } from './apiConfig'
import { ApiError } from './ApiError'

export type FmmIssueSeverity = 'ERROR' | 'WARNING'

export interface FmmIssue {
  severity: FmmIssueSeverity
  rule: string
  element: string
  message: string
}

export interface GenerationResult {
  jobId: string
  mobId: string
  mobName: string
  cuboidCount: number
  boneCount: number
  textureWidth: number
  textureHeight: number
  fmmCompatible: boolean
  fmmIssues: FmmIssue[]
}

export interface ApplyGenerationResponse {
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

export function getGenerationResult(jobId: string): Promise<GenerationResult> {
  return requestJson<GenerationResult>(`${API_BASE_URL}/api/jobs/${jobId}/result`)
}

export function applyGeneration(jobId: string): Promise<ApplyGenerationResponse> {
  return requestJson<ApplyGenerationResponse>(`${API_BASE_URL}/api/jobs/${jobId}/apply`, { method: 'POST' })
}
