/**
 * Ticket 047 -- manipulación de píxeles PURA (sin `<canvas>`, sin DOM),
 * compartida por Pincel/Borrador/Cubeta/Eyedropper (`TextureCanvas.vue`).
 *
 * Deliberadamente NO usa la API 2D de canvas (`ctx.fillRect`/`drawImage`)
 * para pintar: (a) el AC de "pixel-perfect, sin antialiasing" es más
 * fácil de garantizar escribiendo bytes RGBA directo que confiando en
 * que el navegador nunca suavice un `fillRect` (que si puede pasar con
 * transformaciones/escala no enteras); (b) jsdom (entorno de test) no
 * implementa un contexto 2D real (`getContext('2d')` devuelve `null` sin
 * el paquete nativo `canvas`, no instalado en este repo -- ver comentario
 * de `ThreeViewportService.spec.ts` sobre el mismo límite para WebGL) --
 * si la lógica de pintado dependiera de canvas real, sería imposible
 * escribir un test real de "el trazo pintó el color exacto". Escribiendo
 * directo sobre el `Uint8ClampedArray` del atlas, todo esto es 100%
 * testable sin DOM.
 */

export type RgbaColor = readonly [number, number, number, number]

export const TRANSPARENT: RgbaColor = [0, 0, 0, 0]

export interface PixelBounds {
  minX: number
  minY: number
  maxX: number
  maxY: number
}

export interface IntRect {
  x: number
  y: number
  width: number
  height: number
}

/** Bounds vacío -- ver `boundsToRect` para el caso "no se tocó ningún píxel" (ej. todo el trazo cayó fuera del atlas). */
export function createEmptyBounds(): PixelBounds {
  return { minX: Infinity, minY: Infinity, maxX: -Infinity, maxY: -Infinity }
}

function extendBounds(bounds: PixelBounds, x: number, y: number): void {
  bounds.minX = Math.min(bounds.minX, x)
  bounds.minY = Math.min(bounds.minY, y)
  bounds.maxX = Math.max(bounds.maxX, x)
  bounds.maxY = Math.max(bounds.maxY, y)
}

/** `null` si `bounds` sigue vacío (nunca se extendió) -- nunca se debe llamar `recordPatch` en ese caso. */
export function boundsToRect(bounds: PixelBounds): IntRect | null {
  if (bounds.minX > bounds.maxX || bounds.minY > bounds.maxY) {
    return null
  }
  return { x: bounds.minX, y: bounds.minY, width: bounds.maxX - bounds.minX + 1, height: bounds.maxY - bounds.minY + 1 }
}

export function getPixelColor(pixels: Uint8ClampedArray, width: number, x: number, y: number): RgbaColor {
  const i = (y * width + x) * 4
  return [pixels[i]!, pixels[i + 1]!, pixels[i + 2]!, pixels[i + 3]!]
}

function setPixelColor(pixels: Uint8ClampedArray, width: number, x: number, y: number, color: RgbaColor): void {
  const i = (y * width + x) * 4
  pixels[i] = color[0]
  pixels[i + 1] = color[1]
  pixels[i + 2] = color[2]
  pixels[i + 3] = color[3]
}

function colorsEqual(a: RgbaColor, b: RgbaColor): boolean {
  return a[0] === b[0] && a[1] === b[1] && a[2] === b[2] && a[3] === b[3]
}

/**
 * Pinta un cuadrado de `sizePx` × `sizePx` píxeles del ATLAS (nunca de
 * pantalla -- el caller convierte coordenadas de pantalla a atlas ANTES
 * de llamar acá, ver `TextureCanvas.vue`), centrado en `(centerX, centerY)`,
 * recortado a los límites de `width`/`height`. Sin antialiasing: cada
 * píxel tocado recibe `color` exacto, sin mezcla. Comparte esta función
 * Pincel y Borrador (`color` es el único parámetro que cambia --
 * Borrador siempre pasa `TRANSPARENT`).
 */
export function stampSquare(
  pixels: Uint8ClampedArray,
  width: number,
  height: number,
  centerX: number,
  centerY: number,
  sizePx: number,
  color: RgbaColor,
  bounds: PixelBounds,
): void {
  const half = Math.floor((sizePx - 1) / 2)
  const startX = centerX - half
  const startY = centerY - half
  for (let dy = 0; dy < sizePx; dy += 1) {
    const y = startY + dy
    if (y < 0 || y >= height) {
      continue
    }
    for (let dx = 0; dx < sizePx; dx += 1) {
      const x = startX + dx
      if (x < 0 || x >= width) {
        continue
      }
      setPixelColor(pixels, width, x, y, color)
      extendBounds(bounds, x, y)
    }
  }
}

/**
 * Conecta `(x0,y0)` a `(x1,y1)` con estampas de `stampSquare` a lo largo
 * de una línea de Bresenham -- sin esto, un trazo rápido (pocos eventos
 * `pointermove` entre puntos lejanos) dejaría huecos en vez de un trazo
 * continuo.
 */
export function stampLine(
  pixels: Uint8ClampedArray,
  width: number,
  height: number,
  x0: number,
  y0: number,
  x1: number,
  y1: number,
  sizePx: number,
  color: RgbaColor,
  bounds: PixelBounds,
): void {
  let x = x0
  let y = y0
  const dx = Math.abs(x1 - x0)
  const sx = x0 < x1 ? 1 : -1
  const dy = -Math.abs(y1 - y0)
  const sy = y0 < y1 ? 1 : -1
  let err = dx + dy

  // Bresenham real: termina al llegar exactamente a (x1,y1), no es un bucle infinito.
  for (;;) {
    stampSquare(pixels, width, height, x, y, sizePx, color, bounds)
    if (x === x1 && y === y1) {
      break
    }
    const e2 = 2 * err
    if (e2 >= dy) {
      err += dy
      x += sx
    }
    if (e2 <= dx) {
      err += dx
      y += sy
    }
  }
}

export interface FloodFillResult {
  rect: IntRect
  beforePixels: Uint8ClampedArray
  afterPixels: Uint8ClampedArray
}

/**
 * Flood-fill estándar (4-direccional, BFS sobre pila explícita) de la
 * región de color contiguo que contiene `(startX, startY)`. NUNCA muta
 * `pixels` (función pura de lectura) -- devuelve `beforePixels`/
 * `afterPixels` acotados al bounding box de la región tocada, listos
 * para `textureEditorStore.recordPatch()`. `null` si el punto de partida
 * cae fuera del atlas o si `fillColor` ya es el color vigente (no-op
 * explícito -- nunca se registra un Command vacío).
 */
export function computeFloodFill(
  pixels: Uint8ClampedArray,
  width: number,
  height: number,
  startX: number,
  startY: number,
  fillColor: RgbaColor,
): FloodFillResult | null {
  if (startX < 0 || startY < 0 || startX >= width || startY >= height) {
    return null
  }
  const targetColor = getPixelColor(pixels, width, startX, startY)
  if (colorsEqual(targetColor, fillColor)) {
    return null
  }

  const visited = new Uint8Array(width * height)
  const stack: number[] = [startY * width + startX]
  visited[startY * width + startX] = 1
  const touched: number[] = []
  const bounds = createEmptyBounds()

  while (stack.length > 0) {
    const index = stack.pop()!
    const x = index % width
    const y = Math.floor(index / width)
    touched.push(index)
    extendBounds(bounds, x, y)

    const neighbors: Array<[number, number]> = [
      [x + 1, y],
      [x - 1, y],
      [x, y + 1],
      [x, y - 1],
    ]
    for (const [nx, ny] of neighbors) {
      if (nx < 0 || ny < 0 || nx >= width || ny >= height) {
        continue
      }
      const nIndex = ny * width + nx
      if (visited[nIndex]) {
        continue
      }
      if (!colorsEqual(getPixelColor(pixels, width, nx, ny), targetColor)) {
        continue
      }
      visited[nIndex] = 1
      stack.push(nIndex)
    }
  }

  const rect = boundsToRect(bounds)!
  const beforePixels = new Uint8ClampedArray(rect.width * rect.height * 4)
  for (let row = 0; row < rect.height; row += 1) {
    const srcStart = ((rect.y + row) * width + rect.x) * 4
    beforePixels.set(pixels.subarray(srcStart, srcStart + rect.width * 4), row * rect.width * 4)
  }
  const afterPixels = beforePixels.slice()
  for (const index of touched) {
    const x = (index % width) - rect.x
    const y = Math.floor(index / width) - rect.y
    setPixelColor(afterPixels, rect.width, x, y, fillColor)
  }

  return { rect, beforePixels, afterPixels }
}

/** Eyedropper: color exacto del píxel clickeado -- `null` si cae fuera del atlas. */
export function pickColorAt(pixels: Uint8ClampedArray, width: number, height: number, x: number, y: number): RgbaColor | null {
  if (x < 0 || y < 0 || x >= width || y >= height) {
    return null
  }
  return getPixelColor(pixels, width, x, y)
}
