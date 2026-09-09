<script setup lang="ts">
/**
 * Wrapper Vue del viewport Three.js compartido (ticket 008; cámara
 * orbital/grid/reset en el servicio desde el ticket 016; selección
 * sincronizada con la jerarquía vía `useSelectionStore` desde el 017).
 * No crea su propio renderer -- adjunta el canvas singleton de
 * `ThreeViewportService` a su contenedor mientras está montado, y lo
 * libera al desmontar (así la próxima pantalla que lo use puede
 * reclamarlo sin pelear por el contexto WebGL).
 */
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import type { MobProjectModel } from '../domain/MobProjectModel'
import { useSelectionStore } from '../editor/selectionStore'
import { threeViewportService } from './ThreeViewportService'

// Umbral de movimiento del mouse entre pointerdown y click -- por encima
// de esto se interpreta como arrastre de órbita (OrbitControls), no como
// un click de selección. Sin esto, cada drag de cámara seleccionaría/
// deseleccionaría accidentalmente lo que quedó bajo el cursor al soltar.
const CLICK_DRAG_THRESHOLD_PX = 5

const props = defineProps<{ model: MobProjectModel }>()

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

onMounted(() => {
  if (!container.value) {
    return
  }
  threeViewportService.attachTo(container.value)
  threeViewportService.setModel(props.model, selection.selectedCuboidId)
  threeViewportService.startRenderLoop()
  container.value.addEventListener('pointerdown', handlePointerDown)
  container.value.addEventListener('click', handleClick)
})

watch(
  () => [props.model, selection.selectedCuboidId] as const,
  ([model, selectedCuboidId]) => threeViewportService.setModel(model, selectedCuboidId),
)

onBeforeUnmount(() => {
  container.value?.removeEventListener('pointerdown', handlePointerDown)
  container.value?.removeEventListener('click', handleClick)
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
