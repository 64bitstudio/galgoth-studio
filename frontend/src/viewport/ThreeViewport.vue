<script setup lang="ts">
/**
 * Wrapper Vue del viewport Three.js compartido (ticket 008). No crea su
 * propio renderer -- adjunta el canvas singleton de `ThreeViewportService`
 * a su contenedor mientras está montado, y lo libera al desmontar (así la
 * próxima pantalla que lo use puede reclamarlo sin pelear por el
 * contexto WebGL).
 */
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import type { MobProjectModel } from '../domain/MobProjectModel'
import { threeViewportService } from './ThreeViewportService'

const props = defineProps<{ model: MobProjectModel }>()

const container = ref<HTMLDivElement>()

function focusCameraOnModel(): void {
  // Encuadre fijo simple -- suficiente para el dev harness; una cámara
  // orbital real (OrbitControls) llega con el editor interactivo (016).
  threeViewportService.camera.position.set(40, 40, 40)
  threeViewportService.camera.lookAt(0, 16, 0)
}

onMounted(() => {
  if (!container.value) {
    return
  }
  threeViewportService.attachTo(container.value)
  focusCameraOnModel()
  threeViewportService.setModel(props.model)
  threeViewportService.startRenderLoop()
})

watch(
  () => props.model,
  (model) => threeViewportService.setModel(model),
)

onBeforeUnmount(() => {
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
