/**
 * Ticket 047 -- extraído de `textureEditorStore.ts` (ticket 046, ya
 * mergeado) para que la Cubeta (`pixelTools.ts`, flood-fill real) pueda
 * recortar/insertar un `rect` sobre un buffer RGBA plano SIN duplicar la
 * misma mecánica de slicing 2D que el store ya tenía privada -- evita
 * divergencia entre "cómo lee/escribe un rect el store" y "cómo lo hace
 * la Cubeta" (regla del equipo: sin duplicación de manipulación de
 * píxeles entre herramientas). Comportamiento idéntico al que
 * `textureEditorStore.ts` tenía inline: O(área de `rect`), nunca
 * O(buffer completo).
 */
import type { TextureRect } from './TexturePatchCommand'

/** Escribe `patch` (RGBA, `rect.width * rect.height * 4` bytes) dentro de `pixels` (buffer plano de un bitmap de `bufferWidth` de ancho), fila por fila. */
export function writeRectInto(pixels: Uint8ClampedArray, bufferWidth: number, rect: TextureRect, patch: Uint8ClampedArray): void {
  const rowBytes = rect.width * 4
  for (let row = 0; row < rect.height; row += 1) {
    const srcStart = row * rowBytes
    const destStart = ((rect.y + row) * bufferWidth + rect.x) * 4
    pixels.set(patch.subarray(srcStart, srcStart + rowBytes), destStart)
  }
}

/** Lee `rect` desde `pixels` (buffer plano de un bitmap de `bufferWidth` de ancho) a un buffer RGBA nuevo de `rect.width * rect.height * 4`. */
export function readRectFrom(pixels: Uint8ClampedArray, bufferWidth: number, rect: TextureRect): Uint8ClampedArray {
  const rowBytes = rect.width * 4
  const out = new Uint8ClampedArray(rect.width * rect.height * 4)
  for (let row = 0; row < rect.height; row += 1) {
    const srcStart = ((rect.y + row) * bufferWidth + rect.x) * 4
    out.set(pixels.subarray(srcStart, srcStart + rowBytes), row * rowBytes)
  }
  return out
}
