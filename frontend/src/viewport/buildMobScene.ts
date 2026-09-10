/**
 * Construye una escena Three.js plana (sin anidar Object3D por bone --
 * ver nota de diseño abajo) a partir de un MobProjectModel, usando
 * EXCLUSIVAMENTE `coordinateSystem.ts` (CoordinateSystemContract) para
 * cualquier transformación de punto/orientación. Ticket 008.
 *
 * Nota de diseño: `from`/`to`/`origin` de bones y cuboids viven todos en
 * un mismo espacio "plano" (igual que Minecraft/Blockbench -- ver
 * samples/carcomido_minecraft_cuboids.bbmodel), no en el espacio LOCAL
 * de su bone padre. Por eso este módulo NO anida Object3D replicando la
 * jerarquía de bones (el anidamiento nativo de Three.js compone
 * transformaciones locales, que no es la semántica de este contrato) --
 * en vez de eso, para cada cuboid recorre la cadena de bones ancestros y
 * compone las rotaciones con `applyPivotRotation`, exactamente como
 * `composeBoneChildTransform` generalizado a N niveles (aplica primero
 * la rotación propia del cuboid sobre su origen, y sobre ESE resultado
 * aplica cada bone ancestro, de más cercano a más lejano, sobre su
 * propio pivote -- nunca sobre las coordenadas originales sin acumular).
 *
 * Ticket 047 (HU-26): `atlasTexture` opcional -- si se pasa, cada cuboid
 * se pinta con la textura del atlas real (UV mapeada cara por cara vía
 * `applyCuboidFaceUvs`, `textureUvMapping.ts`) en vez del gris plano
 * (`CUBOID_COLOR`). Sin este parámetro (todos los callers existentes:
 * `ThreeViewport.vue`/`GenerationPreviewViewport.vue`, tab Modelo) el
 * comportamiento es EXACTAMENTE el mismo de antes -- aditivo, no rompe
 * ningún caller ya mergeado.
 *
 * Ticket 049 (HU-25, Diseño técnico §14): dos adiciones a `buildCuboidMesh`,
 * ninguna cambia `buildMobGroup` ni cómo compone la escena (un mesh por
 * cuboid, un marcador por bone, igual que siempre):
 * 1. `mesh.userData.faceNamesByGroup = BOX_GEOMETRY_FACE_ORDER` -- SIEMPRE,
 *    etiquetado determinista una sola vez en la construcción, misma fuente
 *    de verdad de orientación que ya usa `applyCuboidFaceUvs`.
 * 2. El material deja de ser una única instancia y pasa a un array de 6
 *    slots con la MISMA instancia repetida -- necesario para que Three.js
 *    resuelva `intersection.face.materialIndex` real durante el raycasting
 *    (`ThreeViewportService.pickCuboidFaceAt`): con un material único
 *    (no-array), `Mesh._computeIntersections` (código fuente de Three.js)
 *    NUNCA copia `group.materialIndex` al resultado -- lo deja siempre en
 *    0, lo cual habría vuelto inútil el picking determinista por grupo que
 *    exige este ticket. Repetir la misma instancia 6 veces no cambia el
 *    render (cada grupo se sigue pintando exactamente con el mismo
 *    material que antes) -- rompe, eso sí, cualquier código que asuma
 *    `mesh.material` como un objeto único en vez de un array de 6 (ver
 *    tests actualizados de este ticket: `buildMobScene.spec.ts`,
 *    `ThreeViewportService.spec.ts`, `TextureCanvas.spec.ts`).
 *
 * `selectedFace` (nuevo 4to parámetro de `buildMobGroup`, HU-25 AC "la cara
 * correspondiente se resalta en el preview 3D"): si el cuboid del mesh
 * coincide, agrega un highlight (`LineLoop` sobre el perímetro real de esa
 * cara, mismo color que `SELECTION_OUTLINE_COLOR`) como hijo del mesh --
 * mismo patrón ya usado para `SELECTION_OUTLINE_NAME` (hereda el transform
 * automáticamente).
 */
import {
  BoxGeometry,
  BufferAttribute,
  BufferGeometry,
  EdgesGeometry,
  Group,
  LineBasicMaterial,
  LineLoop,
  LineSegments,
  Mesh,
  MeshStandardMaterial,
  Quaternion,
  SphereGeometry,
  type Texture,
} from 'three'
import { applyPivotRotation, rotationMatrixFromEulerXYZDeg } from '../domain/coordinateSystem'
import type { Bone, Cuboid, MobProjectModel, Vec3 } from '../domain/MobProjectModel'
import { applyCuboidFaceUvs, BOX_GEOMETRY_FACE_ORDER, type CuboidFaceRef } from './textureUvMapping'

const CUBOID_COLOR = 0x8a8f98
// Blanco -- con `map` seteado, el color del material TIÑE la textura;
// blanco puro deja pasar los colores reales del atlas sin alterarlos.
const TEXTURED_CUBOID_COLOR = 0xffffff
const BONE_PIVOT_COLOR = 0xe0574c
const BONE_PIVOT_RADIUS = 0.5
const SELECTION_OUTLINE_COLOR = 0xffb020
export const SELECTION_OUTLINE_NAME = 'selection-outline'
export const FACE_HIGHLIGHT_NAME = 'face-highlight'

/**
 * Orden real del PERÍMETRO de las 4 esquinas de cada cara de una
 * `BoxGeometry` sin segmentos (mismos 4 vértices que documenta
 * `DEFAULT_FACE_CORNERS` en `textureUvMapping.ts`: 0=arriba-izq,
 * 1=arriba-der, 2=abajo-izq, 3=abajo-der) -- recorrer 0,1,2,3 en ese orden
 * cruzaría la cara en diagonal (1->2 es la diagonal del rectángulo, no un
 * lado); el orden de borde real es 0->1->3->2->0.
 */
const FACE_PERIMETER_CORNER_ORDER = [0, 1, 3, 2] as const

/** Construye el outline (`LineLoop`) de UNA cara de `geometry` (índice de grupo `faceIndex`, 0..5) -- hijo del mesh, ver docstring de la clase. */
function buildFaceHighlight(geometry: BoxGeometry, faceIndex: number): LineLoop {
  const position = geometry.getAttribute('position') as BufferAttribute
  const positions = new Float32Array(FACE_PERIMETER_CORNER_ORDER.length * 3)
  FACE_PERIMETER_CORNER_ORDER.forEach((cornerIndex, i) => {
    const vertexIndex = faceIndex * 4 + cornerIndex
    positions[i * 3] = position.getX(vertexIndex)
    positions[i * 3 + 1] = position.getY(vertexIndex)
    positions[i * 3 + 2] = position.getZ(vertexIndex)
  })
  const highlightGeometry = new BufferGeometry()
  highlightGeometry.setAttribute('position', new BufferAttribute(positions, 3))
  const highlight = new LineLoop(highlightGeometry, new LineBasicMaterial({ color: SELECTION_OUTLINE_COLOR }))
  highlight.name = FACE_HIGHLIGHT_NAME
  return highlight
}

/** [bone, su padre, su abuelo, ..., raíz] -- orden en que se componen las rotaciones. */
function ancestorChain(boneId: string, bonesById: Map<string, Bone>): Bone[] {
  const chain: Bone[] = []
  let current = bonesById.get(boneId)
  while (current) {
    chain.push(current)
    current = current.parentId ? bonesById.get(current.parentId) : undefined
  }
  return chain
}

/** Aplica, en orden, la rotación de cada bone de la cadena sobre su propio pivote. */
function composeChainOnPoint(point: Vec3, chain: Bone[]): Vec3 {
  return chain.reduce((acc, bone) => applyPivotRotation(acc, bone.pivot, bone.rotation), point)
}

function midpoint(from: Vec3, to: Vec3): Vec3 {
  return [(from[0] + to[0]) / 2, (from[1] + to[1]) / 2, (from[2] + to[2]) / 2]
}

interface AtlasTextureInfo {
  texture: Texture
  width: number
  height: number
}

function buildCuboidMesh(
  cuboid: Cuboid,
  chain: Bone[],
  isSelected: boolean,
  atlas: AtlasTextureInfo | null,
  selectedFace: CuboidFaceRef | null,
): Mesh {
  const size: Vec3 = [
    Math.abs(cuboid.to[0] - cuboid.from[0]),
    Math.abs(cuboid.to[1] - cuboid.from[1]),
    Math.abs(cuboid.to[2] - cuboid.from[2]),
  ]
  const localCenter = midpoint(cuboid.from, cuboid.to)

  const geometry = new BoxGeometry(size[0], size[1], size[2])
  if (atlas) {
    applyCuboidFaceUvs(geometry.getAttribute('uv') as BufferAttribute, cuboid.faces, atlas.width, atlas.height)
  }
  const material = atlas
    ? new MeshStandardMaterial({ color: TEXTURED_CUBOID_COLOR, map: atlas.texture })
    : new MeshStandardMaterial({ color: CUBOID_COLOR })
  // Ticket 049: array de 6 slots con la MISMA instancia -- ver docstring
  // de la clase sobre por qué esto es necesario para el picking
  // determinista por cara (`intersection.face.materialIndex` real).
  const mesh = new Mesh(geometry, [material, material, material, material, material, material])
  mesh.name = cuboid.name
  // El nombre puede repetirse entre cuboids -- el picking (ticket 017) usa
  // este id real, nunca el nombre, para identificar qué se clickeó.
  mesh.userData.cuboidId = cuboid.id
  // Ticket 049 (HU-25, Diseño técnico §14): etiquetado determinista de cada
  // grupo de material con su FaceName -- misma fuente de verdad que ya usa
  // `applyCuboidFaceUvs` para el orden real de `BoxGeometry`.
  mesh.userData.faceNamesByGroup = BOX_GEOMETRY_FACE_ORDER

  if (isSelected) {
    // Hijo del mesh -- hereda su transform automáticamente, sin duplicar
    // la lógica de posición/orientación compuesta de la cadena de bones.
    const outline = new LineSegments(new EdgesGeometry(geometry), new LineBasicMaterial({ color: SELECTION_OUTLINE_COLOR }))
    outline.name = SELECTION_OUTLINE_NAME
    mesh.add(outline)
  }

  if (selectedFace && selectedFace.cuboidId === cuboid.id) {
    const faceIndex = BOX_GEOMETRY_FACE_ORDER.indexOf(selectedFace.face)
    if (faceIndex !== -1) {
      mesh.add(buildFaceHighlight(geometry, faceIndex))
    }
  }

  // Posición: aplica la rotación propia del cuboid sobre su origen, luego compone la cadena de bones.
  const ownRotation = applyPivotRotation(localCenter, cuboid.origin, cuboid.rotation)
  const worldCenter = composeChainOnPoint(ownRotation, chain)
  mesh.position.set(worldCenter[0], worldCenter[1], worldCenter[2])

  // Orientación: producto de las mismas matrices de rotación, mismo orden
  // (cuboid primero, luego cada bone ancestro) -- una rotación pura no
  // depende de qué pivote se usó, solo del orden de composición.
  let matrix = rotationMatrixFromEulerXYZDeg(cuboid.rotation)
  for (const bone of chain) {
    matrix = rotationMatrixFromEulerXYZDeg(bone.rotation).multiply(matrix)
  }
  mesh.quaternion.copy(new Quaternion().setFromRotationMatrix(matrix))

  return mesh
}

function buildBonePivotMarker(bone: Bone, bonesById: Map<string, Bone>): Mesh {
  const chain = ancestorChain(bone.id, bonesById)
  const worldPivot = composeChainOnPoint(bone.pivot, chain)

  const marker = new Mesh(
    new SphereGeometry(BONE_PIVOT_RADIUS, 12, 12),
    new MeshStandardMaterial({ color: BONE_PIVOT_COLOR }),
  )
  marker.name = `bone-pivot:${bone.name}`
  marker.position.set(worldPivot[0], worldPivot[1], worldPivot[2])
  return marker
}

/**
 * Construye el grupo Three.js completo del mob: un mesh por cuboid (AC #1
 * del ticket 008) más un marcador esférico en el pivote mundial de cada
 * bone. `selectedCuboidId` (ticket 016) agrega un outline (`EdgesGeometry`
 * + `LineSegments`, hijo del mesh -- hereda su transform automáticamente)
 * al cuboid seleccionado, para distinguirlo visualmente de los demás
 * (AC #2 del ticket 016). La sincronización real de selección con la
 * jerarquía llega en el ticket 017 -- este parámetro es la superficie que
 * ese ticket conectará a un store compartido.
 *
 * `atlasTexture` (ticket 047, HU-26): si se pasa, cada cuboid se pinta
 * con este mapa usando `model.uv.textureWidth/textureHeight` como
 * dimensiones de referencia para normalizar `Cuboid.faces[x].uv`
 * (píxeles del atlas) a UV 0..1 -- ver `textureUvMapping.ts`.
 *
 * `selectedFace` (ticket 049, HU-25): si se pasa y su `cuboidId` coincide
 * con el cuboid actual, ese cuboid recibe un highlight de UNA sola cara
 * (`FACE_HIGHLIGHT_NAME`) -- independiente de `selectedCuboidId`/el
 * outline de cuboid completo, ambos pueden coexistir sin conflicto.
 */
export function buildMobGroup(
  model: MobProjectModel,
  selectedCuboidId?: string | null,
  atlasTexture?: Texture | null,
  selectedFace?: CuboidFaceRef | null,
): Group {
  const bonesById = new Map(model.bones.map((bone) => [bone.id, bone]))
  const group = new Group()
  group.name = model.name
  const atlas: AtlasTextureInfo | null = atlasTexture
    ? { texture: atlasTexture, width: model.uv.textureWidth, height: model.uv.textureHeight }
    : null

  for (const cuboid of model.cuboids) {
    const chain = ancestorChain(cuboid.boneId, bonesById)
    group.add(buildCuboidMesh(cuboid, chain, cuboid.id === selectedCuboidId, atlas, selectedFace ?? null))
  }
  for (const bone of model.bones) {
    group.add(buildBonePivotMarker(bone, bonesById))
  }

  return group
}
