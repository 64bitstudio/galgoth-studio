/**
 * Ticket 048 -- import de PNG (región seleccionada + atlas completo),
 * Diseño técnico §8 de `docs/definiciones/galgoth-studio-fase3-textura.md`
 * (HU-28).
 *
 * Funciones PURAS (sin canvas 2D, sin DOM) que resuelven "¿cómo encaja un
 * bitmap importado de tamaño S dentro de un destino de tamaño T?" -- mismo
 * criterio de testabilidad que `pixelTools.ts`: la decisión pixel-perfect
 * de §18 ("sin antialiasing") ya establecida por el ticket 047 prohíbe
 * CUALQUIER resampleo, así que este módulo deliberadamente NO expone --
 * ni internamente usa -- ninguna operación de escalado/resize. Las únicas
 * dos operaciones que preservan cada píxel importado sin resamplear son:
 *
 * - **crop**: si el origen es más grande que el destino en algún eje, el
 *   excedente se descarta (nunca se comprime para que quepa).
 * - **pad**: si el origen es más chico que el destino en algún eje, el
 *   margen faltante se rellena TRANSPARENTE (0,0,0,0) -- nunca se estira
 *   el contenido existente para llenarlo.
 *
 * Alineación elegida: esquina superior izquierda (0,0) fija -- el
 * excedente se recorta por la derecha/abajo, el margen se agrega por la
 * derecha/abajo. Es la alineación más predecible para un atlas (que ya
 * usa origen superior-izquierdo en todo el resto del editor -- ver
 * `flipY = false` en `TextureCanvas.vue`) y evita introducir un segundo
 * eje de decisión (¿centrado? ¿por eje?) que el ticket no especifica.
 * Documentado explícitamente en el ticket 048 como una decisión de
 * implementación, no una suposición de negocio escondida.
 */
import type { AtlasBuffer } from './pixelTools'

/**
 * Recorta y/o rellena `source` para que quede EXACTAMENTE de
 * `targetWidth` × `targetHeight`, sin resamplear ni un solo píxel:
 * copia byte a byte el área que se superpone (esquina superior
 * izquierda) y deja en 0 (transparente) cualquier byte del destino que
 * no tenga contraparte en el origen. Determinista y sin efectos
 * secundarios -- nunca muta `source.pixels`.
 */
export function cropOrPad(source: AtlasBuffer, targetWidth: number, targetHeight: number): AtlasBuffer {
  const pixels = new Uint8ClampedArray(targetWidth * targetHeight * 4)
  const copyWidth = Math.min(source.width, targetWidth)
  const copyHeight = Math.min(source.height, targetHeight)

  for (let y = 0; y < copyHeight; y += 1) {
    const srcStart = y * source.width * 4
    const destStart = y * targetWidth * 4
    pixels.set(source.pixels.subarray(srcStart, srcStart + copyWidth * 4), destStart)
  }

  return { pixels, width: targetWidth, height: targetHeight }
}

/** Resultado de comparar las dimensiones de un import contra su destino -- lo que la UI necesita para describir el ajuste (o su ausencia) antes de pedir confirmación. */
export interface ImportAdjustment {
  /** `true` si las dimensiones coinciden exactamente -- ningún crop/pad involucrado. */
  matches: boolean
  /** Píxeles de excedente descartados en cada eje (0 si no aplica). */
  cropWidth: number
  cropHeight: number
  /** Píxeles de margen transparente agregados en cada eje (0 si no aplica). */
  padWidth: number
  padHeight: number
}

/** Compara dimensiones de origen contra destino -- pura, sin tocar píxeles. Nunca produce un resultado de "escalar": por construcción, un eje o bien se recorta, o bien se rellena, o coincide -- jamás las tres cosas a la vez ni ninguna otra categoría. */
export function describeImportAdjustment(sourceWidth: number, sourceHeight: number, targetWidth: number, targetHeight: number): ImportAdjustment {
  const cropWidth = Math.max(0, sourceWidth - targetWidth)
  const cropHeight = Math.max(0, sourceHeight - targetHeight)
  const padWidth = Math.max(0, targetWidth - sourceWidth)
  const padHeight = Math.max(0, targetHeight - sourceHeight)
  return {
    matches: cropWidth === 0 && cropHeight === 0 && padWidth === 0 && padHeight === 0,
    cropWidth,
    cropHeight,
    padWidth,
    padHeight,
  }
}
