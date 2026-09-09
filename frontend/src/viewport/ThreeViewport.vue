<script setup lang="ts">
/**
 * Wrapper Vue del viewport Three.js compartido (ticket 008; cámara
 * orbital/grid/reset en el servicio desde el ticket 016). No crea su
 * propio renderer -- adjunta el canvas singleton de `ThreeViewportService`
 * a su contenedor mientras está montado, y lo libera al desmontar (así la
 * próxima pantalla que lo use puede reclamarlo sin pelear por el
 * contexto WebGL).
 */
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import type { MobProjectModel } from '../domain/MobProjectModel'
import { threeViewportService } from './ThreeViewportService'

const props = defineProps<{ model: MobProjectModel; selectedCuboidId?: string | null }>()

const container = ref<HTMLDivElement>()

onMounted(() => {
  if (!container.value) {
    return
  }
  threeViewportService.attachTo(container.value)
  threeViewportService.setModel(props.model, props.selectedCuboidId)
  threeViewportService.startRenderLoop()
})

watch(
  () => [props.model, props.selectedCuboidId] as const,
  ([model, selectedCuboidId]) => threeViewportService.setModel(model, selectedCuboidId),
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
