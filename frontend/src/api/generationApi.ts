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

/** `geometryDetail` ausente/undefined usa el default del backend (`MEDIUM`) -- mismo contrato que un request sin body. */
export async function startGeneration(mobId: string, geometryDetail?: GeometryDetail): Promise<StartGenerationResponse> {
  const response = await fetch(`${API_BASE_URL}/api/mobs/${mobId}/generate`, {
    method: 'POST',
    headers: geometryDetail ? { 'Content-Type': 'application/json' } : undefined,
    body: geometryDetail ? JSON.stringify({ geometryDetail }) : undefined,
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
