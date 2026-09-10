/**
 * Cliente HTTP de `PUT /api/mobs/{mobId}/texture` (ticket 045 backend,
 * `MobTextureController`/`TextureService`) -- primer consumidor
 * frontend real, cerrado en el ticket 056 (checkpoint del PO: AC#1 de
 * 056 no era satisfacible porque el editor manual de textura -- 046/047
 * -- nunca subía el bitmap pintado al backend, solo el camino de
 * generación por IA vía "Aplicar", ticket 054, persistía algo real).
 *
 * Mismo patrón de body crudo (sin multipart, sin JSON) que
 * `thumbnailApi.ts`/`referenceImagesApi.ts`: el bitmap PNG viaja tal
 * cual en el body vía `Content-Type: image/png`, el backend decodifica/
 * hashea/persiste y devuelve el `storageKey` que ÉL calculó -- nunca
 * uno propuesto por el cliente (Diseño técnico §6 de
 * `docs/definiciones/galgoth-studio-fase3-textura.md`: "el backend es
 * la autoridad, no el frontend"). Ver `textureFlush.ts` para el único
 * caller real de este cliente (flush obligatorio antes de "Guardar").
 */
import { API_BASE_URL } from './apiConfig'
import { ApiError } from './ApiError'

export interface TextureUploadResult {
  storageKey: string
}

export async function uploadTexture(mobId: string, png: Blob): Promise<TextureUploadResult> {
  const response = await fetch(`${API_BASE_URL}/api/mobs/${mobId}/texture`, {
    method: 'PUT',
    headers: { 'Content-Type': 'image/png' },
    body: png,
  })

  const body = await response.json().catch(() => null)
  if (!response.ok) {
    throw new ApiError(body?.message ?? `Error HTTP ${response.status}`, response.status, body?.error)
  }
  return body as TextureUploadResult
}
