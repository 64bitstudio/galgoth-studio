/**
 * Cliente HTTP del pipeline de thumbnails (ticket 023). El thumbnail es
 * un asset DERIVADO, nunca parte de la transacción del commit que lo
 * dispara (ver `ThumbnailService` en el backend) -- este cliente se
 * invoca DESPUÉS de un Guardar exitoso, y su fallo se captura por
 * separado en el llamador (`EditorToolbar.vue`) para no revertir ni
 * bloquear el commit que ya se completó.
 */
import { API_BASE_URL } from '../api/apiConfig'
import { ApiError } from '../api/ApiError'

export async function uploadThumbnail(mobId: string, png: Blob): Promise<void> {
  const response = await fetch(`${API_BASE_URL}/api/mobs/${mobId}/thumbnail`, {
    method: 'POST',
    headers: { 'Content-Type': 'image/png' },
    body: png,
  })
  if (!response.ok) {
    throw new ApiError(`No se pudo subir el thumbnail (HTTP ${response.status}).`, response.status)
  }
}
