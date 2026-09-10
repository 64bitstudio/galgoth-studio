/**
 * Orquesta `POST /api/mobs/{mobId}/geometry/apply` (ticket 043, Diseño
 * técnico §2/§15) para Resize/Add/Remove de cuboid -- las 3 únicas
 * operaciones que afectan UV. Move/Rotate/pivot NUNCA pasan por acá,
 * siguen 100% client-side vía `draftModelStore`/`geometryOperations.ts`.
 *
 * Centralizado en un store propio (en vez de duplicar la orquestación en
 * `ThreeViewport.vue`/`InspectorPanel.vue`/`EditorToolbar.vue`, los 3
 * puntos de entrada reales de Resize/Add/Remove) para que el modal de
 * confirmación de pérdida de pintura (Diseño técnico §2) sea UNO solo,
 * montado una vez en `MobEditor.vue`, sin importar desde cuál de los 3
 * componentes se disparó el resize.
 *
 * Un resize exitoso o confirmado aplica el resultado real (backend-
 * autoritativo) al draft local vía `draftModelStore.commitExternalModel`
 * (ticket 040) -- nunca reimplementa la mutación a mano. Si el backend
 * responde `PAINTED_REGION_RESIZE_CONFIRMATION_REQUIRED`, el preview
 * visual del drag (100% client-side, ver `ThreeViewport.vue`) se deja tal
 * cual -- no se toca `draftModelStore` hasta que el usuario confirme o
 * cancele explícitamente.
 */
import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { Vec3 } from '../domain/MobProjectModel'
import { useDraftModelStore } from './draftModelStore'
import {
  applyGeometry,
  PaintedRegionResizeConfirmationRequiredError,
  type AffectedFace,
  type GeometryApplyOperation,
} from './geometryApplyApi'

export interface PendingResizeConfirmation {
  cuboidId: string
  scale: Vec3
  affectedFaces: AffectedFace[]
}

function describeError(error: unknown): string {
  return error instanceof Error ? error.message : String(error)
}

export const useGeometryApplyStore = defineStore('geometryApply', () => {
  const draft = useDraftModelStore()
  const busy = ref(false)
  const lastError = ref<string | null>(null)
  const pendingResizeConfirmation = ref<PendingResizeConfirmation | null>(null)

  async function runApply(mobId: string, operations: GeometryApplyOperation[]): Promise<boolean> {
    busy.value = true
    try {
      const result = await applyGeometry(mobId, operations)
      draft.commitExternalModel(result.model)
      lastError.value = null
      return true
    } catch (error) {
      lastError.value = describeError(error)
      console.warn('[geometryApplyStore] geometry/apply rechazado:', lastError.value)
      return false
    } finally {
      busy.value = false
    }
  }

  /** Resize de un cuboid ya existente -- disparado SOLO al `pointerup` del gizmo (o al confirmar un input numérico), nunca en cada frame de `pointermove`. */
  async function resizeCuboid(mobId: string, cuboidId: string, scale: Vec3): Promise<void> {
    busy.value = true
    try {
      const result = await applyGeometry(mobId, [{ op: 'resizeCuboid', target: cuboidId, scale }])
      draft.commitExternalModel(result.model)
      lastError.value = null
      pendingResizeConfirmation.value = null
    } catch (error) {
      if (error instanceof PaintedRegionResizeConfirmationRequiredError) {
        // Caso esperado, no un fallo silencioso: se loguea igual (info, no
        // warn) y se abre el modal -- el preview visual del drag se deja
        // intacto (ver docstring de arriba), `draftModelStore` no se toca
        // hasta que el usuario confirme o cancele.
        console.info('[geometryApplyStore] resizeCuboid requiere confirmación de pérdida de pintura:', error.affectedFaces)
        pendingResizeConfirmation.value = { cuboidId, scale, affectedFaces: error.affectedFaces }
      } else {
        lastError.value = describeError(error)
        console.warn('[geometryApplyStore] resizeCuboid rechazado:', lastError.value)
      }
    } finally {
      busy.value = false
    }
  }

  /** Botón "Confirmar" del modal -- reenvía la MISMA operación con `confirmPaintLoss: true`. */
  async function confirmPendingResize(mobId: string): Promise<void> {
    const pending = pendingResizeConfirmation.value
    if (!pending) {
      return
    }
    busy.value = true
    try {
      const result = await applyGeometry(mobId, [{ op: 'resizeCuboid', target: pending.cuboidId, scale: pending.scale }], true)
      draft.commitExternalModel(result.model)
      lastError.value = null
      pendingResizeConfirmation.value = null
    } catch (error) {
      lastError.value = describeError(error)
      console.warn('[geometryApplyStore] confirmPendingResize rechazado:', lastError.value)
    } finally {
      busy.value = false
    }
  }

  /** Botón "Cancelar" del modal -- no reenvía nada. El caller (`ThreeViewport.vue`) es responsable de revertir el preview visual, ya que `draftModelStore` nunca llegó a tocarse. */
  function cancelPendingResize(): void {
    pendingResizeConfirmation.value = null
  }

  /** @returns el id REAL (asignado por el backend) del cuboid creado, o `null` si la operación fue rechazada (ver `lastError`). */
  async function createCuboid(
    mobId: string, boneId: string, name: string, from: Vec3, to: Vec3, origin: Vec3,
  ): Promise<string | null> {
    const previousIds = new Set((draft.model?.cuboids ?? []).map((c) => c.id))
    const tempId = `tmp-${crypto.randomUUID()}`
    const ok = await runApply(mobId, [
      { op: 'createCuboid', tempId, name, boneId, from, to, origin, rotation: [0, 0, 0] },
    ])
    if (!ok) {
      return null
    }
    const created = (draft.model?.cuboids ?? []).find((c) => !previousIds.has(c.id))
    return created?.id ?? null
  }

  async function removeCuboid(mobId: string, cuboidId: string): Promise<void> {
    await runApply(mobId, [{ op: 'removeCuboid', target: cuboidId }])
  }

  return {
    busy,
    lastError,
    pendingResizeConfirmation,
    resizeCuboid,
    confirmPendingResize,
    cancelPendingResize,
    createCuboid,
    removeCuboid,
  }
})
