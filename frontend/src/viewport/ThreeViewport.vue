<script setup lang="ts">
/**
 * Wrapper Vue del viewport Three.js compartido (ticket 008; cámara
 * orbital/grid/reset en el servicio desde el ticket 016; selección
 * sincronizada con la jerarquía vía `useSelectionStore` desde el 017).
 * No crea su propio renderer -- adjunta el canvas singleton de
 * `ThreeViewportService` a su contenedor mientras está montado, y lo
 * libera al desmontar (así la próxima pantalla que lo use puede
 * reclamarlo sin pelear por el contexto WebGL).
 *
 * Ticket 018: lee el modelo de `useDraftModelStore` (ya no como prop) --
 * es la fuente de verdad editable compartida con la jerarquía y las
 * herramientas de transformación. También cablea los gizmos de
 * transformación (`TransformControls`, en el servicio) a las acciones
 * del store al soltar el drag (no en cada frame -- ver Hecho del ticket
 * sobre por qué "commit al soltar" es la estrategia correcta dado que
 * `setModel` reconstruye todos los meshes en cada mutación).
 *
 * Ticket 043, Diseño técnico §15: el modo 'scale' (Resize) deja de
 * commitear localmente vía `draftModelStore.resizeSelectedCuboid` --
 * durante el drag (`objectChange`), el preview sigue siendo 100% local
 * (Three.js ya escala el mesh en vivo, sin ninguna llamada de red); solo
 * al soltar (`mouseUp`, equivalente a `pointerup`) se dispara la ÚNICA
 * llamada a `POST /geometry/apply` (`useGeometryApplyStore.resizeCuboid`).
 * Si el backend exige confirmación de pérdida de pintura, el preview
 * visual del tamaño soltado se deja TAL CUAL (no se toca el mesh ni el
 * store) mientras `MobEditor.vue` muestra el modal -- un `watch` sobre
 * `pendingResizeConfirmation` (ver más abajo) refresca el mesh cuando el
 * modal se resuelve, tanto si fue "Confirmar" como "Cancelar". Move/Rotate
 * siguen exactamente igual que antes (100% client-side).
 */
import { MathUtils, Quaternion, Vector3 } from 'three'
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import type { Vec3 } from '../domain/MobProjectModel'
import { useDraftModelStore } from '../editor/draftModelStore'
import { useGeometryApplyStore } from '../editor/geometryApplyStore'
import { useSelectionStore } from '../editor/selectionStore'
import { threeViewportService } from './ThreeViewportService'

// Umbral de movimiento del mouse entre pointerdown y click -- por encima
// de esto se interpreta como arrastre de órbita (OrbitControls), no como
// un click de selección. Sin esto, cada drag de cámara seleccionaría/
// deseleccionaría accidentalmente lo que quedó bajo el cursor al soltar.
const CLICK_DRAG_THRESHOLD_PX = 5

const draft = useDraftModelStore()
const selection = useSelectionStore()
const geometryApply = useGeometryApplyStore()
const container = ref<HTMLDivElement>()
let pointerDownPosition: { x: number; y: number } | null = null

function handlePointerDown(event: PointerEvent): void {
  pointerDownPosition = { x: event.clientX, y: event.clientY }
}

function handleClick(event: MouseEvent): void {
  if (pointerDownPosition) {
    const distance = Math.hypot(event.clientX - pointerDownPosition.x, event.clientY - pointerDownPosition.y)
    if (distance > CLICK_DRAG_THRESHOLD_PX) {
      return // fue un arrastre de órbita, no una selección
    }
  }
  selection.select(threeViewportService.pickCuboidIdAt(event.clientX, event.clientY))
}

// -- Gizmos de transformación (ticket 018) -----------------------------------

let dragStartPosition: Vector3 | null = null
let dragStartQuaternion: Quaternion | null = null
let lastRotation: { axis: Vector3; angleRad: number } | null = null

/**
 * El eje de rotación de `TransformControls` en modo 'local' con un solo
 * anillo arrastrado es SIEMPRE un vector unitario limpio sobre un eje
 * (verificado contra el código fuente real de TransformControls.js,
 * `_unit.X/Y/Z`) -- nunca hace falta descomponer un quaternion general.
 * Si el usuario arrastró el anillo libre (rotación compuesta, sin un eje
 * dominante), se ignora: nuestro contrato de rotación (`rotateCuboid`)
 * solo puede expresar un delta de un solo eje por vez.
 */
function axisToRotationDelta(axis: Vector3, angleDeg: number): Vec3 | null {
  if (axis.x > 0.5) {
    return [angleDeg, 0, 0]
  }
  if (axis.y > 0.5) {
    return [0, angleDeg, 0]
  }
  if (axis.z > 0.5) {
    return [0, 0, angleDeg]
  }
  return null
}

function handleTransformMouseDown(): void {
  const object = threeViewportService.transformControls.object
  if (!object) {
    return
  }
  dragStartPosition = object.position.clone()
  dragStartQuaternion = object.quaternion.clone()
  lastRotation = null
}

/**
 * `rotationAxis`/`rotationAngle` son propiedades reales de
 * `TransformControls` en runtime (definidas vía `defineProperty` en su
 * código fuente, actualizadas en cada frame de un drag de rotación) --
 * pero faltan en los tipos de `@types/three` (solo declara los eventos
 * `*-changed`, no las propiedades en sí). El cast es un hueco conocido
 * de los tipos, no una suposición sobre el runtime real.
 */
function handleTransformObjectChange(): void {
  const controls = threeViewportService.transformControls as unknown as {
    mode: string
    rotationAxis: Vector3
    rotationAngle: number
  }
  if (controls.mode === 'rotate') {
    lastRotation = { axis: controls.rotationAxis.clone(), angleRad: controls.rotationAngle }
  }
}

function handleTransformMouseUp(): void {
  const controls = threeViewportService.transformControls
  const object = controls.object
  const cuboidId = selection.selectedCuboidId
  const mobId = draft.model?.mobId
  if (!object || !dragStartPosition || !dragStartQuaternion || !cuboidId) {
    resetDragState()
    return
  }

  if (controls.mode === 'translate') {
    const worldDelta = object.position.clone().sub(dragStartPosition)
    const localDelta = worldDelta.applyQuaternion(dragStartQuaternion.clone().invert())
    if (localDelta.lengthSq() > 0) {
      draft.moveSelectedCuboid(cuboidId, [localDelta.x, localDelta.y, localDelta.z])
    }
    revertMeshIfRejected(cuboidId)
  } else if (controls.mode === 'scale') {
    const s = object.scale
    if ((s.x !== 1 || s.y !== 1 || s.z !== 1) && mobId) {
      // Ticket 043: commit real vía backend (POST /geometry/apply), no
      // local -- el preview del drag ya lo mostró Three.js en vivo, sin
      // red, así que no hace falta tocar el mesh acá en ningún caso: si el
      // backend confirma, `commitExternalModel` dispara el `watch` de abajo
      // con la geometría real; si pide confirmación, el mesh se queda tal
      // cual hasta que `MobEditor.vue` resuelva el modal (Confirmar/Cancelar).
      geometryApply.resizeCuboid(mobId, cuboidId, [s.x, s.y, s.z])
    }
  } else if (controls.mode === 'rotate' && lastRotation) {
    const delta = axisToRotationDelta(lastRotation.axis, MathUtils.radToDeg(lastRotation.angleRad))
    if (delta) {
      draft.rotateSelectedCuboid(cuboidId, delta)
    }
    revertMeshIfRejected(cuboidId)
  }

  resetDragState()
}

/** Si la operación (Move/Rotate, 100% client-side) fue rechazada (dimensión inválida, etc.), el modelo del store NO cambió -- se fuerza un refresh con los datos reales para que el gizmo/mesh vuelvan a la última posición válida. Resize (backend) nunca llega acá -- ver el bloque 'scale' de arriba. */
function revertMeshIfRejected(cuboidId: string): void {
  if (draft.lastError && draft.model) {
    threeViewportService.setModel(draft.model, cuboidId)
  }
}

/**
 * El modal de confirmación de pérdida de pintura (Diseño técnico §2, en
 * `MobEditor.vue`) se resuelve de 2 formas -- "Confirmar" ya deja
 * `draft.model` actualizado (vía `commitExternalModel`) ANTES de limpiar
 * `pendingResizeConfirmation`; "Cancelar" limpia `pendingResizeConfirmation`
 * SIN tocar `draft.model` (sigue siendo la geometría vieja). En ambos
 * casos, un refresh del mesh contra `draft.model` deja el resultado
 * correcto: la geometría real confirmada, o la geometría anterior si se
 * canceló -- un solo watcher cubre los 2 casos sin que `ThreeViewport`
 * necesite saber cuál de los 2 botones se usó.
 */
watch(
  () => geometryApply.pendingResizeConfirmation,
  (pending, previousPending) => {
    if (!pending && previousPending && draft.model) {
      threeViewportService.setModel(draft.model, previousPending.cuboidId)
    }
  },
)

function resetDragState(): void {
  dragStartPosition = null
  dragStartQuaternion = null
  lastRotation = null
}

onMounted(() => {
  if (!container.value) {
    return
  }
  threeViewportService.attachTo(container.value)
  if (draft.model) {
    threeViewportService.setModel(draft.model, selection.selectedCuboidId)
  }
  threeViewportService.startRenderLoop()
  container.value.addEventListener('pointerdown', handlePointerDown)
  container.value.addEventListener('click', handleClick)

  const controls = threeViewportService.transformControls
  controls.addEventListener('mouseDown', handleTransformMouseDown)
  controls.addEventListener('objectChange', handleTransformObjectChange)
  controls.addEventListener('mouseUp', handleTransformMouseUp)
})

watch(
  () => [draft.model, selection.selectedCuboidId] as const,
  ([model, selectedCuboidId]) => {
    if (model) {
      threeViewportService.setModel(model, selectedCuboidId)
    }
  },
)

onBeforeUnmount(() => {
  container.value?.removeEventListener('pointerdown', handlePointerDown)
  container.value?.removeEventListener('click', handleClick)
  const controls = threeViewportService.transformControls
  controls.removeEventListener('mouseDown', handleTransformMouseDown)
  controls.removeEventListener('objectChange', handleTransformObjectChange)
  controls.removeEventListener('mouseUp', handleTransformMouseUp)
  threeViewportService.detach()
})
</script>

<template>
  <div ref="container" class="three-viewport" />
</template>

<style scoped>
.three-viewport {
  width: 100%;
  height: 100%;
  min-height: 480px;
}
</style>
