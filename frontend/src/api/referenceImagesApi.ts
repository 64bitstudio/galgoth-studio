/**
 * Cliente HTTP de imágenes de referencia (ticket 024 backend, primer
 * consumidor frontend real en este ticket 027). Mismo patrón de body
 * crudo (sin multipart) que `thumbnailApi.ts` -- el content-type real
 * de la imagen viaja en el header `Content-Type`, no en un campo JSON.
 */
import { API_BASE_URL } from './apiConfig'
import { ApiError } from './ApiError'

export interface ReferenceImageSummary {
  id: string
  url: string
  width: number
  height: number
  contentType: string
  createdAt: string
}

/** Mismo límite que el backend (10MB, VoBo del PO en el ticket 024) -- validado también acá para dar feedback inmediato sin esperar el rechazo del servidor. */
export const MAX_REFERENCE_IMAGE_BYTES = 10 * 1024 * 1024
export const SUPPORTED_REFERENCE_IMAGE_TYPES = ['image/png', 'image/jpeg']

export async function uploadReferenceImage(mobId: string, file: Blob): Promise<ReferenceImageSummary> {
  const response = await fetch(`${API_BASE_URL}/api/mobs/${mobId}/references`, {
    method: 'POST',
    headers: { 'Content-Type': file.type },
    body: file,
  })

  const body = await response.json().catch(() => null)
  if (!response.ok) {
    throw new ApiError(body?.message ?? `Error HTTP ${response.status}`, response.status, body?.error)
  }
  return body as ReferenceImageSummary
}
