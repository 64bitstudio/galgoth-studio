/**
 * Cliente HTTP de "Guardar" (ticket 023) -- el ticket 020 implementó
 * `POST /api/mobs/{mobId}/revisions` solo en el backend (VoBo explícito
 * del Product Owner en su momento); este es el primer cliente frontend
 * que lo invoca de verdad, desde el botón "Guardar" del `EditorToolbar`.
 * `getDraft` (034): primer cliente frontend de `GET /api/mobs/{mobId}/draft`
 * (020, backend-only hasta ahora) -- usado por `MobEditor.vue` para
 * cargar el draft real de un mob al abrir la ruta de edición.
 */
import { API_BASE_URL } from '../api/apiConfig'
import { ApiError } from '../api/ApiError'
import type { MobProjectModel } from '../domain/MobProjectModel'

export interface SaveRevisionResult {
  created: boolean
  revisionNumber: number
  reason: string | null
}

export interface DraftView {
  mobId: string
  draftVersion: number
  model: MobProjectModel
  updatedAt: string
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

export async function getDraft(mobId: string): Promise<DraftView> {
  const response = await fetch(`${API_BASE_URL}/api/mobs/${mobId}/draft`)

  const body = await response.json().catch(() => null)
  if (!response.ok) {
    throw new ApiError(body?.message ?? `Error HTTP ${response.status}`, response.status, body?.error)
  }
  return body as DraftView
}
