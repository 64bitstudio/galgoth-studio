<script setup lang="ts">
/**
 * Dashboard "Mis proyectos" (HU-01/HU-02, ticket 021; rediseño de
 * fidelidad visual estricta, ticket 072, VoBo del PO sobre el preview
 * interactivo). Historia de la pantalla:
 * - Ticket 021: listado completo, simplificación consciente (compartía
 *   pantalla con "Inicio", que entonces no existía por separado).
 * - Ticket 039: agrega las CTA "Crear un mob con IA"/"Crear nuevo
 *   proyecto" arriba del listado (más un saludo "Buenos días").
 * - Ticket 071 (rediseño de Inicio): "Inicio" pasa a tener su propia
 *   pantalla real (`HomeView.vue`, en "/") -- ESTE componente, ahora
 *   solo en "/projects", sigue siendo el saludo+CTA+listado heredados de
 *   039, sin tocar (decisión explícita de ese ticket: solo Inicio
 *   estaba en su alcance).
 * - Ticket 072 (este): la referencia visual rediseña "Mis proyectos" con
 *   su propia identidad -- título+subtítulo (no el saludo de Inicio),
 *   buscador, botón "+ Nuevo proyecto", badge de estado por card
 *   (`ProjectCard.vue`) y una card "Nuevo proyecto" punteada al final
 *   de la grilla. El flujo "Crear un mob con IA" (con su selector de
 *   proyecto) YA NO vive acá -- la referencia no lo incluye, y ese
 *   punto de entrada ya existe completo en Inicio (ticket 071).
 * - Post-073 (pedido explícito del PO tras revisar el detalle de
 *   proyecto en vivo): agrega el mismo breadcrumb "Galgoth Studio >
 *   Mis proyectos" que ya se implementó en `ProjectDetail.vue` --
 *   mismo patrón visual (`IconChevron`), sin nivel intermedio porque
 *   esta pantalla YA ES el segundo nivel de esa jerarquía.
 */
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import ConfirmDialog from '../design-system/components/ConfirmDialog.vue'
import GSidebar, { type GSidebarKey } from '../design-system/components/GSidebar.vue'
import IconChevron from '../design-system/icons/IconChevron.vue'
import IconPlus from '../design-system/icons/IconPlus.vue'
import IconSearch from '../design-system/icons/IconSearch.vue'
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
const searchQuery = ref('')

const filteredProjects = computed(() => {
  const query = searchQuery.value.trim().toLowerCase()
  if (!query) {
    return projects.value
  }
  return projects.value.filter((project) => project.name.toLowerCase().includes(query))
})

const nameModal = ref<{ mode: 'create' | 'rename'; projectId?: string; initialName?: string; initialDescription?: string | null } | null>(null)
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
  nameModal.value = { mode: 'rename', projectId, initialName: project?.name, initialDescription: project?.description }
}

function closeNameModal(): void {
  if (nameModalBusy.value) {
    return
  }
  nameModal.value = null
}

async function confirmNameModal(name: string, description: string | null): Promise<void> {
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
      await renameProject(nameModal.value.projectId, name, description)
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

/** Ticket 071 -- "Inicio" ya NO es sinónimo de "Mis proyectos": navega a "/" (HomeView.vue), no a este dashboard. "Explorar"/"Plantillas" no tienen pantalla todavía (no-op deliberado). */
function handleSidebarSelect(key: GSidebarKey): void {
  if (key === 'home') {
    router.push('/')
  } else if (key === 'projects') {
    router.push('/projects')
  }
}
</script>

<template>
  <div class="projects-dashboard-shell">
    <GSidebar active="projects" @select="handleSidebarSelect" />
    <main class="projects-dashboard app-scroll">
      <nav class="projects-dashboard__breadcrumb" aria-label="Ruta de navegación">
        <router-link to="/">Galgoth Studio</router-link>
        <IconChevron :size="12" />
        <span>Mis proyectos</span>
      </nav>

      <h1 class="projects-dashboard__title">Mis proyectos</h1>
      <p class="projects-dashboard__subtitle">Organiza y administra todos tus mundos y colecciones de mobs.</p>

      <div class="projects-dashboard__toolbar">
        <div class="projects-dashboard__search">
          <IconSearch :size="16" class="projects-dashboard__search-icon" />
          <input v-model="searchQuery" type="search" class="projects-dashboard__search-input" aria-label="Buscar proyectos" placeholder="Buscar proyectos..." />
        </div>
        <button type="button" class="projects-dashboard__new-btn" @click="openCreateModal">
          <IconPlus :size="16" />
          Nuevo proyecto
        </button>
      </div>

      <p v-if="loadError" class="projects-dashboard__error">{{ loadError }}</p>
      <p v-if="actionError" class="projects-dashboard__error">{{ actionError }}</p>
      <p v-if="!loadError && searchQuery && filteredProjects.length === 0" class="projects-dashboard__empty">
        Ningún proyecto coincide con "{{ searchQuery }}".
      </p>

      <div v-if="!loadError" class="projects-dashboard__grid">
        <ProjectCard v-for="project in filteredProjects" :key="project.id" :project="project" @open="openProject" @action="handleCardAction" />
        <button type="button" class="projects-dashboard__new-card" @click="openCreateModal">
          <span class="projects-dashboard__new-card-icon"><IconPlus :size="20" /></span>
          <span class="projects-dashboard__new-card-title">Nuevo proyecto</span>
          <span class="projects-dashboard__new-card-desc">Crea un nuevo mundo de mobs desde cero</span>
        </button>
      </div>
    </main>

    <Transition name="app-dialog">
      <ProjectNameModal
        v-if="nameModal"
        :mode="nameModal.mode"
        :initial-name="nameModal.initialName"
        :initial-description="nameModal.initialDescription"
        :busy="nameModalBusy"
        :error="nameModalError"
        @confirm="confirmNameModal"
        @cancel="closeNameModal"
      />
    </Transition>

    <Transition name="app-dialog">
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
    </Transition>
  </div>
</template>

<style scoped>
.projects-dashboard-shell {
  display: flex;
  height: 100vh;
}

.projects-dashboard {
  flex: 1;
  padding: var(--space-6) var(--space-8);
  overflow: auto;
}

.projects-dashboard__breadcrumb {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: var(--text-sm);
  color: var(--muted);
  margin-bottom: var(--space-4);
}

.projects-dashboard__breadcrumb a {
  color: var(--muted);
  text-decoration: none;
  transition: color 160ms cubic-bezier(0.16, 1, 0.3, 1);
}

.projects-dashboard__breadcrumb a:hover {
  color: var(--text);
}

.projects-dashboard__breadcrumb span {
  color: var(--text);
  font-weight: 600;
}

.projects-dashboard__title {
  margin: 0;
  font-size: var(--text-2xl);
  font-weight: 800;
  letter-spacing: -0.01em;
}

.projects-dashboard__subtitle {
  margin: var(--space-1) 0 var(--space-6);
  color: var(--muted);
  font-size: var(--text-md);
}

.projects-dashboard__toolbar {
  display: flex;
  align-items: center;
  gap: var(--space-4);
  margin-bottom: var(--space-6);
}

.projects-dashboard__search {
  position: relative;
  flex: 1;
  max-width: 420px;
}

.projects-dashboard__search-icon {
  position: absolute;
  left: var(--space-3);
  top: 50%;
  transform: translateY(-50%);
  color: var(--muted);
  pointer-events: none;
}

.projects-dashboard__search-input {
  width: 100%;
  min-height: var(--hit-target-min);
  padding: 0 var(--space-3) 0 38px;
  background: var(--surface);
  border: var(--border-width) solid var(--border);
  border-radius: var(--radius-md);
  color: var(--text);
  font-size: var(--text-base);
}

.projects-dashboard__search-input:hover {
  border-color: var(--muted);
}

.projects-dashboard__search-input:focus-visible {
  border-color: var(--accent);
}

.projects-dashboard__new-btn {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  min-height: var(--hit-target-min);
  padding: 0 var(--space-4);
  margin-left: auto;
  border-radius: var(--radius-md);
  border: none;
  background: var(--accent);
  color: var(--accent-ink);
  font-weight: 700;
  cursor: pointer;
  white-space: nowrap;
  transition:
    background-color 220ms cubic-bezier(0.16, 1, 0.3, 1),
    transform 220ms cubic-bezier(0.16, 1, 0.3, 1);
}

.projects-dashboard__new-btn:hover {
  background: var(--accent-hover);
}

.projects-dashboard__new-btn:active {
  transform: scale(0.98);
}

.projects-dashboard__grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(300px, 380px));
  gap: var(--space-4);
}

.projects-dashboard__new-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  gap: var(--space-3);
  min-height: 220px;
  padding: var(--space-6);
  background: transparent;
  border: 2px dashed var(--border);
  border-radius: var(--radius-lg);
  color: var(--muted);
  cursor: pointer;
  font: inherit;
  transition: color 200ms ease, border-color 200ms ease;
}

.projects-dashboard__new-card:hover {
  color: var(--accent);
  border-color: var(--accent);
}

.projects-dashboard__new-card-icon {
  width: 48px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--radius-md);
  border: var(--border-width) solid var(--border);
  color: inherit;
}

.projects-dashboard__new-card-title {
  font-weight: 700;
  font-size: var(--text-md);
  color: var(--text);
}

.projects-dashboard__new-card-desc {
  font-size: var(--text-sm);
  max-width: 220px;
}

.projects-dashboard__empty {
  color: var(--muted);
  margin-bottom: var(--space-4);
}

.projects-dashboard__error {
  color: var(--danger);
}
</style>
