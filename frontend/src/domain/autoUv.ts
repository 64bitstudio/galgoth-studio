/**
 * AutoUv -- implementación TS, espejo exacto de
 * `backend/.../domain/uv/AlphaAutoPackStrategy.java` (ticket 006/007).
 * Feedback inmediato en el editor manual: cada `Command` de crear/redimensionar
 * un cuboid llama a `layoutUv` de forma síncrona, sin ninguna petición HTTP
 * -- el backend sigue siendo la autoridad canónica (recomputa en Guardar/
 * Apply/export, ver `docs/definiciones/galgoth-studio-mvp.md` Diseño técnico §6).
 *
 * Desenvolvimiento de caja estándar de Minecraft (fórmula verificada contra
 * el código fuente real de Blockbench, `JannisX11/blockbench` -- no
 * inventada, ver Hecho del ticket 006) + shelf-packing determinista sobre
 * el atlas: misma fórmula, mismo orden de empaquetado que el lado Java --
 * verificado por fixture compartida (`contracts/fixtures/uv-layout-fixture.json`,
 * ticket 007).
 */
import type { Cuboid, CuboidFaces, Face, FaceName, UvRegion } from './MobProjectModel'

/** Único atlas de textura que existe este ciclo -- ver Face.texture. */
const SINGLE_TEXTURE_INDEX = 0

export interface UvLayoutResult {
  cuboids: Cuboid[]
  regions: UvRegion[]
}

/** Espejo de `UvAtlasOverflowException` (Java) -- mismos 4 campos, mismo significado. */
export class UvAtlasOverflowError extends Error {
  readonly currentWidth: number
  readonly currentHeight: number
  readonly requiredWidth: number
  readonly requiredHeight: number

  constructor(currentWidth: number, currentHeight: number, requiredWidth: number, requiredHeight: number) {
    super(
      `UV_ATLAS_OVERFLOW: el atlas actual (${currentWidth}x${currentHeight}) no alcanza para el modelo -- se requieren al menos ${requiredWidth}x${requiredHeight}`,
    )
    this.name = 'UvAtlasOverflowError'
    this.currentWidth = currentWidth
    this.currentHeight = currentHeight
    this.requiredWidth = requiredWidth
    this.requiredHeight = requiredHeight
  }
}

interface Footprint {
  width: number
  height: number
}

interface Placement {
  x: number
  y: number
}

interface PackResult {
  placements: Placement[]
  totalHeight: number
  maxRowWidth: number
}

/**
 * Tamaño de un eje YA EN TÉXELS. Escala ANTES de redondear, igual que
 * `BoxUvMath.scaledAxis` del backend (ticket 118) -- si se redondeara
 * primero, la densidad no podría recuperar lo que el redondeo destruyó.
 * A `texelsPerUnit = 1` el resultado es idéntico al de siempre.
 */
function scaledAxis(from: number, to: number, texelsPerUnit: number): number {
  return Math.round(Math.abs(to - from) * texelsPerUnit)
}

function footprintOf(cuboid: Cuboid, texelsPerUnit: number): Footprint {
  const x = scaledAxis(cuboid.from[0], cuboid.to[0], texelsPerUnit)
  const y = scaledAxis(cuboid.from[1], cuboid.to[1], texelsPerUnit)
  const z = scaledAxis(cuboid.from[2], cuboid.to[2], texelsPerUnit)
  return { width: 2 * (x + z), height: z + y }
}

/** Densidades que el backend puede haber usado (`TexelDensity`). Inferir cualquier otro valor sería inventar. */
const DENSIDADES_POSIBLES = [1, 2, 4]

/**
 * Deduce la densidad de téxel del layout que YA tiene el modelo, comparando
 * el ancho real de una cara contra el tamaño del cuboid en unidades --
 * ticket 119.
 *
 * <p>Existe porque el modelo no transporta su densidad: `TextureDensity` es
 * un parámetro de generación del backend y no se persiste en
 * `MobProjectModel`. Sin esto, cualquier recálculo de UV en el editor
 * rehace el atlas a X1 y desalinea la textura ya pintada de un mob generado
 * a X4.
 *
 * <p>Se toma la densidad más frecuente entre los cuboids medibles, no la
 * del primero: una sola cara degenerada o un cuboid raro no puede decidir
 * por todo el modelo. Sin cuboids medibles devuelve 1, que es exactamente
 * el comportamiento anterior a este ticket.
 */
export function inferTexelsPerUnit(cuboids: Cuboid[]): number {
  const votos = new Map<number, number>()
  for (const cuboid of cuboids) {
    const anchoUnidades = Math.abs(cuboid.to[0] - cuboid.from[0])
    const rect = cuboid.faces?.north?.uv
    if (!rect || anchoUnidades === 0) {
      continue
    }
    const anchoTexels = rect[2] - rect[0]
    if (anchoTexels <= 0) {
      continue
    }
    const candidata = anchoTexels / anchoUnidades
    const cercana = DENSIDADES_POSIBLES.find((d) => Math.abs(candidata - d) < 0.01)
    if (cercana) {
      votos.set(cercana, (votos.get(cercana) ?? 0) + 1)
    }
  }
  let mejor = 1
  let maxVotos = 0
  for (const [densidad, n] of votos) {
    if (n > maxVotos) {
      mejor = densidad
      maxVotos = n
    }
  }
  return mejor
}

/** Shelf-packing sin límite de alto -- envuelve de fila cuando se excede `width`. */
function packWithinWidth(footprints: Footprint[], width: number): PackResult {
  const placements: Placement[] = []
  let cursorX = 0
  let cursorY = 0
  let rowHeight = 0
  let maxRowWidth = 0

  for (const footprint of footprints) {
    if (cursorX > 0 && cursorX + footprint.width > width) {
      maxRowWidth = Math.max(maxRowWidth, cursorX)
      cursorX = 0
      cursorY += rowHeight
      rowHeight = 0
    }
    placements.push({ x: cursorX, y: cursorY })
    cursorX += footprint.width
    rowHeight = Math.max(rowHeight, footprint.height)
  }
  maxRowWidth = Math.max(maxRowWidth, cursorX)

  return { placements, totalHeight: cursorY + rowHeight, maxRowWidth }
}

function faceAt(u0: number, v0: number, width: number, height: number): Face {
  return { uv: [u0, v0, u0 + width, v0 + height], texture: SINGLE_TEXTURE_INDEX }
}

function boxUnwrapFaces(cuboid: Cuboid, offsetX: number, offsetY: number, texelsPerUnit: number): CuboidFaces {
  const x = scaledAxis(cuboid.from[0], cuboid.to[0], texelsPerUnit)
  const y = scaledAxis(cuboid.from[1], cuboid.to[1], texelsPerUnit)
  const z = scaledAxis(cuboid.from[2], cuboid.to[2], texelsPerUnit)

  return {
    up: faceAt(offsetX + z, offsetY, x, z),
    down: faceAt(offsetX + z + x, offsetY, x, z),
    west: faceAt(offsetX, offsetY + z, z, y),
    north: faceAt(offsetX + z, offsetY + z, x, y),
    east: faceAt(offsetX + z + x, offsetY + z, z, y),
    south: faceAt(offsetX + 2 * z + x, offsetY + z, x, y),
  }
}

const FACE_NAMES: FaceName[] = ['north', 'south', 'east', 'west', 'up', 'down']

/**
 * Recalcula la UV de las 6 caras de CADA cuboid de la lista (función pura y
 * determinista del conjunto completo de entrada, en el mismo orden que
 * `cuboids` -- no un parche incremental sobre el layout anterior).
 *
 * @throws {UvAtlasOverflowError} si el conjunto no cabe en `textureWidth`x`textureHeight`
 *         -- el atlas nunca crece en silencio.
 */
export function layoutUv(
  cuboids: Cuboid[],
  textureWidth: number,
  textureHeight: number,
  texelsPerUnit: number = 1,
): UvLayoutResult {
  const footprints = cuboids.map((cuboid) => footprintOf(cuboid, texelsPerUnit))

  const attempt = packWithinWidth(footprints, textureWidth)
  if (attempt.totalHeight > textureHeight || attempt.maxRowWidth > textureWidth) {
    const requiredWidth = Math.max(textureWidth, ...footprints.map((f) => f.width))
    const fallback = packWithinWidth(footprints, requiredWidth)
    const requiredHeight = Math.max(textureHeight, fallback.totalHeight)
    throw new UvAtlasOverflowError(textureWidth, textureHeight, requiredWidth, requiredHeight)
  }

  const updatedCuboids: Cuboid[] = []
  const regions: UvRegion[] = []
  cuboids.forEach((cuboid, i) => {
    const placement = attempt.placements[i]!
    const faces = boxUnwrapFaces(cuboid, placement.x, placement.y, texelsPerUnit)
    updatedCuboids.push({ ...cuboid, faces })
    for (const faceName of FACE_NAMES) {
      // status: 'unpainted' -- AutoUv siempre recomputa desde cero (Fase 1+2,
      // sin cambios este ticket), nunca preserva contenido pintado; mismo
      // default que el constructor de conveniencia de UvRegion.java (ticket 040).
      regions.push({ cuboidId: cuboid.id, face: faceName, rect: faces[faceName].uv, status: 'unpainted' })
    }
  })

  return { cuboids: updatedCuboids, regions }
}
