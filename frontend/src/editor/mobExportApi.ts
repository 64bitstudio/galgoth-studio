/**
 * Cliente HTTP de la pantalla de exportación (ticket 032, HU-19,
 * `MobExportController` en el backend). `getExportStatus` calcula la
 * compatibilidad FMM SIEMPRE contra la última revisión guardada (nunca
 * el draft en curso, decisión confirmada explícitamente con el PO --
 * ver el comentario de cabecera de `MobExportService` en el backend).
 * `downloadBbmodel` exporta esa misma revisión -- "Guardar y exportar"
 * NO es un endpoint propio: el orquestador (`ExportScreen.vue`) llama
 * primero `saveRevision` (`draftPersistenceApi.ts`, 020/023) y RECIÉN
 * DESPUÉS `downloadBbmodel`, dos llamadas sucesivas reutilizando el
 * mecanismo de "Guardar" tal cual en vez de duplicarlo.
 */
import { API_BASE_URL } from '../api/apiConfig'
import { ApiError } from '../api/ApiError'
import type { FmmIssue } from '../api/generationResultApi'

export interface ExportStatus {
  mobId: string
  mobName: string
  hasSavedRevision: boolean
  hasUnsavedChanges: boolean
  fmmCompatible: boolean | null
  fmmIssues: FmmIssue[]
}

export interface DownloadedBbmodel {
  filename: string
  blob: Blob
}

export async function getExportStatus(mobId: string): Promise<ExportStatus> {
  const response = await fetch(`${API_BASE_URL}/api/mobs/${mobId}/export/status`)
  const body = await response.json().catch(() => null)
  if (!response.ok) {
    throw new ApiError(body?.message ?? `Error HTTP ${response.status}`, response.status, body?.error)
  }
  return body as ExportStatus
}

/** Nombre de archivo real, tomado del header `Content-Disposition` que arma el backend (nunca inventado en el cliente). */
function filenameFrom(response: Response, fallback: string): string {
  const header = response.headers.get('Content-Disposition') ?? ''
  const match = /filename="?([^";]+)"?/i.exec(header)
  return match?.[1] ?? fallback
}

export async function downloadBbmodel(mobId: string): Promise<DownloadedBbmodel> {
  const response = await fetch(`${API_BASE_URL}/api/mobs/${mobId}/export/bbmodel`)
  if (!response.ok) {
    const body = await response.json().catch(() => null)
    throw new ApiError(body?.message ?? `Error HTTP ${response.status}`, response.status, body?.error)
  }
  const blob = await response.blob()
  return { filename: filenameFrom(response, `${mobId}.bbmodel`), blob }
}
