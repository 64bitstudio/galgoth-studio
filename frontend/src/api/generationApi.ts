/**
 * Cliente HTTP del pipeline de generación asistida por IA (ticket 029,
 * `GenerationJobController` en el backend). `eventsUrl` no hace un
 * `fetch` -- se le pasa directo al constructor `EventSource` del
 * navegador, que maneja la conexión/reconexión SSE por su cuenta
 * (incluyendo reenviar `Last-Event-ID` automáticamente en cada
 * reconexión, ver AC #3 -- ningún código de reconexión manual hace
 * falta acá).
 */
import { API_BASE_URL } from './apiConfig'
import { ApiError } from './ApiError'

export interface StartGenerationResponse {
  jobId: string
}

/** Presupuesto orientativo de cuboides totales (ticket 100, HU-4) -- LOW 8-18, MEDIUM 18-45, HIGH 35-80. Espejo de `GeometryDetail` (backend). */
export type GeometryDetail = 'LOW' | 'MEDIUM' | 'HIGH'

/**
 * TOPE de atlas, no tamaño exacto (ticket 103, HU-10) -- espejo de
 * `TextureResolution` (backend): el atlas sigue saliendo del packing real
 * (Diseño técnico §7), y este valor acota qué densidad de texel se usa.
 * Decisión explícita del PO; ver el Javadoc del enum en backend.
 */
export type TextureResolution = '64' | '128' | '256'

/** Campos ausentes/undefined usan los defaults del backend (`MEDIUM`/`128`) -- mismo contrato que un request sin body. */
export async function startGeneration(
  mobId: string,
  geometryDetail?: GeometryDetail,
  textureResolution?: TextureResolution,
): Promise<StartGenerationResponse> {
  const payload: Record<string, string> = {}
  if (geometryDetail) {
    payload.geometryDetail = geometryDetail
  }
  if (textureResolution) {
    payload.textureResolution = textureResolution
  }
  const hasPayload = Object.keys(payload).length > 0
  const response = await fetch(`${API_BASE_URL}/api/mobs/${mobId}/generate`, {
    method: 'POST',
    headers: hasPayload ? { 'Content-Type': 'application/json' } : undefined,
    body: hasPayload ? JSON.stringify(payload) : undefined,
  })

  const body = await response.json().catch(() => null)
  if (!response.ok) {
    throw new ApiError(body?.message ?? `Error HTTP ${response.status}`, response.status, body?.error)
  }
  return body as StartGenerationResponse
}

export async function cancelGeneration(jobId: string): Promise<void> {
  const response = await fetch(`${API_BASE_URL}/api/jobs/${jobId}/cancel`, { method: 'POST' })
  if (!response.ok) {
    const body = await response.json().catch(() => null)
    throw new ApiError(body?.message ?? `Error HTTP ${response.status}`, response.status, body?.error)
  }
}

export function eventsUrl(jobId: string): string {
  return `${API_BASE_URL}/api/jobs/${jobId}/events`
}
