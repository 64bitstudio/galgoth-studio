/**
 * Cliente HTTP del pipeline de generación/regeneración de textura por IA
 * (ticket 054 backend, `TextureGenerationController`; ticket 055
 * frontend, HU-36 a HU-38) -- ver `docs/API.md`. El progreso SSE
 * reutiliza `GET /api/jobs/{jobId}/events` TAL CUAL (029, `eventsUrl` ya
 * exportado por `generationApi.ts`) -- ese endpoint es genérico por
 * `jobId`, sin lógica de `job_type`, así que no hace falta un segundo
 * cliente para lo mismo.
 *
 * "Reject" NO es un endpoint (ver `docs/API.md`): la propuesta queda en
 * `ai_jobs` para auditoría, sin tocar `mob_drafts`/`mob_revisions`/MinIO
 * -- simplemente no se llama a `applyTexture`. No hay ninguna función
 * `rejectTexture` acá a propósito.
 */
import { API_BASE_URL } from './apiConfig'
import { ApiError } from './ApiError'
import type { FaceName } from '../domain/MobProjectModel'

export type TextureStyleValue = 'faithful' | 'minecraft_vanilla' | 'pixel_art' | 'realistic'
export type TextureDetailLevelValue = 'low' | 'medium' | 'high'

export interface StartTextureGenerationRequest {
  style: TextureStyleValue
  detailLevel: TextureDetailLevelValue
  /** `null` -> HU-36 (modelo completo, todos los bones con geometría); un id de bone -> HU-37 (regenera solo ese bone). */
  boneId: string | null
}

export interface StartTextureGenerationResponse {
  jobId: string
}

/** Espejo de `TouchedFace` (backend, ticket 054) -- unidad del diff Antes/Después (HU-38) y de la detección de sobrescritura de contenido pintado a mano (HU-37 AC #2). */
export interface TouchedFace {
  cuboidId: string
  face: FaceName
  rect: [number, number, number, number]
  handPaintedOverwrite: boolean
}

/** Espejo de `TextureGenerationResultView` (backend) -- respuesta de `GET /api/jobs/{jobId}/texture-result`. */
export interface TextureGenerationResult {
  jobId: string
  mobId: string
  wholeModel: boolean
  touchedBoneIds: string[]
  touchedFaces: TouchedFace[]
  hasHandPaintedOverwrite: boolean
  beforeAtlasPngBase64: string
  afterAtlasPngBase64: string
}

export interface ApplyTextureResponse {
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

/** `POST /api/mobs/{mobId}/ai/generate-texture` -- 202, no bloqueante. Reutiliza siempre la imagen de referencia más reciente ya subida en Fase 2 (HU-36 AC #1) -- este cliente nunca sube una imagen nueva. */
export function startTextureGeneration(mobId: string, request: StartTextureGenerationRequest): Promise<StartTextureGenerationResponse> {
  return requestJson<StartTextureGenerationResponse>(`${API_BASE_URL}/api/mobs/${mobId}/ai/generate-texture`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
  })
}

/** `GET /api/jobs/{jobId}/texture-result` (HU-38) -- diff Antes/Después de una propuesta ya completada, nunca re-ejecuta el pipeline. */
export function getTextureResult(jobId: string): Promise<TextureGenerationResult> {
  return requestJson<TextureGenerationResult>(`${API_BASE_URL}/api/jobs/${jobId}/texture-result`)
}

/** `POST /api/jobs/{jobId}/apply-texture` (HU-38 AC #3) -- Apply atómico; `409 STALE_TEXTURE_BASE` si el draft/revisión base avanzaron desde que se generó la propuesta (código expuesto vía `ApiError.code`, mismo mecanismo que `STALE_EDIT_BASE` de 031). */
export function applyTexture(jobId: string): Promise<ApplyTextureResponse> {
  return requestJson<ApplyTextureResponse>(`${API_BASE_URL}/api/jobs/${jobId}/apply-texture`, { method: 'POST' })
}
