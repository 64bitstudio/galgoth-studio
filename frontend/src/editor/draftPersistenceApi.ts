/**
 * Cliente HTTP de "Guardar" (ticket 023) -- el ticket 020 implementó
 * `POST /api/mobs/{mobId}/revisions` solo en el backend (VoBo explícito
 * del Product Owner en su momento); este es el primer cliente frontend
 * que lo invoca de verdad, desde el botón "Guardar" del `EditorToolbar`.
 */
import { API_BASE_URL } from '../api/apiConfig'
import { ApiError } from '../api/ApiError'
import type { MobProjectModel } from '../domain/MobProjectModel'

export interface SaveRevisionResult {
  created: boolean
  revisionNumber: number
  reason: string | null
}

export async function saveRevision(mobId: string, model: MobProjectModel): Promise<SaveRevisionResult> {
  const response = await fetch(`${API_BASE_URL}/api/mobs/${mobId}/revisions`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ model }),
  })

  const body = await response.json().catch(() => null)
  if (!response.ok) {
    throw new ApiError(body?.message ?? `Error HTTP ${response.status}`, response.status, body?.error)
  }
  return body as SaveRevisionResult
}
