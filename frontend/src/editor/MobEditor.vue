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
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import ThreeViewport from '../viewport/ThreeViewport.vue'
import { threeViewportService } from '../viewport/ThreeViewportService'
import HierarchyPanel from './HierarchyPanel.vue'
import InspectorPanel from './InspectorPanel.vue'
import EditorHeader from './EditorHeader.vue'
import EditorToolbar from './EditorToolbar.vue'
import AiEditPanel from './AiEditPanel.vue'
import GenerationPreviewViewport from '../ai/GenerationPreviewViewport.vue'
import { useDraftModelStore } from './draftModelStore'
import { useGeometryApplyStore } from './geometryApplyStore'
import { getDraft } from './draftPersistenceApi'
import { getMob } from '../projects/mobsApi'
import { emptyMobProjectModel } from '../domain/emptyMobProjectModel'
import { ApiError } from '../api/ApiError'
import GSidebar, { type GSidebarKey } from '../design-system/components/GSidebar.vue'
import GButton from '../design-system/components/GButton.vue'
import ConfirmDialog from '../design-system/components/ConfirmDialog.vue'
import IconCamera from '../design-system/icons/IconCamera.vue'
import IconSparkle from '../design-system/icons/IconSparkle.vue'
import type { MobProjectModel } from '../domain/MobProjectModel'

const route = useRoute()
const router = useRouter()
const projectId = route.params.projectId as string
const mobId = route.params.mobId as string

const draft = useDraftModelStore()
const geometryApply = useGeometryApplyStore()
const loadError = ref<string | null>(null)
const notFound = ref(false)
const mobName = ref('')

// -- Ticket 043, Diseño técnico §2: modal de confirmación de pérdida de --
// -- pintura, compartido por los 3 puntos de entrada de Resize (gizmo 3D --
// -- de ThreeViewport, input numérico de InspectorPanel) -- montado UNA --
// -- sola vez acá, sin importar desde dónde se disparó el resize. --

const pendingResizeMessage = computed(() => {
  const pending = geometryApply.pendingResizeConfirmation
  if (!pending) {
    return ''
  }
  const affected = pending.affectedFaces
    .map((f) => {
      const cuboidName = draft.model?.cuboids.find((c) => c.id === f.cuboidId)?.name ?? f.cuboidId
      return `${cuboidName} (${f.face})`
    })
    .join(', ')
  return `Este cambio de tamaño hará perder el arte ya pintado de: ${affected}. ¿Confirmar de todas formas?`
})

async function confirmPendingResize(): Promise<void> {
  if (!draft.model) {
    return
  }
  await geometryApply.confirmPendingResize(draft.model.mobId)
}

function cancelPendingResize(): void {
  geometryApply.cancelPendingResize()
}

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
    mobName.value = mob.name
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
        <div class="mob-editor__top">
          <EditorHeader :mob-name="mobName" />
          <div class="mob-editor__top-actions">
            <GButton variant="ghost" @click="threeViewportService.resetCamera()"><template #icon><IconCamera :size="16" /></template>Reset cámara</GButton>
            <GButton :variant="showAiPanel ? 'primary' : 'secondary'" @click="showAiPanel = !showAiPanel">
              <template #icon><IconSparkle :size="16" /></template>{{ showAiPanel ? 'Editor manual' : 'Asistente IA' }}
            </GButton>
            <GButton variant="secondary" @click="router.push(`/projects/${projectId}/mobs/${mobId}/export`)">Exportar</GButton>
          </div>
        </div>
        <EditorToolbar />
        <div class="mob-editor__body">
          <div class="mob-editor__panel mob-editor__panel--left">
            <AiEditPanel
              v-if="showAiPanel"
              :mob-id="mobId"
              @preview-model-changed="handleAiPreviewModelChanged"
              @applied="handleAiEditApplied"
            />
            <HierarchyPanel v-else />
          </div>
          <GenerationPreviewViewport v-if="aiPreviewModel" :model="aiPreviewModel" class="mob-editor__canvas" />
          <ThreeViewport v-else class="mob-editor__canvas" />
          <div class="mob-editor__panel mob-editor__panel--right">
            <InspectorPanel />
          </div>
        </div>
      </template>
      <p v-else class="mob-editor__loading">Cargando…</p>
    </main>

    <ConfirmDialog
      v-if="geometryApply.pendingResizeConfirmation"
      title="Confirmar pérdida de pintura"
      :message="pendingResizeMessage"
      confirm-label="Confirmar"
      danger
      :busy="geometryApply.busy"
      :error="geometryApply.lastError"
      @confirm="confirmPendingResize"
      @cancel="cancelPendingResize"
    />
  </div>
</template>

<style scoped>
.mob-editor-shell {
  display: flex;
  height: 100vh;
}

.mob-editor {
  flex: 1;
  padding: var(--space-3) var(--space-4) var(--space-4);
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  min-width: 0;
  min-height: 0;
}

.mob-editor__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
}

.mob-editor__top-actions {
  display: flex;
  gap: var(--space-2);
  flex-shrink: 0;
}

.mob-editor__body {
  flex: 1;
  min-height: 0;
  display: flex;
  gap: var(--space-3);
}

.mob-editor__panel {
  width: 260px;
  flex-shrink: 0;
  background: var(--panel);
  border: var(--border-width) solid var(--border);
  border-radius: var(--radius-lg);
  overflow: hidden;
}

.mob-editor__canvas {
  flex: 1;
  min-width: 0;
  background: var(--surface);
  border: var(--border-width) solid var(--border);
  border-radius: var(--radius-lg);
  overflow: hidden;
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
