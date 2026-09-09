<script setup lang="ts">
/**
 * Development harness del ticket 008 -- ruta NO enlazada desde la
 * navegación productiva (ver `router.ts`). Abre el sample Carcomido
 * directamente en el viewport sin pasar por creación de proyecto/mob
 * (021/022, que no existen todavía). Se retira o queda oculta detrás de
 * un flag una vez el milestone M3 esté listo (ver Objetivo del ticket).
 *
 * Ticket 016: agrega el selector de cuboid (para probar el outline de
 * selección -- la sincronización real con una jerarquía llega en 017) y
 * el botón de reset de cámara.
 */
import { onMounted, ref } from 'vue'
import ThreeViewport from './ThreeViewport.vue'
import { threeViewportService } from './ThreeViewportService'
import type { MobProjectModel } from '../domain/MobProjectModel'

const model = ref<MobProjectModel | null>(null)
const loadError = ref<string | null>(null)
const selectedCuboidId = ref<string | null>(null)

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
        <label class="viewport-harness__select-label">
          Seleccionar cuboid (prueba de outline, ver ticket 017 para selección real):
          <select v-model="selectedCuboidId">
            <option :value="null">(ninguno)</option>
            <option v-for="cuboid in model.cuboids" :key="cuboid.id" :value="cuboid.id">{{ cuboid.name }}</option>
          </select>
        </label>
        <button type="button" @click="threeViewportService.resetCamera()">Reset cámara</button>
      </div>
      <ThreeViewport :model="model" :selected-cuboid-id="selectedCuboidId" class="viewport-harness__canvas" />
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

.viewport-harness__label,
.viewport-harness__select-label {
  font-family: monospace;
  font-size: 0.85rem;
  color: #666;
  margin: 0;
}

.viewport-harness__error {
  font-family: monospace;
  color: #c0392b;
}

.viewport-harness__canvas {
  flex: 1;
  min-height: 0;
}
</style>
