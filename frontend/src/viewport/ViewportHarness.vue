<script setup lang="ts">
/**
 * Development harness del ticket 008 -- ruta NO enlazada desde la
 * navegación productiva (ver `router.ts`). Abre el sample Carcomido
 * directamente en el viewport sin pasar por creación de proyecto/mob
 * (021/022, que no existen todavía). Se retira o queda oculta detrás de
 * un flag una vez el milestone M3 esté listo (ver Objetivo del ticket).
 *
 * Ticket 017: reemplaza el `<select>` de prueba del ticket 016 por el
 * panel de jerarquía REAL (`HierarchyPanel`), lado a lado con el
 * viewport -- ambos comparten `useSelectionStore`, así que clickear un
 * cuboid en cualquiera de los dos resalta en el otro (AC del ticket).
 */
import { onMounted, ref } from 'vue'
import ThreeViewport from './ThreeViewport.vue'
import { threeViewportService } from './ThreeViewportService'
import HierarchyPanel from '../editor/HierarchyPanel.vue'
import type { MobProjectModel } from '../domain/MobProjectModel'

const model = ref<MobProjectModel | null>(null)
const loadError = ref<string | null>(null)

onMounted(async () => {
  const response = await fetch('/dev-fixtures/carcomido-mob-project-model.json')
  if (!response.ok) {
    loadError.value = `No se pudo cargar el fixture de desarrollo (HTTP ${response.status}).`
    return
  }
  model.value = (await response.json()) as MobProjectModel
})
</script>

<template>
  <div class="viewport-harness">
    <p v-if="loadError" class="viewport-harness__error">{{ loadError }}</p>
    <template v-else-if="model">
      <div class="viewport-harness__toolbar">
        <span class="viewport-harness__label">
          Dev harness -- {{ model.name }} ({{ model.cuboids.length }} cuboids, {{ model.bones.length }} bones)
        </span>
        <button type="button" @click="threeViewportService.resetCamera()">Reset cámara</button>
      </div>
      <div class="viewport-harness__body">
        <HierarchyPanel :model="model" class="viewport-harness__hierarchy" />
        <ThreeViewport :model="model" class="viewport-harness__canvas" />
      </div>
    </template>
    <p v-else class="viewport-harness__label">Cargando fixture de desarrollo…</p>
  </div>
</template>

<style scoped>
.viewport-harness {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  height: 100vh;
  padding: 1rem;
  box-sizing: border-box;
}

.viewport-harness__toolbar {
  display: flex;
  align-items: center;
  gap: 1rem;
  flex-wrap: wrap;
}

.viewport-harness__label {
  font-family: monospace;
  font-size: 0.85rem;
  color: #666;
  margin: 0;
}

.viewport-harness__error {
  font-family: monospace;
  color: #c0392b;
}

.viewport-harness__body {
  flex: 1;
  min-height: 0;
  display: flex;
  gap: 0.5rem;
}

.viewport-harness__hierarchy {
  width: 220px;
  flex-shrink: 0;
  border: 1px solid #333;
  border-radius: 4px;
}

.viewport-harness__canvas {
  flex: 1;
  min-width: 0;
}
</style>
