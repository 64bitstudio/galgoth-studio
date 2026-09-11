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
 *
 * Ticket 050 (HU-41, mockup 07): la tab "Textura" deja de estar
 * deshabilitada -- `activeTab` decide qué se monta en el body, mismo
 * `draft.model` real ya cargado arriba (nunca un segundo fetch/estado
 * duplicado). En la tab Textura se monta EXCLUSIVAMENTE `TextureCanvas`
 * (047/048/049 ya construido -- este ticket lo ENSAMBLA, no lo
 * reimplementa): ni `EditorToolbar` (Move/Scale/Rotate/Add/Delete son
 * herramientas de geometría, sin sentido acá) ni `HierarchyPanel`/
 * `InspectorPanel`/`AiEditPanel` (mockup 07 no los muestra -- solo UV
 * Editor a la izquierda + Vista previa 3D a la derecha, ya resueltos
 * DENTRO de `TextureCanvas`).
 *
 * Ticket 068 (feedback del PO -- homologación visual Modelo/Textura):
 * `.mob-editor__top-actions` (Reset cámara/Asistente IA/Exportar/Guardar)
 * DEJÓ de ser específica de la tab Modelo -- corrección del párrafo
 * anterior, que sí lo era hasta este ticket. Ahora se muestra siempre, en
 * las dos tabs: "Reset cámara" ya operaba sobre el mismo singleton de
 * `ThreeViewportService` que usa el preview 3D de Textura, así que
 * funciona igual sin cambios; "Guardar" sube acá desde
 * `EditorToolbar.vue`/`TextureCanvas.vue` (cada uno sigue dueño de su
 * propia orquestación de guardado, expuesta vía `defineExpose` --
 * `editorToolbarRef`/`textureCanvasRef` más abajo -- este componente
 * solo decide A CUÁL delegar según `activeTab`, sin duplicar ninguna
 * lógica de negocio).
 *
 * Ticket 069 (VoBo del PO sobre el mockup
 * https://claude.ai/code/artifact/a3514398-4fc4-430f-96b2-40299c0038a4):
 * el botón de IA -- corrección del párrafo anterior, que decía que esto
 * quedaba para después -- ahora abre el MISMO `GDrawer.vue` compartido en
 * las dos tabs (antes: toggle inline de `AiEditPanel` en Modelo,
 * navegación a una ruta propia en Textura). `showAiDrawer` es un solo
 * flag compartido; el contenido embebido dentro (`AiEditPanel` o
 * `TextureAiGeneratorPanel`) se decide según `activeTab`, igual que
 * "Guardar". `TextureAiGeneratorPanel` ya no hace su propio fetch del
 * draft (llega como prop `model`, el mismo `draft.model` ya cargado acá)
 * -- al aplicar una textura generada, `handleTextureAiApplied` reobtiene
 * el draft (el atlas persistido no viaja en el JSON del draft) y le pide
 * a `TextureCanvas.reloadAtlas()` que lo recargue, sin duplicar la lógica
 * de descarga/decodificación que ese componente ya tiene.
 */
import { computed, nextTick, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import ThreeViewport from '../viewport/ThreeViewport.vue'
import { threeViewportService } from '../viewport/ThreeViewportService'
import HierarchyPanel from './HierarchyPanel.vue'
import InspectorPanel from './InspectorPanel.vue'
import EditorHeader from './EditorHeader.vue'
import EditorToolbar from './EditorToolbar.vue'
import AiEditPanel from './AiEditPanel.vue'
import GenerationPreviewViewport from '../ai/GenerationPreviewViewport.vue'
import TextureAiGeneratorPanel from '../ai/texture/TextureAiGeneratorPanel.vue'
import TextureCanvas from './texture/TextureCanvas.vue'
import { useDraftModelStore } from './draftModelStore'
import { useGeometryApplyStore } from './geometryApplyStore'
import { getDraft } from './draftPersistenceApi'
import { getMob } from '../projects/mobsApi'
import { emptyMobProjectModel } from '../domain/emptyMobProjectModel'
import { ApiError } from '../api/ApiError'
import GSidebar, { type GSidebarKey } from '../design-system/components/GSidebar.vue'
import GButton from '../design-system/components/GButton.vue'
import GDrawer from '../design-system/components/GDrawer.vue'
import ConfirmDialog from '../design-system/components/ConfirmDialog.vue'
import IconCamera from '../design-system/icons/IconCamera.vue'
import IconSparkle from '../design-system/icons/IconSparkle.vue'
import IconSave from '../design-system/icons/IconSave.vue'
import IconExport from '../design-system/icons/IconExport.vue'
import type { TextureSaveState } from './texture/TextureSaveStatus.vue'
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

// Ticket 050: tab de workspace activa -- 'modelo' por default, mismo
// criterio que `EditorHeader.vue` (GTabs) ya usaba antes de fuego.
const activeTab = ref<string>('modelo')

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
const aiPreviewModel = ref<MobProjectModel | null>(null)

function handleAiPreviewModelChanged(model: MobProjectModel | null): void {
  aiPreviewModel.value = model
}

function handleAiEditApplied(model: MobProjectModel): void {
  draft.load(model)
}

/**
 * Ticket 069: el generador de textura por IA ya no navega a su propia
 * ruta -- `applyTexture` (backend) no devuelve el modelo completo (el
 * atlas persistido nunca viaja en el JSON del draft, ver
 * `TextureCanvas.loadModelAtlas`), así que hace falta reobtener el draft
 * y pedirle a `TextureCanvas` que recargue su atlas -- sin duplicar la
 * lógica de descarga/decodificación que ese componente ya tiene.
 */
async function handleTextureAiApplied(): Promise<void> {
  const draftView = await getDraft(mobId)
  draft.load(draftView.model)
  await nextTick() // deja que `:model="draft.model"` le llegue al prop de TextureCanvas antes de pedirle recargar.
  await textureCanvasRef.value?.reloadAtlas()
}

/**
 * Ticket 068 (feedback del PO -- homologación Modelo/Textura): la fila
 * superior (Reset cámara/Asistente IA/Exportar/Guardar) pasa a mostrarse
 * SIEMPRE, en las dos tabs -- antes `v-if="activeTab === 'modelo'"` la
 * ocultaba por completo en Textura. "Guardar" además sube a esta fila
 * (antes vivía dentro de `EditorToolbar.vue`/`TextureCanvas.vue`) -- ES
 * el mismo botón compartido en las dos tabs, delegando al hijo activo vía
 * `defineExpose` (cada uno sigue dueño de su propia orquestación de
 * guardado: geometría+flush de textura en Modelo, atlas+flush en
 * Textura -- NINGUNA lógica de negocio se duplicó ni se movió).
 *
 * El botón de IA SÍ mantiene su comportamiento actual sin cambios en
 * este ticket (Modelo: toggle del panel inline; Textura: navega a la
 * ruta ya existente del generador, 055) -- unificar eso en un mismo
 * drawer para las dos tabs es una pieza más grande, señalada aparte
 * (ticket 069) para no mezclar un cambio de layout con un cambio de
 * arquitectura de otro tamaño en el mismo PR.
 */
const editorToolbarRef = ref<InstanceType<typeof EditorToolbar>>()
const textureCanvasRef = ref<InstanceType<typeof TextureCanvas>>()

const activeSaveState = computed<TextureSaveState>(() => {
  if (activeTab.value === 'modelo') {
    return editorToolbarRef.value?.saveState ?? 'saved'
  }
  return textureCanvasRef.value?.saveState ?? 'saved'
})

const activeCanSave = computed<boolean>(() => {
  if (activeTab.value === 'modelo') {
    return editorToolbarRef.value?.canSave ?? false
  }
  return textureCanvasRef.value?.canSave ?? false
})

function handleTopSave(): void {
  if (activeTab.value === 'modelo') {
    void editorToolbarRef.value?.handleSave()
  } else {
    void textureCanvasRef.value?.handleSave()
  }
}

// Ticket 069: un solo flag para el drawer compartido -- el contenido
// embebido adentro (AiEditPanel/TextureAiGeneratorPanel) se decide según
// `activeTab`, mismo criterio que `activeSaveState`/`handleTopSave`.
const showAiDrawer = ref(false)

function handleIaButtonClick(): void {
  showAiDrawer.value = true
}

function closeAiDrawer(): void {
  showAiDrawer.value = false
  // Hallazgo real (069): sin este reset, cerrar el drawer con un plan de
  // IA activo (031) dejaba el canvas principal trabado en
  // `GenerationPreviewViewport` en vez de volver al `ThreeViewport`
  // editable -- bug heredado del toggle inline original, nunca disparado
  // en la práctica porque nadie cerraba el panel con un plan activo.
  aiPreviewModel.value = null
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
          <EditorHeader :mob-name="mobName" :active-tab="activeTab" @update:active-tab="activeTab = $event" />
          <!-- Ticket 068: esta fila ya no depende de la tab activa -- se muestra siempre, homologada entre Modelo y Textura. -->
          <div class="mob-editor__top-actions">
            <GButton variant="ghost" @click="threeViewportService.resetCamera()"><template #icon><IconCamera :size="16" /></template>Reset cámara</GButton>
            <GButton :variant="showAiDrawer ? 'primary' : 'accent'" @click="handleIaButtonClick">
              <template #icon><IconSparkle :size="16" /></template>{{ activeTab === 'modelo' ? 'Asistente IA' : 'Generar con IA' }}
            </GButton>
            <GButton variant="secondary" @click="router.push(`/projects/${projectId}/mobs/${mobId}/export`)"><template #icon><IconExport :size="16" /></template>Exportar</GButton>
            <GButton variant="primary" :disabled="!activeCanSave" @click="handleTopSave">
              <template #icon><IconSave :size="16" /></template>{{ activeSaveState === 'saving' ? 'Guardando…' : 'Guardar' }}
            </GButton>
          </div>
        </div>
        <template v-if="activeTab === 'modelo'">
          <EditorToolbar ref="editorToolbarRef" />
          <div class="mob-editor__body">
            <div class="mob-editor__panel mob-editor__panel--left">
              <HierarchyPanel />
            </div>
            <GenerationPreviewViewport v-if="aiPreviewModel" :model="aiPreviewModel" class="mob-editor__canvas" />
            <ThreeViewport v-else class="mob-editor__canvas" />
            <div class="mob-editor__panel mob-editor__panel--right">
              <InspectorPanel />
            </div>
          </div>
        </template>
        <TextureCanvas v-else-if="activeTab === 'textura'" ref="textureCanvasRef" :model="draft.model" class="mob-editor__texture" />
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

    <!-- Ticket 069: drawer compartido del Asistente IA -- mismo GDrawer en las dos tabs, contenido embebido según activeTab. -->
    <GDrawer
      v-if="showAiDrawer && draft.model"
      :title="activeTab === 'modelo' ? 'Asistente IA' : 'Generador de textura (IA)'"
      @cancel="closeAiDrawer"
    >
      <template #title-icon><IconSparkle :size="16" /></template>
      <AiEditPanel
        v-if="activeTab === 'modelo'"
        :mob-id="mobId"
        @preview-model-changed="handleAiPreviewModelChanged"
        @applied="handleAiEditApplied"
      />
      <TextureAiGeneratorPanel v-else :mob-id="mobId" :model="draft.model" @applied="handleTextureAiApplied" />
    </GDrawer>
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

.mob-editor__texture {
  flex: 1;
  min-height: 0;
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
