/**
 * Operaciones de edición manual del editor (ticket 018) -- mutan un
 * `MobProjectModel` de forma pura (retornan un modelo nuevo, nunca
 * mutan el de entrada), respetando las mismas reglas de geometría que
 * `GeometryEngine` (backend, ticket 005): sin dimensiones negativas o
 * cero, IDs siempre generados por la aplicación (nunca hardcodeados),
 * UV recalculada por AutoUv (007) en cada creación/redimensión.
 *
 * A diferencia del backend, esto NO es un motor de batch con whitelist/
 * tempRef -- cada función se invoca de inmediato por una acción directa
 * del usuario en el editor (Command manual, ver Diseño técnico §4), no
 * como un batch de operaciones de IA no confiables. El backend sigue
 * siendo la autoridad canónica final (revalida cada operación por completo
 * en Guardar/Apply/export).
 */
import { layoutUv } from '../domain/autoUv'
import type { Bone, Cuboid, MobProjectModel, Vec3 } from '../domain/MobProjectModel'

export class InvalidGeometryError extends Error {
  constructor(message: string) {
    super(message)
    this.name = 'InvalidGeometryError'
  }
}

function isPositiveSize(from: Vec3, to: Vec3): boolean {
  return to[0] - from[0] > 0 && to[1] - from[1] > 0 && to[2] - from[2] > 0
}

function addVec3(a: Vec3, b: Vec3): Vec3 {
  return [a[0] + b[0], a[1] + b[1], a[2] + b[2]]
}

function generateId(): string {
  return crypto.randomUUID()
}

function replaceCuboid(model: MobProjectModel, cuboidId: string, next: Cuboid): MobProjectModel {
  return { ...model, cuboids: model.cuboids.map((c) => (c.id === cuboidId ? next : c)) }
}

function replaceBone(model: MobProjectModel, boneId: string, next: Bone): MobProjectModel {
  return { ...model, bones: model.bones.map((b) => (b.id === boneId ? next : b)) }
}

function requireCuboid(model: MobProjectModel, cuboidId: string): Cuboid {
  const cuboid = model.cuboids.find((c) => c.id === cuboidId)
  if (!cuboid) {
    throw new InvalidGeometryError(`No existe ningún cuboid con id '${cuboidId}'.`)
  }
  return cuboid
}

function requireBone(model: MobProjectModel, boneId: string): Bone {
  const bone = model.bones.find((b) => b.id === boneId)
  if (!bone) {
    throw new InvalidGeometryError(`No existe ningún bone con id '${boneId}'.`)
  }
  return bone
}

/** Recalcula la UV de TODOS los cuboids -- misma autoridad determinista que 006/007, nunca UV a medias. */
function refreshUv(model: MobProjectModel): MobProjectModel {
  const result = layoutUv(model.cuboids, model.uv.textureWidth, model.uv.textureHeight)
  return { ...model, cuboids: result.cuboids, uv: { ...model.uv, regions: result.regions } }
}

// -- Move / Resize / Rotate cuboid -----------------------------------------

export function moveCuboid(model: MobProjectModel, cuboidId: string, delta: Vec3): MobProjectModel {
  const cuboid = requireCuboid(model, cuboidId)
  const from = addVec3(cuboid.from, delta)
  const to = addVec3(cuboid.to, delta)
  const origin = addVec3(cuboid.origin, delta)
  if (!isPositiveSize(from, to)) {
    throw new InvalidGeometryError('moveCuboid produciría una dimensión <= 0 -- rechazado.')
  }
  return replaceCuboid(model, cuboidId, { ...cuboid, from, to, origin })
}

/** `scale` escala (to-from) por eje MANTENIENDO EL CENTRO fijo -- mismo criterio que `AlphaAutoPackStrategy`/backend. */
export function resizeCuboid(model: MobProjectModel, cuboidId: string, scale: Vec3): MobProjectModel {
  if (scale[0] <= 0 || scale[1] <= 0 || scale[2] <= 0) {
    throw new InvalidGeometryError(`resizeCuboid: 'scale' debe ser > 0 en los 3 ejes, recibió ${scale}.`)
  }
  const cuboid = requireCuboid(model, cuboidId)
  const center: Vec3 = [
    (cuboid.from[0] + cuboid.to[0]) / 2,
    (cuboid.from[1] + cuboid.to[1]) / 2,
    (cuboid.from[2] + cuboid.to[2]) / 2,
  ]
  const newSize: Vec3 = [
    (cuboid.to[0] - cuboid.from[0]) * scale[0],
    (cuboid.to[1] - cuboid.from[1]) * scale[1],
    (cuboid.to[2] - cuboid.from[2]) * scale[2],
  ]
  const from: Vec3 = [center[0] - newSize[0] / 2, center[1] - newSize[1] / 2, center[2] - newSize[2] / 2]
  const to: Vec3 = [center[0] + newSize[0] / 2, center[1] + newSize[1] / 2, center[2] + newSize[2] / 2]
  if (!isPositiveSize(from, to)) {
    throw new InvalidGeometryError('resizeCuboid produciría una dimensión <= 0 -- rechazado.')
  }
  return replaceCuboid(model, cuboidId, { ...cuboid, from, to })
}

/** Suma `rotationDeg` a la rotación actual del cuboid (delta, no absoluto). */
export function rotateCuboid(model: MobProjectModel, cuboidId: string, rotationDeg: Vec3): MobProjectModel {
  const cuboid = requireCuboid(model, cuboidId)
  return replaceCuboid(model, cuboidId, { ...cuboid, rotation: addVec3(cuboid.rotation, rotationDeg) })
}

// -- Pivot / rotación de bone -------------------------------------------------

/** Reemplaza (absoluto) el pivote de un bone -- rotaciones futuras de ese bone y sus hijos usan el nuevo pivote (AC #5). */
export function setBonePivot(model: MobProjectModel, boneId: string, pivot: Vec3): MobProjectModel {
  const bone = requireBone(model, boneId)
  return replaceBone(model, boneId, { ...bone, pivot })
}

export function setBoneRotation(model: MobProjectModel, boneId: string, rotation: Vec3): MobProjectModel {
  const bone = requireBone(model, boneId)
  return replaceBone(model, boneId, { ...bone, rotation })
}

// -- Add cuboid / Add bone -----------------------------------------------------

export interface CreateCuboidResult {
  model: MobProjectModel
  cuboidId: string
}

/** El ID lo genera la aplicación (AC #2) -- nunca hardcodeado en el cliente. AutoUv corre sobre TODOS los cuboids tras crear. */
export function createCuboid(
  model: MobProjectModel,
  boneId: string,
  name: string,
  from: Vec3,
  to: Vec3,
  origin: Vec3,
): CreateCuboidResult {
  requireBone(model, boneId)
  if (!isPositiveSize(from, to)) {
    throw new InvalidGeometryError('createCuboid: from/to deben producir dimensiones > 0 en los 3 ejes.')
  }
  const cuboidId = generateId()
  const placeholderFace = { uv: [0, 0, 0, 0] as [number, number, number, number], texture: null }
  const newCuboid: Cuboid = {
    id: cuboidId,
    name,
    boneId,
    from,
    to,
    origin,
    rotation: [0, 0, 0],
    faces: {
      north: placeholderFace,
      south: placeholderFace,
      east: placeholderFace,
      west: placeholderFace,
      up: placeholderFace,
      down: placeholderFace,
    },
  }
  const withCuboid = { ...model, cuboids: [...model.cuboids, newCuboid] }
  return { model: refreshUv(withCuboid), cuboidId }
}

export interface CreateBoneResult {
  model: MobProjectModel
  boneId: string
}

export function createBone(
  model: MobProjectModel,
  parentId: string | null,
  name: string,
  pivot: Vec3,
  rotation: Vec3,
): CreateBoneResult {
  if (parentId !== null) {
    requireBone(model, parentId)
  }
  const boneId = generateId()
  const newBone: Bone = { id: boneId, name, parentId, pivot, rotation }
  return { model: { ...model, bones: [...model.bones, newBone] }, boneId }
}

// -- Delete --------------------------------------------------------------------

/** Cascada de uv.regions -- mismo criterio que `RemoveCuboid` (backend, ticket 005 AC #5): sin referencias colgantes. */
export function removeCuboid(model: MobProjectModel, cuboidId: string): MobProjectModel {
  requireCuboid(model, cuboidId)
  return {
    ...model,
    cuboids: model.cuboids.filter((c) => c.id !== cuboidId),
    uv: { ...model.uv, regions: model.uv.regions.filter((r) => r.cuboidId !== cuboidId) },
  }
}

/** Bones y cuboids que `removeBoneCascade` afectaría -- para la advertencia explícita del AC #3, ANTES de confirmar. */
export interface BoneRemovalImpact {
  affectedBoneIds: string[]
  affectedCuboidIds: string[]
}

export function computeBoneRemovalImpact(model: MobProjectModel, boneId: string): BoneRemovalImpact {
  requireBone(model, boneId)
  const affectedBoneIds: string[] = []
  const queue = [boneId]
  while (queue.length > 0) {
    const current = queue.shift()!
    affectedBoneIds.push(current)
    for (const bone of model.bones) {
      if (bone.parentId === current) {
        queue.push(bone.id)
      }
    }
  }
  const affectedBoneIdSet = new Set(affectedBoneIds)
  const affectedCuboidIds = model.cuboids.filter((c) => affectedBoneIdSet.has(c.boneId)).map((c) => c.id)
  return { affectedBoneIds, affectedCuboidIds }
}

/** Elimina el bone Y TODOS sus descendientes (bones+cuboids) -- llamar solo tras confirmar el impacto (AC #3). */
export function removeBoneCascade(model: MobProjectModel, boneId: string): MobProjectModel {
  const { affectedBoneIds, affectedCuboidIds } = computeBoneRemovalImpact(model, boneId)
  const boneIdSet = new Set(affectedBoneIds)
  const cuboidIdSet = new Set(affectedCuboidIds)
  return {
    ...model,
    bones: model.bones.filter((b) => !boneIdSet.has(b.id)),
    cuboids: model.cuboids.filter((c) => !cuboidIdSet.has(c.id)),
    uv: { ...model.uv, regions: model.uv.regions.filter((r) => !cuboidIdSet.has(r.cuboidId)) },
  }
}

// -- Duplicate ------------------------------------------------------------------

export interface DuplicateCuboidResult {
  model: MobProjectModel
  cuboidId: string
}

/** Copia independiente con ID nuevo (AC #4) -- misma geometría/bone, UV propia recalculada por AutoUv. */
export function duplicateCuboid(model: MobProjectModel, cuboidId: string): DuplicateCuboidResult {
  const source = requireCuboid(model, cuboidId)
  const newCuboidId = generateId()
  const copy: Cuboid = { ...source, id: newCuboidId, name: `${source.name}_copy` }
  const withCopy = { ...model, cuboids: [...model.cuboids, copy] }
  return { model: refreshUv(withCopy), cuboidId: newCuboidId }
}
