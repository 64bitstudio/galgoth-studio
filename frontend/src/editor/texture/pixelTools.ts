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
 * directo sobre el `Uint8ClampedArray` del atlas, esto es completamente
 * testable sin DOM.
 */

export type RgbaColor = readonly [number, number, number, number]

export const TRANSPARENT: RgbaColor = [0, 0, 0, 0]

/** `pixels`/`width`/`height` viajan juntos en todas las funciones de este módulo (S107: agrupar en vez de 3 parámetros sueltos repetidos). */
export interface AtlasBuffer {
  pixels: Uint8ClampedArray
  width: number
  height: number
}

export interface PixelPoint {
  x: number
  y: number
}

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

/** Bounds vacío -- ver `boundsToRect` para el caso "no se tocó ningún píxel" (ej. el trazo completo cayó fuera del atlas). */
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

export function getPixelColor(atlas: AtlasBuffer, x: number, y: number): RgbaColor {
  const i = (y * atlas.width + x) * 4
  return [atlas.pixels[i]!, atlas.pixels[i + 1]!, atlas.pixels[i + 2]!, atlas.pixels[i + 3]!]
}

function setPixelColor(atlas: AtlasBuffer, x: number, y: number, color: RgbaColor): void {
  const i = (y * atlas.width + x) * 4
  atlas.pixels[i] = color[0]
  atlas.pixels[i + 1] = color[1]
  atlas.pixels[i + 2] = color[2]
  atlas.pixels[i + 3] = color[3]
}

function colorsEqual(a: RgbaColor, b: RgbaColor): boolean {
  return a[0] === b[0] && a[1] === b[1] && a[2] === b[2] && a[3] === b[3]
}

/**
 * Pinta un cuadrado de `sizePx` × `sizePx` píxeles del ATLAS (nunca de
 * pantalla -- el caller convierte coordenadas de pantalla a atlas ANTES
 * de llamar acá, ver `TextureCanvas.vue`), centrado en `center`,
 * recortado a los límites de `atlas`. Sin antialiasing: cada píxel
 * tocado recibe `color` exacto, sin mezcla. Comparte esta función Pincel
 * y Borrador (`color` es el único parámetro que cambia -- Borrador
 * siempre pasa `TRANSPARENT`).
 */
export function stampSquare(atlas: AtlasBuffer, center: PixelPoint, sizePx: number, color: RgbaColor, bounds: PixelBounds): void {
  const half = Math.floor((sizePx - 1) / 2)
  const startX = center.x - half
  const startY = center.y - half
  for (let dy = 0; dy < sizePx; dy += 1) {
    const y = startY + dy
    if (y < 0 || y >= atlas.height) {
      continue
    }
    for (let dx = 0; dx < sizePx; dx += 1) {
      const x = startX + dx
      if (x < 0 || x >= atlas.width) {
        continue
      }
      setPixelColor(atlas, x, y, color)
      extendBounds(bounds, x, y)
    }
  }
}

/**
 * Conecta `from` a `to` con estampas de `stampSquare` a lo largo de una
 * línea de Bresenham -- sin esto, un trazo rápido (pocos eventos
 * `pointermove` entre puntos lejanos) dejaría huecos en vez de un trazo
 * continuo.
 */
export function stampLine(
  atlas: AtlasBuffer,
  from: PixelPoint,
  to: PixelPoint,
  sizePx: number,
  color: RgbaColor,
  bounds: PixelBounds,
): void {
  let x = from.x
  let y = from.y
  const dx = Math.abs(to.x - from.x)
  const sx = from.x < to.x ? 1 : -1
  const dy = -Math.abs(to.y - from.y)
  const sy = from.y < to.y ? 1 : -1
  let err = dx + dy

  // Bresenham real: termina al llegar exactamente a (to.x,to.y), no es un bucle infinito.
  for (;;) {
    stampSquare(atlas, { x, y }, sizePx, color, bounds)
    if (x === to.x && y === to.y) {
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

function neighborsOf(x: number, y: number): PixelPoint[] {
  return [
    { x: x + 1, y },
    { x: x - 1, y },
    { x, y: y + 1 },
    { x, y: y - 1 },
  ]
}

/** BFS del flood-fill: expande `stack`/`visited` en el lugar, extiende `bounds`, acumula `touched` -- extraído de `computeFloodFill` para mantener su complejidad cognitiva bajo control (S3776). */
function floodFillWalk(atlas: AtlasBuffer, start: PixelPoint, targetColor: RgbaColor, bounds: PixelBounds): number[] {
  const visited = new Uint8Array(atlas.width * atlas.height)
  const stack: number[] = [start.y * atlas.width + start.x]
  visited[start.y * atlas.width + start.x] = 1
  const touched: number[] = []

  while (stack.length > 0) {
    const index = stack.pop()!
    const x = index % atlas.width
    const y = Math.floor(index / atlas.width)
    touched.push(index)
    extendBounds(bounds, x, y)

    for (const n of neighborsOf(x, y)) {
      if (n.x < 0 || n.y < 0 || n.x >= atlas.width || n.y >= atlas.height) {
        continue
      }
      const nIndex = n.y * atlas.width + n.x
      if (visited[nIndex] || !colorsEqual(getPixelColor(atlas, n.x, n.y), targetColor)) {
        continue
      }
      visited[nIndex] = 1
      stack.push(nIndex)
    }
  }
  return touched
}

/** Recorta `atlas.pixels` a `rect` en un buffer nuevo -- `beforePixels` de `computeFloodFill`. */
function readRect(atlas: AtlasBuffer, rect: IntRect): Uint8ClampedArray {
  const out = new Uint8ClampedArray(rect.width * rect.height * 4)
  for (let row = 0; row < rect.height; row += 1) {
    const srcStart = ((rect.y + row) * atlas.width + rect.x) * 4
    out.set(atlas.pixels.subarray(srcStart, srcStart + rect.width * 4), row * rect.width * 4)
  }
  return out
}

/**
 * Flood-fill estándar (4-direccional, BFS sobre pila explícita) de la
 * región de color contiguo que contiene `start`. NUNCA muta
 * `atlas.pixels` (función pura de lectura) -- devuelve `beforePixels`/
 * `afterPixels` acotados al bounding box de la región tocada, listos
 * para `textureEditorStore.recordPatch()`. `null` si el punto de partida
 * cae fuera del atlas o si `fillColor` ya es el color vigente (no-op
 * explícito -- nunca se registra un Command vacío).
 */
export function computeFloodFill(atlas: AtlasBuffer, start: PixelPoint, fillColor: RgbaColor): FloodFillResult | null {
  if (start.x < 0 || start.y < 0 || start.x >= atlas.width || start.y >= atlas.height) {
    return null
  }
  const targetColor = getPixelColor(atlas, start.x, start.y)
  if (colorsEqual(targetColor, fillColor)) {
    return null
  }

  const bounds = createEmptyBounds()
  const touched = floodFillWalk(atlas, start, targetColor, bounds)

  const rect = boundsToRect(bounds)!
  const beforePixels = readRect(atlas, rect)
  const afterPixels = beforePixels.slice()
  const afterAtlas: AtlasBuffer = { pixels: afterPixels, width: rect.width, height: rect.height }
  for (const index of touched) {
    const x = (index % atlas.width) - rect.x
    const y = Math.floor(index / atlas.width) - rect.y
    setPixelColor(afterAtlas, x, y, fillColor)
  }

  return { rect, beforePixels, afterPixels }
}

/** Eyedropper: color exacto del píxel clickeado -- `null` si cae fuera del atlas. */
export function pickColorAt(atlas: AtlasBuffer, x: number, y: number): RgbaColor | null {
  if (x < 0 || y < 0 || x >= atlas.width || y >= atlas.height) {
    return null
  }
  return getPixelColor(atlas, x, y)
}
