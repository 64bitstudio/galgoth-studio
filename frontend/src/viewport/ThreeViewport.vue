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
 */
import { MathUtils, Quaternion, Vector3 } from 'three'
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import type { Vec3 } from '../domain/MobProjectModel'
import { useDraftModelStore } from '../editor/draftModelStore'
import { useSelectionStore } from '../editor/selectionStore'
import { threeViewportService } from './ThreeViewportService'

// Umbral de movimiento del mouse entre pointerdown y click -- por encima
// de esto se interpreta como arrastre de órbita (OrbitControls), no como
// un click de selección. Sin esto, cada drag de cámara seleccionaría/
// deseleccionaría accidentalmente lo que quedó bajo el cursor al soltar.
const CLICK_DRAG_THRESHOLD_PX = 5

const draft = useDraftModelStore()
const selection = useSelectionStore()
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
  } else if (controls.mode === 'scale') {
    const s = object.scale
    if (s.x !== 1 || s.y !== 1 || s.z !== 1) {
      draft.resizeSelectedCuboid(cuboidId, [s.x, s.y, s.z])
    }
  } else if (controls.mode === 'rotate' && lastRotation) {
    const delta = axisToRotationDelta(lastRotation.axis, MathUtils.radToDeg(lastRotation.angleRad))
    if (delta) {
      draft.rotateSelectedCuboid(cuboidId, delta)
    }
  }

  // Si la operación fue rechazada (dimensión inválida, etc.) el modelo del
  // store NO cambió -- Vue no vuelve a llamar setModel solo, así que el
  // mesh se queda visualmente en la posición/escala/rotación inválida del
  // drag. Se fuerza un refresh con los datos reales (sin cambios) para
  // que el gizmo/mesh vuelvan a la última posición válida.
  if (draft.lastError && draft.model) {
    threeViewportService.setModel(draft.model, cuboidId)
  }

  resetDragState()
}

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
