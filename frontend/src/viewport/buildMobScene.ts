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
 */
import { BoxGeometry, Group, Mesh, MeshStandardMaterial, Quaternion, SphereGeometry } from 'three'
import { applyPivotRotation, rotationMatrixFromEulerXYZDeg } from '../domain/coordinateSystem'
import type { Bone, Cuboid, MobProjectModel, Vec3 } from '../domain/MobProjectModel'

const CUBOID_COLOR = 0x8a8f98
const BONE_PIVOT_COLOR = 0xe0574c
const BONE_PIVOT_RADIUS = 0.5

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

function buildCuboidMesh(cuboid: Cuboid, chain: Bone[]): Mesh {
  const size: Vec3 = [
    Math.abs(cuboid.to[0] - cuboid.from[0]),
    Math.abs(cuboid.to[1] - cuboid.from[1]),
    Math.abs(cuboid.to[2] - cuboid.from[2]),
  ]
  const localCenter = midpoint(cuboid.from, cuboid.to)

  const mesh = new Mesh(new BoxGeometry(size[0], size[1], size[2]), new MeshStandardMaterial({ color: CUBOID_COLOR }))
  mesh.name = cuboid.name

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
 * Construye el grupo Three.js completo del mob: un mesh por cuboid (AC #1)
 * más un marcador esférico en el pivote mundial de cada bone, para hacer
 * visible que la jerarquía se resolvió correctamente (AC #1, "...y bones
 * en la jerarquía correcta").
 */
export function buildMobGroup(model: MobProjectModel): Group {
  const bonesById = new Map(model.bones.map((bone) => [bone.id, bone]))
  const group = new Group()
  group.name = model.name

  for (const cuboid of model.cuboids) {
    const chain = ancestorChain(cuboid.boneId, bonesById)
    group.add(buildCuboidMesh(cuboid, chain))
  }
  for (const bone of model.bones) {
    group.add(buildBonePivotMarker(bone, bonesById))
  }

  return group
}
