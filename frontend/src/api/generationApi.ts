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
 * Densidad de téxel (ticket 109) -- espejo de `TextureDensity` (backend),
 * reemplaza al `TextureResolution` del 103.
 *
 * Elige cuántos téxeles recibe cada unidad de modelo; el tamaño del atlas
 * sale del packing a esa densidad, sin tope. El tope del 103 se eliminó
 * porque, medido contra un mob real, degradaba la densidad justo en los
 * modelos complejos. Ver el enum del backend para el detalle.
 */
export type TextureDensity = 'standard' | 'high' | 'max'

/** Campos ausentes/undefined usan los defaults del backend (`MEDIUM`/`max`) -- mismo contrato que un request sin body. */
export async function startGeneration(
  mobId: string,
  geometryDetail?: GeometryDetail,
  textureDensity?: TextureDensity,
): Promise<StartGenerationResponse> {
  const payload: Record<string, string> = {}
  if (geometryDetail) {
    payload.geometryDetail = geometryDetail
  }
  if (textureDensity) {
    payload.textureDensity = textureDensity
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
