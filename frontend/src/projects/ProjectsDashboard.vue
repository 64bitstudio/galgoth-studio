<script setup lang="ts">
/**
 * Dashboard "Mis proyectos" (HU-01/HU-02, mockup 01 -- ticket 021).
 * Simplificación consciente, documentada (no una reinterpretación
 * silenciosa): el mockup separa "Inicio" (saludo + "Proyectos
 * recientes" + link "Ver todos") de "Mis proyectos" (listado completo),
 * pero el AC de este ticket solo describe la segunda -- se implementa
 * un único dashboard con el listado COMPLETO, y tanto "Inicio" como
 * "Mis proyectos" en el sidebar apuntan aquí por ahora.
 *
 * Ticket 039 (corrección de producto): "Crear un mob con IA" pasa a ser
 * la CTA PRINCIPAL, habilitada, primera opción visual, acento mint --
 * el flujo de generación IA ya existe y es funcionalidad central del
 * producto, no "una fase futura". "Crear nuevo proyecto" (antes
 * "Proyecto vacío") queda como alternativa secundaria. El diálogo de
 * "Eliminar" pasa de un `GPanel`+backdrop ad-hoc a `ConfirmDialog.vue`
 * (design system), con loading real y bloqueo de doble submit --
 * `deleteBusy`/`duplicatingId` evitan reintentar la misma operación
 * mientras el backend todavía no respondió.
 */
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import AiMobProjectPickerDialog from './AiMobProjectPickerDialog.vue'
import ConfirmDialog from '../design-system/components/ConfirmDialog.vue'
import GSidebar, { type GSidebarKey } from '../design-system/components/GSidebar.vue'
import IconNewProject from '../design-system/icons/IconNewProject.vue'
import IconSparkle from '../design-system/icons/IconSparkle.vue'
import ProjectCard from './ProjectCard.vue'
import ProjectNameModal from './ProjectNameModal.vue'
import {
  ApiError,
  createProject,
  deleteProject,
  duplicateProject,
  listProjects,
  renameProject,
  type ProjectSummary,
} from './projectsApi'

const router = useRouter()

const projects = ref<ProjectSummary[]>([])
const loadError = ref<string | null>(null)
const actionError = ref<string | null>(null)

const nameModal = ref<{ mode: 'create' | 'rename'; projectId?: string; initialName?: string } | null>(null)
const nameModalBusy = ref(false)
const nameModalError = ref<string | null>(null)
const pendingDelete = ref<ProjectSummary | null>(null)
const deleteBusy = ref(false)
const deleteError = ref<string | null>(null)
const duplicatingId = ref<string | null>(null)

async function loadProjects(): Promise<void> {
  try {
    projects.value = await listProjects()
    loadError.value = null
  } catch (error) {
    loadError.value = error instanceof ApiError ? error.message : 'No se pudieron cargar los proyectos.'
  }
}

onMounted(loadProjects)

function openCreateModal(): void {
  nameModalError.value = null
  nameModal.value = { mode: 'create' }
}

function openRenameModal(projectId: string): void {
  const project = projects.value.find((p) => p.id === projectId)
  nameModalError.value = null
  nameModal.value = { mode: 'rename', projectId, initialName: project?.name }
}

function closeNameModal(): void {
  if (nameModalBusy.value) {
    return
  }
  nameModal.value = null
}

async function confirmNameModal(name: string): Promise<void> {
  if (!nameModal.value || nameModalBusy.value) {
    return
  }
  nameModalBusy.value = true
  nameModalError.value = null
  try {
    if (nameModal.value.mode === 'create') {
      const created = await createProject(name)
      nameModal.value = null
      await router.push(`/projects/${created.id}`)
    } else if (nameModal.value.projectId) {
      await renameProject(nameModal.value.projectId, name)
      nameModal.value = null
      await loadProjects()
    }
    actionError.value = null
  } catch (error) {
    nameModalError.value = error instanceof ApiError ? error.message : 'La operación no se pudo completar.'
  } finally {
    nameModalBusy.value = false
  }
}

async function handleCardAction(actionKey: string, projectId: string): Promise<void> {
  if (actionKey === 'rename') {
    openRenameModal(projectId)
  } else if (actionKey === 'duplicate') {
    await handleDuplicate(projectId)
  } else if (actionKey === 'delete') {
    deleteError.value = null
    pendingDelete.value = projects.value.find((p) => p.id === projectId) ?? null
  }
}

async function handleDuplicate(projectId: string): Promise<void> {
  if (duplicatingId.value) {
    return // bloquea doble submit real -- un segundo clic mientras el primero todavía no respondió no dispara otra duplicación.
  }
  duplicatingId.value = projectId
  try {
    await duplicateProject(projectId)
    actionError.value = null
    await loadProjects()
  } catch (error) {
    actionError.value = error instanceof ApiError ? error.message : 'No se pudo duplicar el proyecto.'
  } finally {
    duplicatingId.value = null
  }
}

async function confirmDelete(): Promise<void> {
  if (!pendingDelete.value || deleteBusy.value) {
    return
  }
  deleteBusy.value = true
  deleteError.value = null
  try {
    await deleteProject(pendingDelete.value.id)
    pendingDelete.value = null
    actionError.value = null
    await loadProjects()
  } catch (error) {
    deleteError.value = error instanceof ApiError ? error.message : 'No se pudo eliminar el proyecto.'
  } finally {
    deleteBusy.value = false
  }
}

function cancelDelete(): void {
  if (deleteBusy.value) {
    return
  }
  pendingDelete.value = null
}

function openProject(projectId: string): void {
  router.push(`/projects/${projectId}`)
}

const showAiProjectPicker = ref(false)
const aiProjectPickerBusy = ref(false)
const aiProjectPickerError = ref<string | null>(null)

function openAiWizard(): void {
  aiProjectPickerError.value = null
  showAiProjectPicker.value = true
}

function closeAiProjectPicker(): void {
  if (aiProjectPickerBusy.value) {
    return
  }
  showAiProjectPicker.value = false
}

async function confirmAiProjectPicker(choice: { projectId: string } | { newProjectName: string }): Promise<void> {
  if (aiProjectPickerBusy.value) {
    return
  }
  aiProjectPickerBusy.value = true
  aiProjectPickerError.value = null
  try {
    const projectId = 'projectId' in choice ? choice.projectId : (await createProject(choice.newProjectName)).id
    showAiProjectPicker.value = false
    await router.push(`/projects/${projectId}/mobs/new-ai`)
  } catch (error) {
    aiProjectPickerError.value = error instanceof ApiError ? error.message : 'No se pudo continuar con la generación IA.'
  } finally {
    aiProjectPickerBusy.value = false
  }
}

/** "Explorar"/"Plantillas" no tienen pantalla todavía (no-op deliberado) -- "Inicio"/"Mis proyectos" ya apuntan aquí, per el comentario de cabecera. */
function handleSidebarSelect(key: GSidebarKey): void {
  if (key === 'home' || key === 'projects') {
    router.push('/projects')
  }
}
</script>

<template>
  <div class="projects-dashboard-shell">
    <GSidebar active="projects" @select="handleSidebarSelect" />
    <main class="projects-dashboard app-scroll">
      <h1 class="projects-dashboard__greeting">Buenos días</h1>
      <p class="projects-dashboard__subtitle">¿Qué quieres crear hoy?</p>

      <div class="projects-dashboard__cta-row">
        <button type="button" class="projects-dashboard__cta projects-dashboard__cta--primary" @click="openAiWizard">
          <IconSparkle :size="28" />
          <span class="projects-dashboard__cta-title">Crear un mob con IA</span>
          <span class="projects-dashboard__cta-subtitle">Convierte una imagen en un modelo de Minecraft</span>
        </button>
        <button type="button" class="projects-dashboard__cta projects-dashboard__cta--secondary" @click="openCreateModal">
          <IconNewProject :size="28" class="projects-dashboard__cta-icon" />
          <span class="projects-dashboard__cta-title">Crear nuevo proyecto</span>
          <span class="projects-dashboard__cta-subtitle">Organiza varios mobs dentro de un mismo proyecto.</span>
        </button>
      </div>

      <p v-if="actionError" class="projects-dashboard__error">{{ actionError }}</p>

      <h2 class="projects-dashboard__section-title">Mis proyectos</h2>
      <p v-if="loadError" class="projects-dashboard__error">{{ loadError }}</p>
      <p v-else-if="projects.length === 0" class="projects-dashboard__empty">Todavía no tienes proyectos -- crea el primero arriba.</p>
      <div v-else class="projects-dashboard__grid">
        <ProjectCard v-for="project in projects" :key="project.id" :project="project" @open="openProject" @action="handleCardAction" />
      </div>
    </main>

    <ProjectNameModal
      v-if="nameModal"
      :mode="nameModal.mode"
      :initial-name="nameModal.initialName"
      :busy="nameModalBusy"
      :error="nameModalError"
      @confirm="confirmNameModal"
      @cancel="closeNameModal"
    />

    <ConfirmDialog
      v-if="pendingDelete"
      title="Eliminar proyecto"
      :message="`¿Eliminar el proyecto &quot;${pendingDelete.name}&quot;? Esta acción no se puede deshacer.`"
      confirm-label="Eliminar"
      danger
      :busy="deleteBusy"
      :error="deleteError"
      @confirm="confirmDelete"
      @cancel="cancelDelete"
    />

    <AiMobProjectPickerDialog
      v-if="showAiProjectPicker"
      :projects="projects"
      :busy="aiProjectPickerBusy"
      :error="aiProjectPickerError"
      @confirm="confirmAiProjectPicker"
      @cancel="closeAiProjectPicker"
    />
  </div>
</template>

<style scoped>
.projects-dashboard-shell {
  display: flex;
  height: 100vh;
}

.projects-dashboard {
  flex: 1;
  padding: var(--space-6);
  overflow: auto;
}

.projects-dashboard__greeting {
  margin: 0;
  font-size: var(--text-xl);
}

.projects-dashboard__subtitle {
  margin: var(--space-1) 0 var(--space-4);
  color: var(--muted);
}

.projects-dashboard__cta-row {
  display: flex;
  gap: var(--space-4);
  margin-bottom: var(--space-6);
}

.projects-dashboard__cta {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: var(--space-1);
  padding: var(--space-4);
  border-radius: var(--radius-lg);
  border: var(--border-width) solid var(--border);
  background: var(--panel);
  color: var(--text);
  cursor: pointer;
  text-align: left;
  max-width: 320px;
}

.projects-dashboard__cta:hover {
  border-color: var(--accent);
}

.projects-dashboard__cta--primary {
  background: var(--accent-soft);
  border-color: var(--accent);
  color: var(--accent);
}

.projects-dashboard__cta--secondary {
  color: var(--muted);
}

.projects-dashboard__cta-icon {
  color: var(--muted);
  margin-bottom: var(--space-1);
}

.projects-dashboard__cta-title {
  font-weight: 600;
  color: var(--text);
}

.projects-dashboard__cta--primary .projects-dashboard__cta-title {
  color: var(--accent);
}

.projects-dashboard__cta-subtitle {
  font-size: var(--text-sm);
  color: var(--muted);
}

.projects-dashboard__section-title {
  font-size: var(--text-md);
  margin: 0 0 var(--space-3);
}

.projects-dashboard__grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: var(--space-4);
}

.projects-dashboard__empty {
  color: var(--muted);
}

.projects-dashboard__error {
  color: var(--danger);
}
</style>
