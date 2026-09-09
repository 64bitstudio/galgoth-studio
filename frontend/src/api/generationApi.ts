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

export async function startGeneration(mobId: string): Promise<StartGenerationResponse> {
  const response = await fetch(`${API_BASE_URL}/api/mobs/${mobId}/generate`, { method: 'POST' })

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
