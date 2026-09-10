/**
 * Ticket 056 -- codifica el `TextureAtlas` en memoria (RGBA plano, mismo
 * layout que `ImageData.data`, ver `textureEditorStore.ts`) a un `Blob`
 * PNG real, listo para subir vía `PUT /api/mobs/{mobId}/texture`
 * (`textureUploadApi.ts`). Dirección inversa/simétrica de
 * `pngImportDecode.ts` (PNG -> AtlasBuffer): mismo motivo para aislarlo
 * en su propio módulo angosto -- requiere un `<canvas>` con contexto 2D
 * real (`putImageData` + `toBlob`), y jsdom (entorno de test) no
 * implementa ninguno de los dos de verdad, así que el resto del flujo de
 * flush (`textureFlush.ts`) se prueba mockeando esta función en vez de
 * quedar acoplado a un canvas real.
 */
import type { TextureAtlas } from './textureEditorStore'

export class PngEncodeError extends Error {}

export async function encodeAtlasToPngBlob(atlas: TextureAtlas): Promise<Blob> {
  const canvas = document.createElement('canvas')
  canvas.width = atlas.width
  canvas.height = atlas.height
  const ctx = canvas.getContext('2d')
  if (!ctx) {
    throw new PngEncodeError('No se pudo preparar un lienzo para codificar el atlas de textura como PNG.')
  }
  ctx.putImageData(new ImageData(new Uint8ClampedArray(atlas.pixels), atlas.width, atlas.height), 0, 0)

  return await new Promise<Blob>((resolve, reject) => {
    canvas.toBlob((blob) => {
      if (blob) {
        resolve(blob)
      } else {
        reject(new PngEncodeError('No se pudo codificar el atlas como PNG (toBlob devolvió null).'))
      }
    }, 'image/png')
  })
}
