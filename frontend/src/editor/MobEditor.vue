<script setup lang="ts">
/**
 * Ruta real del editor manual sobre un mob existente (ticket 034) --
 * primera vez que `ThreeViewport.vue`/`HierarchyPanel.vue`/`EditorToolbar.vue`
 * (016-018, reales y probados desde entonces) se montan sobre un
 * `mobId` REAL tomado de la URL, en vez de solo vía `/dev/viewport-harness`
 * (fixture estático). Desbloquea 031/032/033, que asumen esta pantalla.
 *
 * Carga en dos pasos: primero `GET /api/mobs/{mobId}` (resumen real --
 * `name`/`baseType`, necesarios si no hay ningún draft todavía) y
 * después `GET /api/mobs/{mobId}/draft` (020). Un mob recién creado sin
 * ningún commit todavía (autosave/Guardar/"Usar este modelo") no tiene
 * fila en `mob_drafts` -- `DRAFT_NOT_FOUND` es un caso ESPERADO, no un
 * error: el editor arranca desde un modelo vacío coherente con el mob
 * real (`emptyMobProjectModel`, compartido con el pipeline de
 * generación IA), nunca una pantalla rota ni en blanco sin explicación.
 */
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import ThreeViewport from '../viewport/ThreeViewport.vue'
import { threeViewportService } from '../viewport/ThreeViewportService'
import HierarchyPanel from './HierarchyPanel.vue'
import EditorToolbar from './EditorToolbar.vue'
import AiEditPanel from './AiEditPanel.vue'
import GenerationPreviewViewport from '../ai/GenerationPreviewViewport.vue'
import { useDraftModelStore } from './draftModelStore'
import { getDraft } from './draftPersistenceApi'
import { getMob } from '../projects/mobsApi'
import { emptyMobProjectModel } from '../domain/emptyMobProjectModel'
import { ApiError } from '../api/ApiError'
import GSidebar, { type GSidebarKey } from '../design-system/components/GSidebar.vue'
import type { MobProjectModel } from '../domain/MobProjectModel'

const route = useRoute()
const router = useRouter()
const projectId = route.params.projectId as string
const mobId = route.params.mobId as string

const draft = useDraftModelStore()
const loadError = ref<string | null>(null)
const notFound = ref(false)

// Ticket 031: mientras `AiEditPanel` tiene un plan activo, el canvas
// principal muestra ese modelo de solo lectura (`aiPreviewModel`) en vez
// del `ThreeViewport` editable -- ambos envuelven el mismo singleton de
// `ThreeViewportService` (un solo canvas WebGL del proceso), así que no
// pueden estar montados a la vez. Ver el comentario de cabecera de
// `AiEditPanel.vue` para el porqué completo.
const showAiPanel = ref(false)
const aiPreviewModel = ref<MobProjectModel | null>(null)

function handleAiPreviewModelChanged(model: MobProjectModel | null): void {
  aiPreviewModel.value = model
}

function handleAiEditApplied(model: MobProjectModel): void {
  draft.load(model)
}

onMounted(async () => {
  try {
    const mob = await getMob(mobId)
    try {
      const draftView = await getDraft(mobId)
      draft.load(draftView.model)
    } catch (error) {
      if (error instanceof ApiError && error.code === 'DRAFT_NOT_FOUND') {
        // Caso esperado (AC #2): ningún commit todavía -- arranca vacío.
        draft.load(emptyMobProjectModel(mobId, projectId, mob.name, mob.baseType))
      } else {
        throw error
      }
    }
  } catch (error) {
    if (error instanceof ApiError && error.code === 'MOB_NOT_FOUND') {
      notFound.value = true
      return
    }
    loadError.value = error instanceof ApiError ? error.message : 'No se pudo cargar el editor de este mob.'
  }
})

function handleSidebarSelect(key: GSidebarKey): void {
  if (key === 'home' || key === 'projects') {
    router.push('/projects')
  }
}

function backToProject(): void {
  router.push(`/projects/${projectId}`)
}
</script>

<template>
  <div class="mob-editor-shell">
    <GSidebar active="projects" @select="handleSidebarSelect" />
    <main class="mob-editor">
      <p v-if="notFound" class="mob-editor__error">
        Este mob no existe. <button type="button" class="mob-editor__link-button" @click="backToProject">Volver al proyecto</button>
      </p>
      <p v-else-if="loadError" class="mob-editor__error">{{ loadError }}</p>
      <template v-else-if="draft.model">
        <EditorToolbar />
        <div class="mob-editor__body">
          <AiEditPanel
            v-if="showAiPanel"
            :mob-id="mobId"
            class="mob-editor__hierarchy"
            @preview-model-changed="handleAiPreviewModelChanged"
            @applied="handleAiEditApplied"
          />
          <HierarchyPanel v-else class="mob-editor__hierarchy" />
          <GenerationPreviewViewport v-if="aiPreviewModel" :model="aiPreviewModel" class="mob-editor__canvas" />
          <ThreeViewport v-else class="mob-editor__canvas" />
        </div>
        <div class="mob-editor__actions">
          <button type="button" class="mob-editor__reset-camera" @click="threeViewportService.resetCamera()">Reset cámara</button>
          <button type="button" class="mob-editor__ai-toggle" @click="showAiPanel = !showAiPanel">
            {{ showAiPanel ? 'Editor manual' : 'Asistente IA' }}
          </button>
        </div>
      </template>
      <p v-else class="mob-editor__loading">Cargando…</p>
    </main>
  </div>
</template>

<style scoped>
.mob-editor-shell {
  display: flex;
  height: 100vh;
}

.mob-editor {
  flex: 1;
  padding: var(--space-4);
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  min-width: 0;
}

.mob-editor__body {
  flex: 1;
  min-height: 0;
  display: flex;
  gap: var(--space-2);
}

.mob-editor__hierarchy {
  width: 240px;
  flex-shrink: 0;
  border: var(--border-width) solid var(--border);
  border-radius: var(--radius-md);
  overflow: auto;
}

.mob-editor__canvas {
  flex: 1;
  min-width: 0;
}

.mob-editor__actions {
  display: flex;
  gap: var(--space-2);
}

.mob-editor__reset-camera,
.mob-editor__ai-toggle {
  min-height: var(--hit-target-min);
  padding: 0 var(--space-3);
  background: var(--surface-2);
  color: var(--text);
  border: var(--border-width) solid var(--border);
  border-radius: var(--radius-md);
  cursor: pointer;
}

.mob-editor__ai-toggle {
  border-color: var(--accent);
  font-weight: 600;
}

.mob-editor__loading,
.mob-editor__error {
  color: var(--muted);
}

.mob-editor__link-button {
  background: none;
  border: none;
  color: var(--accent);
  text-decoration: underline;
  cursor: pointer;
  padding: 0;
  font: inherit;
}
</style>
