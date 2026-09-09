<script setup lang="ts">
/**
 * Development harness del ticket 008 -- ruta NO enlazada desde la
 * navegación productiva (ver `router.ts`). Abre el sample Carcomido
 * directamente en el viewport sin pasar por creación de proyecto/mob
 * (021/022, que no existen todavía). Se retira o queda oculta detrás de
 * un flag una vez el milestone M3 esté listo (ver Objetivo del ticket).
 *
 * Ticket 017: panel de jerarquía real, lado a lado con el viewport.
 * Ticket 018: el fixture se carga en `useDraftModelStore` (ya no un ref
 * local) -- es el mismo draft editable que el toolbar de transformación
 * mutará.
 *
 * Ticket 023: el botón "Guardar" real (`EditorToolbar`) necesita un
 * `mobId` que EXISTA de verdad en el backend -- el fixture estático trae
 * un `mobId`/`projectId` inventados. En vez de mockear el backend para
 * este harness de desarrollo, se busca-o-crea un proyecto+mob real
 * ("Dev Harness"/"carcomido-harness", vía 021/022, ya existentes) y se
 * sobreescriben esos dos campos del fixture con los ids reales --
 * conservando toda la geometría rica de Carcomido tal cual.
 */
import { onMounted, ref } from 'vue'
import ThreeViewport from './ThreeViewport.vue'
import { threeViewportService } from './ThreeViewportService'
import HierarchyPanel from '../editor/HierarchyPanel.vue'
import EditorToolbar from '../editor/EditorToolbar.vue'
import { useDraftModelStore } from '../editor/draftModelStore'
import type { MobProjectModel } from '../domain/MobProjectModel'
import { createProject, listProjects } from '../projects/projectsApi'
import { createMob, listMobs } from '../projects/mobsApi'

const DEV_PROJECT_NAME = 'Dev Harness'
const DEV_MOB_NAME = 'carcomido-harness'

const draft = useDraftModelStore()
const loadError = ref<string | null>(null)

/** Busca el proyecto/mob de desarrollo por nombre, o los crea si es la primera vez que corre el harness contra este backend. */
async function findOrCreateHarnessMob(): Promise<{ projectId: string; mobId: string }> {
  const projects = await listProjects()
  const project = projects.find((p) => p.name === DEV_PROJECT_NAME) ?? (await createProject(DEV_PROJECT_NAME))

  const mobs = await listMobs(project.id)
  const mob = mobs.find((m) => m.name === DEV_MOB_NAME) ?? (await createMob(project.id, DEV_MOB_NAME, 'custom'))

  return { projectId: project.id, mobId: mob.id }
}

onMounted(async () => {
  const response = await fetch('/dev-fixtures/carcomido-mob-project-model.json')
  if (!response.ok) {
    loadError.value = `No se pudo cargar el fixture de desarrollo (HTTP ${response.status}).`
    return
  }
  const fixture = (await response.json()) as MobProjectModel

  try {
    const { projectId, mobId } = await findOrCreateHarnessMob()
    draft.load({ ...fixture, projectId, mobId })
  } catch (error) {
    loadError.value = `No se pudo preparar el proyecto/mob real del harness contra el backend: ${String(error)}`
  }
})
</script>

<template>
  <div class="viewport-harness">
    <p v-if="loadError" class="viewport-harness__error">{{ loadError }}</p>
    <template v-else-if="draft.model">
      <div class="viewport-harness__toolbar">
        <span class="viewport-harness__label">
          Dev harness -- {{ draft.model.name }} ({{ draft.model.cuboids.length }} cuboids, {{ draft.model.bones.length }} bones)
        </span>
        <button type="button" @click="threeViewportService.resetCamera()">Reset cámara</button>
      </div>
      <EditorToolbar />
      <div class="viewport-harness__body">
        <HierarchyPanel class="viewport-harness__hierarchy" />
        <ThreeViewport class="viewport-harness__canvas" />
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
