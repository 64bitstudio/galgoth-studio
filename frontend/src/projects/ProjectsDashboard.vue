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
 * La tarjeta CTA "Crear un mob con IA" del mockup se muestra pero
 * deshabilitada (sin pipeline de IA todavía, épica futura) -- "Proyecto
 * vacío" es la única CTA funcional de este ticket, abre el modal de
 * nombre (HU-01).
 */
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import GButton from '../design-system/components/GButton.vue'
import GPanel from '../design-system/components/GPanel.vue'
import GSidebar, { type GSidebarKey } from '../design-system/components/GSidebar.vue'
import IconNewProject from '../design-system/icons/IconNewProject.vue'
import IconImage from '../design-system/icons/IconImage.vue'
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
const pendingDelete = ref<ProjectSummary | null>(null)

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
  nameModal.value = { mode: 'create' }
}

function openRenameModal(projectId: string): void {
  const project = projects.value.find((p) => p.id === projectId)
  nameModal.value = { mode: 'rename', projectId, initialName: project?.name }
}

function closeNameModal(): void {
  nameModal.value = null
}

async function confirmNameModal(name: string): Promise<void> {
  if (!nameModal.value) {
    return
  }
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
    actionError.value = error instanceof ApiError ? error.message : 'La operación no se pudo completar.'
  }
}

async function handleCardAction(actionKey: string, projectId: string): Promise<void> {
  if (actionKey === 'rename') {
    openRenameModal(projectId)
  } else if (actionKey === 'duplicate') {
    await handleDuplicate(projectId)
  } else if (actionKey === 'delete') {
    pendingDelete.value = projects.value.find((p) => p.id === projectId) ?? null
  }
}

async function handleDuplicate(projectId: string): Promise<void> {
  try {
    await duplicateProject(projectId)
    actionError.value = null
    await loadProjects()
  } catch (error) {
    actionError.value = error instanceof ApiError ? error.message : 'No se pudo duplicar el proyecto.'
  }
}

async function confirmDelete(): Promise<void> {
  if (!pendingDelete.value) {
    return
  }
  try {
    await deleteProject(pendingDelete.value.id)
    pendingDelete.value = null
    actionError.value = null
    await loadProjects()
  } catch (error) {
    actionError.value = error instanceof ApiError ? error.message : 'No se pudo eliminar el proyecto.'
  }
}

function cancelDelete(): void {
  pendingDelete.value = null
}

function openProject(projectId: string): void {
  router.push(`/projects/${projectId}`)
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
    <main class="projects-dashboard">
      <h1 class="projects-dashboard__greeting">Buenos días</h1>
      <p class="projects-dashboard__subtitle">¿Qué quieres crear hoy?</p>

      <div class="projects-dashboard__cta-row">
        <button type="button" class="projects-dashboard__cta projects-dashboard__cta--primary" @click="openCreateModal">
          <IconNewProject :size="28" />
          <span class="projects-dashboard__cta-title">Proyecto vacío</span>
          <span class="projects-dashboard__cta-subtitle">Empieza desde cero</span>
        </button>
        <button type="button" class="projects-dashboard__cta" disabled>
          <IconImage :size="28" class="projects-dashboard__cta-icon" />
          <span class="projects-dashboard__cta-title">Crear un mob con IA</span>
          <span class="projects-dashboard__cta-subtitle">Convierte una imagen en un modelo de Minecraft</span>
          <span class="projects-dashboard__cta-hint">Disponible en una fase futura</span>
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
      @confirm="confirmNameModal"
      @cancel="closeNameModal"
    />

    <div v-if="pendingDelete" class="projects-dashboard__delete-backdrop" @click.self="cancelDelete">
      <GPanel class="projects-dashboard__delete-confirm">
        <p>¿Eliminar el proyecto "{{ pendingDelete.name }}"? Esta acción no se puede deshacer.</p>
        <div class="projects-dashboard__delete-actions">
          <GButton variant="ghost" @click="cancelDelete">Cancelar</GButton>
          <GButton variant="danger" @click="confirmDelete">Eliminar</GButton>
        </div>
      </GPanel>
    </div>
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

.projects-dashboard__cta--primary {
  background: var(--accent-soft);
  border-color: var(--accent);
  color: var(--accent);
}

.projects-dashboard__cta:disabled {
  cursor: not-allowed;
  color: var(--muted);
}

.projects-dashboard__cta-icon {
  color: var(--muted);
  margin-bottom: var(--space-1);
}

.projects-dashboard__cta--primary .projects-dashboard__cta-icon {
  color: var(--accent);
}

.projects-dashboard__cta-title {
  font-weight: 600;
  color: var(--text);
}

.projects-dashboard__cta-subtitle {
  font-size: var(--text-sm);
  color: var(--muted);
}

.projects-dashboard__cta-hint {
  font-size: var(--text-xs);
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

.projects-dashboard__delete-backdrop {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.6);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 100;
}

.projects-dashboard__delete-confirm {
  width: min(360px, 90vw);
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.projects-dashboard__delete-actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--space-2);
}
</style>
