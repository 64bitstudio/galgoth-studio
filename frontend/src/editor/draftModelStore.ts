/**
 * Draft en memoria del mob que se está editando (ticket 018) -- estado
 * mutable compartido entre el viewport, la jerarquía y las herramientas
 * de transformación. Envuelve las funciones puras de `geometryOperations.ts`
 * en acciones de store, y captura los errores de validación (dimensión
 * inválida, referencia inexistente, etc.) como `lastError` -- siempre
 * logueado con `console.warn` además de guardado, para que la UI decida
 * cómo mostrarlo sin que el error quede invisible en ningún caso.
 *
 * Persistencia real (Guardar/autosave) llega en el ticket 020 -- este
 * store SOLO mantiene el estado en memoria, no lo guarda todavía.
 */
import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { MobProjectModel, Vec3 } from '../domain/MobProjectModel'
import type { BoneRemovalImpact } from './geometryOperations'
import {
  InvalidGeometryError,
  computeBoneRemovalImpact,
  createBone,
  createCuboid,
  duplicateCuboid,
  moveCuboid,
  removeBoneCascade,
  removeCuboid,
  resizeCuboid,
  rotateCuboid,
  setBonePivot,
  setBoneRotation,
} from './geometryOperations'

function describeError(error: unknown): string {
  return error instanceof InvalidGeometryError ? error.message : String(error)
}

export const useDraftModelStore = defineStore('draftModel', () => {
  const model = ref<MobProjectModel | null>(null)
  const lastError = ref<string | null>(null)

  function load(loadedModel: MobProjectModel): void {
    model.value = loadedModel
    lastError.value = null
  }

  /** Envuelve una operación que puede lanzar `InvalidGeometryError`; aplica el resultado o reporta el rechazo. */
  function apply(context: string, operation: (current: MobProjectModel) => MobProjectModel): void {
    if (!model.value) {
      return
    }
    try {
      model.value = operation(model.value)
      lastError.value = null
    } catch (error) {
      const message = describeError(error)
      console.warn(`[draftModelStore] ${context} rechazado:`, message)
      lastError.value = message
    }
  }

  function moveSelectedCuboid(cuboidId: string, delta: Vec3): void {
    apply('moveCuboid', (current) => moveCuboid(current, cuboidId, delta))
  }

  function resizeSelectedCuboid(cuboidId: string, scale: Vec3): void {
    apply('resizeCuboid', (current) => resizeCuboid(current, cuboidId, scale))
  }

  function rotateSelectedCuboid(cuboidId: string, rotationDeg: Vec3): void {
    apply('rotateCuboid', (current) => rotateCuboid(current, cuboidId, rotationDeg))
  }

  function setPivot(boneId: string, pivot: Vec3): void {
    apply('setBonePivot', (current) => setBonePivot(current, boneId, pivot))
  }

  function setRotation(boneId: string, rotation: Vec3): void {
    apply('setBoneRotation', (current) => setBoneRotation(current, boneId, rotation))
  }

  /** @returns el id del cuboid creado, o `null` si `model` no está cargado o la operación fue rechazada (ver `lastError`). */
  function addCuboid(boneId: string, name: string, from: Vec3, to: Vec3, origin: Vec3): string | null {
    if (!model.value) {
      return null
    }
    let createdId: string | null = null
    try {
      const result = createCuboid(model.value, boneId, name, from, to, origin)
      model.value = result.model
      lastError.value = null
      createdId = result.cuboidId
    } catch (error) {
      const message = describeError(error)
      console.warn('[draftModelStore] createCuboid rechazado:', message)
      lastError.value = message
    }
    return createdId
  }

  /** @returns el id del bone creado, o `null` si `model` no está cargado o `parentId` no existe (ver `lastError`). */
  function addBone(parentId: string | null, name: string, pivot: Vec3, rotation: Vec3): string | null {
    if (!model.value) {
      return null
    }
    let createdId: string | null = null
    try {
      const result = createBone(model.value, parentId, name, pivot, rotation)
      model.value = result.model
      lastError.value = null
      createdId = result.boneId
    } catch (error) {
      const message = describeError(error)
      console.warn('[draftModelStore] createBone rechazado:', message)
      lastError.value = message
    }
    return createdId
  }

  function deleteCuboid(cuboidId: string): void {
    apply('removeCuboid', (current) => removeCuboid(current, cuboidId))
  }

  function boneRemovalImpact(boneId: string): BoneRemovalImpact | null {
    return model.value ? computeBoneRemovalImpact(model.value, boneId) : null
  }

  function deleteBoneCascade(boneId: string): void {
    apply('removeBoneCascade', (current) => removeBoneCascade(current, boneId))
  }

  /** @returns el id del cuboid duplicado, o `null` si `model` no está cargado o `cuboidId` no existe (ver `lastError`). */
  function duplicate(cuboidId: string): string | null {
    if (!model.value) {
      return null
    }
    let createdId: string | null = null
    try {
      const result = duplicateCuboid(model.value, cuboidId)
      model.value = result.model
      lastError.value = null
      createdId = result.cuboidId
    } catch (error) {
      const message = describeError(error)
      console.warn('[draftModelStore] duplicateCuboid rechazado:', message)
      lastError.value = message
    }
    return createdId
  }

  return {
    model,
    lastError,
    load,
    moveSelectedCuboid,
    resizeSelectedCuboid,
    rotateSelectedCuboid,
    setPivot,
    setRotation,
    addCuboid,
    addBone,
    deleteCuboid,
    boneRemovalImpact,
    deleteBoneCascade,
    duplicate,
  }
})
