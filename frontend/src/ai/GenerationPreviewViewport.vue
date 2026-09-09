<script setup lang="ts">
/**
 * Viewport de preview de generación (ticket 029, AC #2) -- envuelve
 * `ThreeViewportService` DIRECTO, sin pasar por `useDraftModelStore`
 * (a diferencia de `ThreeViewport.vue`, el viewport del editor manual,
 * 016/017/018): el modelo que se renderiza acá es puramente
 * transitorio/descartable (nunca `mob_drafts`/`mob_revisions`), así que
 * usar el store real del editor sería semánticamente incorrecto -- lo
 * mezclaría con el mob que el usuario sí está editando/guardando.
 * Tampoco expone selección/gizmos de transformación: es de solo lectura.
 */
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import type { MobProjectModel } from '../domain/MobProjectModel'
import { threeViewportService } from '../viewport/ThreeViewportService'

const props = defineProps<{ model: MobProjectModel | null }>()

const container = ref<HTMLDivElement>()

onMounted(() => {
  if (!container.value) {
    return
  }
  threeViewportService.attachTo(container.value)
  if (props.model) {
    threeViewportService.setModel(props.model, null)
  }
  threeViewportService.startRenderLoop()
})

watch(
  () => props.model,
  (model) => {
    if (model) {
      threeViewportService.setModel(model, null)
    }
  },
)

onBeforeUnmount(() => {
  threeViewportService.detach()
})
</script>

<template>
  <div ref="container" class="generation-preview-viewport" role="img" aria-label="Vista previa 3D del modelo generándose" />
</template>

<style scoped>
.generation-preview-viewport {
  width: 100%;
  min-height: 280px;
  border: var(--border-width) solid var(--border);
  border-radius: var(--radius-lg);
  background: var(--surface-2);
}
</style>
