/**
 * Draft en memoria del mob que se está editando (ticket 018) -- estado
 * mutable compartido entre el viewport, la jerarquía y las herramientas
 * de transformación. Envuelve las funciones puras de `geometryOperations.ts`
 * en acciones de store, y captura los errores de validación (dimensión
 * inválida, referencia inexistente, etc.) como `lastError` -- siempre
 * logueado con `console.warn` además de guardado, para que la UI decida
 * cómo mostrarlo sin que el error quede invisible en ningún caso.
 *
 * Ticket 019: pila de Command de Undo/Redo sobre este mismo draft --
 * "Command" aquí no es una clase propia, es simplemente la referencia al
 * `MobProjectModel` INMEDIATAMENTE ANTERIOR a cada edición exitosa. Esto
 * es seguro y barato porque cada función de `geometryOperations.ts` es
 * pura (nunca muta su modelo de entrada, siempre retorna uno nuevo vía
 * spread) -- las referencias históricas en la pila nunca se corrompen
 * por una mutación posterior. Ninguna operación rechazada empuja un
 * Command (deshacer un no-op no tendría sentido); Undo/Redo NUNCA crea
 * ni destruye una `mob_revision` -- solo re-asignan `model`.
 *
 * Persistencia real (Guardar/autosave) llega en el ticket 020 -- este
 * store SOLO mantiene el estado en memoria, no lo guarda todavía.
 */
import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
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
  const undoStack = ref<MobProjectModel[]>([])
  const redoStack = ref<MobProjectModel[]>([])

  const canUndo = computed(() => undoStack.value.length > 0)
  const canRedo = computed(() => redoStack.value.length > 0)

  function load(loadedModel: MobProjectModel): void {
    model.value = loadedModel
    lastError.value = null
    // Cargar un mob es el INICIO de una historia de edición, no un paso
    // dentro de una ya existente -- ninguna pila sobrevive a un load().
    undoStack.value = []
    redoStack.value = []
  }

  /** Registra un Command exitoso: `previous` es el estado justo antes del cambio que ya se aplicó a `model`. Descarta la rama de redo pendiente (historial lineal estándar, AC #3). */
  function recordCommand(previous: MobProjectModel): void {
    undoStack.value = [...undoStack.value, previous]
    redoStack.value = []
  }

  function undo(): void {
    if (!model.value || undoStack.value.length === 0) {
      return
    }
    const previous = undoStack.value.at(-1)!
    undoStack.value = undoStack.value.slice(0, -1)
    redoStack.value = [...redoStack.value, model.value]
    model.value = previous
  }

  function redo(): void {
    if (!model.value || redoStack.value.length === 0) {
      return
    }
    const next = redoStack.value.at(-1)!
    redoStack.value = redoStack.value.slice(0, -1)
    undoStack.value = [...undoStack.value, model.value]
    model.value = next
  }

  /**
   * Commit genérico de un `MobProjectModel` ya calculado por fuera de
   * `geometryOperations.ts` (p. ej. un resultado confirmado por el backend,
   * ticket 041) -- pasa por el MISMO mecanismo de Command que toda otra
   * operación (push de `previous`, descarta la rama de redo), sin ninguna
   * lógica específica de QUÉ cambió entre `previous` y `next`. Es lo que
   * prueba, sin lógica de Undo nueva, que el mecanismo genérico de snapshot
   * ya cubre cualquier campo aditivo de `MobProjectModel` (ticket 040, AC #4)
   * -- incluyendo `uv.reservations`.
   */
  function commitExternalModel(next: MobProjectModel): void {
    if (!model.value) {
      return
    }
    const previous = model.value
    model.value = next
    lastError.value = null
    recordCommand(previous)
  }

  /** Envuelve una operación que puede lanzar `InvalidGeometryError`; aplica el resultado o reporta el rechazo. */
  function apply(context: string, operation: (current: MobProjectModel) => MobProjectModel): void {
    if (!model.value) {
      return
    }
    const previous = model.value
    try {
      model.value = operation(previous)
      lastError.value = null
      recordCommand(previous)
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
    const previous = model.value
    let createdId: string | null = null
    try {
      const result = createCuboid(previous, boneId, name, from, to, origin)
      model.value = result.model
      lastError.value = null
      recordCommand(previous)
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
    const previous = model.value
    let createdId: string | null = null
    try {
      const result = createBone(previous, parentId, name, pivot, rotation)
      model.value = result.model
      lastError.value = null
      recordCommand(previous)
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
    const previous = model.value
    let createdId: string | null = null
    try {
      const result = duplicateCuboid(previous, cuboidId)
      model.value = result.model
      lastError.value = null
      recordCommand(previous)
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
    canUndo,
    canRedo,
    load,
    undo,
    redo,
    commitExternalModel,
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
