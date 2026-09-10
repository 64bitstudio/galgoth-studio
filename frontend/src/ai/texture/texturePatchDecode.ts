/**
 * Decodifica un `preview_texture_patch` (054, Diseño técnico §13) a un
 * `ImageBitmap` listo para componerse sobre el canvas de preview del
 * atlas -- mismo patrón de aislamiento que `pngImportDecode.ts` (048,
 * `editor/texture/`): decodificar una imagen real requiere
 * `createImageBitmap`, que jsdom (entorno de test) no implementa de
 * verdad, así que se aísla acá para mockear en el test del componente
 * en vez de dejar esa decodificación sin cobertura o acoplada a un
 * mock de `Image`/canvas mucho más amplio.
 */
import { API_BASE_URL } from '../../api/apiConfig'
import type { TexturePreviewPatchPayload } from './textureGenerationEvents'

export class TexturePatchDecodeError extends Error {}

export async function decodeTexturePreviewPatch(payload: TexturePreviewPatchPayload): Promise<ImageBitmap> {
  if (typeof createImageBitmap !== 'function') {
    throw new TexturePatchDecodeError('Este navegador no soporta decodificar el preview de textura (createImageBitmap no disponible).')
  }
  const blob = await resolvePatchBlob(payload)
  try {
    return await createImageBitmap(blob)
  } catch {
    throw new TexturePatchDecodeError('El parche de preview recibido no se pudo decodificar como imagen.')
  }
}

async function resolvePatchBlob(payload: TexturePreviewPatchPayload): Promise<Blob> {
  if (payload.encoding === 'base64') {
    return base64PngToBlob(payload.data)
  }
  const response = await fetch(`${API_BASE_URL}${payload.url}`)
  if (!response.ok) {
    throw new TexturePatchDecodeError(`No se pudo descargar el preview de textura (HTTP ${response.status}).`)
  }
  return response.blob()
}

function base64PngToBlob(base64: string): Blob {
  const binary = atob(base64)
  const bytes = new Uint8Array(binary.length)
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.codePointAt(i)!
  }
  return new Blob([bytes], { type: 'image/png' })
}
