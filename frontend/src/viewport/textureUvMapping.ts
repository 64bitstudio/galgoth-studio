/**
 * Ticket 047 -- mapea la UV real de cada cara de un cuboid
 * (`Cuboid.faces[x].uv`, ya calculada por AutoUv) sobre el atributo `uv`
 * de la `BoxGeometry` de Three.js que ya construye `buildMobScene.ts`,
 * para que el preview 3D (HU-26) muestre el atlas pintado en el lugar
 * correcto de cada cara, no un color plano.
 *
 * Esta función solo reescribe las coordenadas UV de los vértices YA
 * existentes con la textura del atlas (`map`) -- el etiquetado de
 * `materialIndex`/grupos de material para el picking determinista por
 * cara (`mesh.userData.faceNamesByGroup`, `FACE_LOCAL_NORMALS` más abajo)
 * es HU-25/ticket 049, sin relación con el remapeo de UV de esta función.
 *
 * Orden de caras de `THREE.BoxGeometry` (código fuente real de Three.js,
 * `buildPlane` llamado 6 veces): +x, -x, +y, -y, +z, -z. Mapeo a
 * `FaceName` según la convención Minecraft/Blockbench ya confirmada en
 * `docs/adr/0001-coordinate-system-contract.md` (east=+x, west=-x,
 * up=+y, down=-y, south=+z, north=-z).
 *
 * Ticket 049 (HU-25, Diseño técnico §14): este mismo `BOX_GEOMETRY_FACE_ORDER`
 * es ahora también la única fuente de verdad de `mesh.userData.faceNamesByGroup`
 * (`buildMobScene.ts`) -- el índice de grupo de material real (`materialIndex`,
 * asignado por Three.js durante el raycasting, ver `ThreeViewportService.pickCuboidFaceAt`)
 * indexa este MISMO array, nunca un segundo mapeo de orientación.
 */
import type { BufferAttribute } from 'three'
import type { CuboidFaces, FaceName } from '../domain/MobProjectModel'

export const BOX_GEOMETRY_FACE_ORDER: FaceName[] = ['east', 'west', 'up', 'down', 'south', 'north']

/** Referencia a una cara concreta de un cuboid -- tipo compartido entre `buildMobScene.ts` (highlight en el preview 3D), `ThreeViewportService.pickCuboidFaceAt` y `textureSelectionStore.ts` (ticket 049). */
export interface CuboidFaceRef {
  cuboidId: string
  face: FaceName
}

/**
 * Normal LOCAL (espacio de OBJETO, sin aplicar la rotación/`matrixWorld`
 * del mesh) de cada cara de una `BoxGeometry` sin segmentos adicionales.
 *
 * Ticket 049 -- verificado contra el código fuente real de Three.js
 * (`Mesh.js`, `_computeIntersections`/`checkGeometryIntersection`): los
 * vértices que alimentan `Triangle.getNormal(...)` vienen de
 * `geometry.attributes.position` SIN aplicar `matrixWorld` (el rayo se
 * transforma al espacio local del mesh antes del test, no al revés) --
 * `intersection.face.normal` es SIEMPRE local, incluso si el mesh está
 * rotado en el mundo. Por eso la validación dev-only de
 * `pickCuboidFaceAt` compara directo contra esta tabla, sin componer
 * ninguna rotación del mesh (una composición así produciría falsos
 * positivos de discrepancia para cualquier cuboid rotado).
 */
export const FACE_LOCAL_NORMALS: Record<FaceName, readonly [number, number, number]> = {
  east: [1, 0, 0],
  west: [-1, 0, 0],
  up: [0, 1, 0],
  down: [0, -1, 0],
  south: [0, 0, 1],
  north: [0, 0, -1],
}

/**
 * UV por defecto de CADA cara de una `BoxGeometry` sin segmentos
 * adicionales (4 vértices, orden fijo de `buildPlane`): `(0,1)`, `(1,1)`,
 * `(0,0)`, `(1,0)`. Cada componente ("0" o "1") se sustituye por el
 * borde correspondiente del rect real (`u0`/`u1`, `v0`/`v1`) -- una
 * sustitución lineal simple, no una reinterpretación del winding de la
 * geometría.
 */
const DEFAULT_FACE_CORNERS: ReadonlyArray<readonly [0 | 1, 0 | 1]> = [
  [0, 1],
  [1, 1],
  [0, 0],
  [1, 0],
]

interface NormalizedFaceUv {
  u0: number
  v0: number
  u1: number
  v1: number
}

/**
 * Pixel-space (`Face.uv`, `[x0,y0,x1,y1]` en píxeles del atlas, origen
 * arriba-izquierda) a UV normalizada 0..1. Sin invertir el eje V a mano:
 * la textura se construye como `THREE.DataTexture` con `flipY = false`
 * (ver `buildMobScene.ts`) precisamente para que la fila 0 del buffer
 * (arriba en pixel-space) corresponda a v=0 sin ninguna conversión
 * adicional acá.
 */
function normalizeFaceUv(rect: readonly [number, number, number, number], atlasWidth: number, atlasHeight: number): NormalizedFaceUv {
  const [x0, y0, x1, y1] = rect
  return { u0: x0 / atlasWidth, v0: y0 / atlasHeight, u1: x1 / atlasWidth, v1: y1 / atlasHeight }
}

/**
 * Reescribe el atributo `uv` de `geometry` (una `BoxGeometry` recién
 * creada, sin segmentos extra -- 24 vértices, 4 por cara) para que cada
 * cara muestre exactamente el rect de `faces[faceName].uv` del atlas.
 * Marca `uvAttribute.needsUpdate = true`.
 */
export function applyCuboidFaceUvs(uvAttribute: BufferAttribute, faces: CuboidFaces, atlasWidth: number, atlasHeight: number): void {
  BOX_GEOMETRY_FACE_ORDER.forEach((faceName, faceIndex) => {
    const { u0, v0, u1, v1 } = normalizeFaceUv(faces[faceName].uv, atlasWidth, atlasHeight)
    DEFAULT_FACE_CORNERS.forEach(([du, dv], cornerIndex) => {
      const vertexIndex = faceIndex * 4 + cornerIndex
      uvAttribute.setXY(vertexIndex, du === 0 ? u0 : u1, dv === 0 ? v0 : v1)
    })
  })
  uvAttribute.needsUpdate = true
}
