/**
 * Formas del wire SSE de `GET /api/jobs/{jobId}/events` (ticket 029,
 * espejo de `GenerationEventView`/`MobGenerationService` del backend) +
 * el modelo de preview EN MEMORIA que `GenerationStep.vue` construye
 * incrementalmente a partir de esos eventos. Nunca toca
 * `useDraftModelStore` (el store real del editor manual, 018) -- el AC
 * #2 exige que el preview sea descartable sin efecto en cualquier
 * momento, así que vive en su propio estado aislado.
 */
import type { Bone, BaseType, Cuboid, MobProjectModel } from '../domain/MobProjectModel'
import { emptyMobProjectModel } from '../domain/emptyMobProjectModel'

export interface PreviewOperationsPayload {
  type: 'preview_operations'
  addedOrUpdatedBones: Bone[]
  addedOrUpdatedCuboids: Cuboid[]
  removedCuboidIds: string[]
}

export interface PreviewSnapshotPayload {
  type: 'preview_snapshot'
  model: MobProjectModel
}

export type GenerationEventPayload = PreviewOperationsPayload | PreviewSnapshotPayload

export interface GenerationEvent {
  seq: number
  stage: string
  message: string | null
  progressPct: number | null
  payload: GenerationEventPayload | null
}

/** Mismo modelo vacío del que arranca `MobGenerationService.emptyModelFor` en el backend (`emptyMobProjectModel`, compartido con el editor manual real, 034) -- el primer `preview_operations` que llega se aplica sobre ESTE punto de partida, no sobre `null`. */
export function emptyPreviewModel(mobId: string, projectId: string, name: string, baseType: BaseType): MobProjectModel {
  return emptyMobProjectModel(mobId, projectId, name, baseType)
}

/** Fusiona un `preview_operations` sobre el modelo de preview actual -- agrega/reemplaza bones/cuboids por id, quita los `removedCuboidIds`. Nunca muta `model` (mismo criterio de inmutabilidad que `geometryOperations.ts`, ticket 018). */
export function applyPreviewDelta(model: MobProjectModel, delta: PreviewOperationsPayload): MobProjectModel {
  const bonesById = new Map(model.bones.map((bone) => [bone.id, bone]))
  for (const bone of delta.addedOrUpdatedBones) {
    bonesById.set(bone.id, bone)
  }

  const removedIds = new Set(delta.removedCuboidIds)
  const cuboidsById = new Map(model.cuboids.filter((cuboid) => !removedIds.has(cuboid.id)).map((cuboid) => [cuboid.id, cuboid]))
  for (const cuboid of delta.addedOrUpdatedCuboids) {
    cuboidsById.set(cuboid.id, cuboid)
  }

  return { ...model, bones: [...bonesById.values()], cuboids: [...cuboidsById.values()] }
}
