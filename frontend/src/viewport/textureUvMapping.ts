/**
 * Ticket 047 -- mapea la UV real de cada cara de un cuboid
 * (`Cuboid.faces[x].uv`, ya calculada por AutoUv) sobre el atributo `uv`
 * de la `BoxGeometry` de Three.js que ya construye `buildMobScene.ts`,
 * para que el preview 3D (HU-26) muestre el atlas pintado en el lugar
 * correcto de cada cara, no un color plano.
 *
 * Deliberadamente NO toca `materialIndex`/grupos de material de la
 * geometría (eso es HU-25/ticket 049, "etiquetado de cada cara/grupo de
 * material... en su construcción (`buildCuboidMesh`)", pendiente y fuera
 * de alcance de este ticket -- face picking/selección cruzada). Esta
 * función solo reescribe las coordenadas UV de los vértices YA
 * existentes con un único material compartido (`map` = la textura del
 * atlas) -- cero relación con cómo 049 resolverá el picking por cara.
 *
 * Orden de caras de `THREE.BoxGeometry` (código fuente real de Three.js,
 * `buildPlane` llamado 6 veces): +x, -x, +y, -y, +z, -z. Mapeo a
 * `FaceName` según la convención Minecraft/Blockbench ya confirmada en
 * `docs/adr/0001-coordinate-system-contract.md` (east=+x, west=-x,
 * up=+y, down=-y, south=+z, north=-z).
 */
import type { BufferAttribute } from 'three'
import type { CuboidFaces, FaceName } from '../domain/MobProjectModel'

export const BOX_GEOMETRY_FACE_ORDER: FaceName[] = ['east', 'west', 'up', 'down', 'south', 'north']

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
