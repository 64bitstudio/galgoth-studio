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

function boxSizeAxis(from: number, to: number): number {
  return Math.round(Math.abs(to - from))
}

function footprintOf(cuboid: Cuboid): Footprint {
  const x = boxSizeAxis(cuboid.from[0], cuboid.to[0])
  const y = boxSizeAxis(cuboid.from[1], cuboid.to[1])
  const z = boxSizeAxis(cuboid.from[2], cuboid.to[2])
  return { width: 2 * (x + z), height: z + y }
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

function boxUnwrapFaces(cuboid: Cuboid, offsetX: number, offsetY: number): CuboidFaces {
  const x = boxSizeAxis(cuboid.from[0], cuboid.to[0])
  const y = boxSizeAxis(cuboid.from[1], cuboid.to[1])
  const z = boxSizeAxis(cuboid.from[2], cuboid.to[2])

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
export function layoutUv(cuboids: Cuboid[], textureWidth: number, textureHeight: number): UvLayoutResult {
  const footprints = cuboids.map(footprintOf)

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
    const faces = boxUnwrapFaces(cuboid, placement.x, placement.y)
    updatedCuboids.push({ ...cuboid, faces })
    for (const faceName of FACE_NAMES) {
      regions.push({ cuboidId: cuboid.id, face: faceName, rect: faces[faceName].uv })
    }
  })

  return { cuboids: updatedCuboids, regions }
}
