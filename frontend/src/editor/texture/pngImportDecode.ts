/**
 * Ticket 048 -- decodifica un `File` (PNG elegido por el usuario) a un
 * `AtlasBuffer` (RGBA plano, mismo layout que `ImageData.data`).
 *
 * Aislado en su propio módulo (en vez de vivir inline en el componente)
 * por la misma razón que `pixelTools.ts` documenta para el resto del
 * editor: decodificar una imagen real requiere `createImageBitmap` +
 * un `<canvas>` con contexto 2D real, y jsdom (entorno de test) no
 * implementa ninguno de los dos de verdad -- aislar esto en una función
 * angosta permite que el resto del flujo de import (`textureImportTools.ts`,
 * el panel de confirmación) se pruebe con buffers sintéticos sin
 * depender de decodificación real de PNG, y que ESTA función en
 * particular se pruebe mockeando `createImageBitmap`/`getContext` (ver
 * `pngImportDecode.spec.ts`) en vez de quedar sin cobertura.
 *
 * Nunca escala: el bitmap se dibuja 1:1 a un canvas de su propio tamaño
 * intrínseco (`bitmap.width`/`bitmap.height`) -- el crop/pad hacia el
 * destino final es responsabilidad de `textureImportTools.cropOrPad`,
 * nunca de esta función.
 */
import type { AtlasBuffer } from './pixelTools'

export class PngDecodeError extends Error {}

export async function decodePngFileToAtlasBuffer(file: File): Promise<AtlasBuffer> {
  if (typeof createImageBitmap !== 'function') {
    throw new PngDecodeError('Este navegador no soporta importar imágenes (createImageBitmap no disponible).')
  }

  let bitmap: ImageBitmap
  try {
    bitmap = await createImageBitmap(file)
  } catch {
    throw new PngDecodeError('El archivo no se pudo decodificar como una imagen válida.')
  }

  try {
    const canvas = document.createElement('canvas')
    canvas.width = bitmap.width
    canvas.height = bitmap.height
    const ctx = canvas.getContext('2d')
    if (!ctx) {
      throw new PngDecodeError('No se pudo preparar un lienzo para leer los píxeles de la imagen.')
    }
    ctx.drawImage(bitmap, 0, 0)
    const imageData = ctx.getImageData(0, 0, bitmap.width, bitmap.height)
    return { pixels: new Uint8ClampedArray(imageData.data), width: imageData.width, height: imageData.height }
  } finally {
    bitmap.close()
  }
}
