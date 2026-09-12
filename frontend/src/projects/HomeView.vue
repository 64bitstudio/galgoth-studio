<script setup lang="ts">
/**
 * Pantalla "Inicio" (ticket 071 -- rediseño con fidelidad visual estricta
 * a `requerimientos/1_req_rediseno_inicio/assets/rediseno.png`, VoBo del
 * Product Owner sobre el preview interactivo). Hasta este ticket, "/" y
 * "/projects" apuntaban al mismo `ProjectsDashboard.vue` (simplificación
 * consciente del ticket 021, documentada en su cabecera) -- este
 * componente reemplaza esa simplificación para "/": saludo + las dos CTA
 * principales (Crear con IA / Crear proyecto) + "Continuar trabajando"
 * (mobs recientes, cruzando TODOS los proyectos vía el endpoint nuevo
 * `GET /api/mobs/recent`, ticket 071) + "Proyectos recientes" (subset de
 * `listProjects()`, ya viene ordenado por `updatedAt` descendente).
 * `ProjectsDashboard.vue` ("Mis proyectos", listado completo) NO se toca.
 *
 * Los menús ⋮ de ambas secciones reusan EXACTAMENTE el mismo criterio de
 * acciones/diálogos que ya usan `ProjectsDashboard.vue` (proyectos) y
 * `ProjectDetail.vue` (mobs) -- un mob/proyecto "reciente" no es un
 * concepto distinto, solo una vista distinta del mismo dato.
 */
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import AiMobProjectPickerDialog from './AiMobProjectPickerDialog.vue'
import ConfirmDialog from '../design-system/components/ConfirmDialog.vue'
import GSidebar, { type GSidebarKey } from '../design-system/components/GSidebar.vue'
import IconChevron from '../design-system/icons/IconChevron.vue'
import IconNewProject from '../design-system/icons/IconNewProject.vue'
import IconSparkle from '../design-system/icons/IconSparkle.vue'
import HomeSectionHeader from './HomeSectionHeader.vue'
import MobRenameDialog from './MobRenameDialog.vue'
import ProjectNameModal from './ProjectNameModal.vue'
import RecentMobCard from './RecentMobCard.vue'
import RecentProjectCard from './RecentProjectCard.vue'
import heroAiBg from '../assets/home/button-mob-ia.png'
import heroProjectBg from '../assets/home/button-nuevo-proyecto.png'

/** `v-bind()` en `<style>` no admite un template literal inline con comas dentro de `url(...)` sin ambigüedad -- se arma el valor CSS completo acá y se referencia por nombre simple. */
const heroAiBgUrl = `url(${heroAiBg})`
const heroProjectBgUrl = `url(${heroProjectBg})`
import {
  ApiError,
  createProject,
  deleteProject,
  duplicateProject,
  listProjects,
  renameProject,
  type ProjectSummary,
} from './projectsApi'
import { deleteMob, listRecentMobs, renameMob, type RecentMobSummary } from './mobsApi'

/** La referencia (rediseno.png) muestra 3 mobs recientes y 2 proyectos recientes. */
const RECENT_PROJECTS_LIMIT = 2

const router = useRouter()

const recentMobs = ref<RecentMobSummary[]>([])
const projects = ref<ProjectSummary[]>([])
const loadError = ref<string | null>(null)
const actionError = ref<string | null>(null)

const recentProjects = computed(() => projects.value.slice(0, RECENT_PROJECTS_LIMIT))

async function loadAll(): Promise<void> {
  try {
    const [mobsResult, projectsResult] = await Promise.all([listRecentMobs(), listProjects()])
    recentMobs.value = mobsResult
    projects.value = projectsResult
    loadError.value = null
  } catch (error) {
    loadError.value = error instanceof ApiError ? error.message : 'No se pudo cargar Inicio.'
  }
}

onMounted(loadAll)

function handleSidebarSelect(key: GSidebarKey): void {
  if (key === 'home') {
    router.push('/')
  } else if (key === 'projects') {
    router.push('/projects')
  }
}

/** Ticket 069/039 -- mismo flujo de selección de proyecto que `ProjectsDashboard.vue` antes de entrar al wizard IA. */
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

/** "Crear nuevo proyecto" -- mismo diálogo/flujo que `ProjectsDashboard.vue`. */
const nameModal = ref<{ mode: 'create' | 'rename'; projectId?: string; initialName?: string; initialDescription?: string | null } | null>(null)
const nameModalBusy = ref(false)
const nameModalError = ref<string | null>(null)

function openCreateModal(): void {
  nameModalError.value = null
  nameModal.value = { mode: 'create' }
}

function openRenameProjectModal(projectId: string): void {
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
      await loadAll()
    }
    actionError.value = null
  } catch (error) {
    nameModalError.value = error instanceof ApiError ? error.message : 'La operación no se pudo completar.'
  } finally {
    nameModalBusy.value = false
  }
}

/** Menú ⋮ de "Proyectos recientes" -- mismas 4 acciones/diálogos que `ProjectsDashboard.vue`. */
const pendingDeleteProject = ref<ProjectSummary | null>(null)
const deleteProjectBusy = ref(false)
const deleteProjectError = ref<string | null>(null)
const duplicatingProjectId = ref<string | null>(null)

async function handleProjectAction(actionKey: string, projectId: string): Promise<void> {
  if (actionKey === 'rename') {
    openRenameProjectModal(projectId)
  } else if (actionKey === 'duplicate') {
    await handleDuplicateProject(projectId)
  } else if (actionKey === 'delete') {
    deleteProjectError.value = null
    pendingDeleteProject.value = projects.value.find((p) => p.id === projectId) ?? null
  }
}

async function handleDuplicateProject(projectId: string): Promise<void> {
  if (duplicatingProjectId.value) {
    return // bloquea doble submit -- mismo criterio que ProjectsDashboard.vue
  }
  duplicatingProjectId.value = projectId
  try {
    await duplicateProject(projectId)
    actionError.value = null
    await loadAll()
  } catch (error) {
    actionError.value = error instanceof ApiError ? error.message : 'No se pudo duplicar el proyecto.'
  } finally {
    duplicatingProjectId.value = null
  }
}

async function confirmDeleteProject(): Promise<void> {
  if (!pendingDeleteProject.value || deleteProjectBusy.value) {
    return
  }
  deleteProjectBusy.value = true
  deleteProjectError.value = null
  try {
    await deleteProject(pendingDeleteProject.value.id)
    pendingDeleteProject.value = null
    actionError.value = null
    await loadAll()
  } catch (error) {
    deleteProjectError.value = error instanceof ApiError ? error.message : 'No se pudo eliminar el proyecto.'
  } finally {
    deleteProjectBusy.value = false
  }
}

function cancelDeleteProject(): void {
  if (deleteProjectBusy.value) {
    return
  }
  pendingDeleteProject.value = null
}

function openProject(projectId: string): void {
  router.push(`/projects/${projectId}`)
}

/** Menú ⋮ de "Continuar trabajando" -- mismas 3 acciones/diálogos que `ProjectDetail.vue`. */
const renamingMob = ref<RecentMobSummary | null>(null)
const renameMobBusy = ref(false)
const renameMobError = ref<string | null>(null)

const pendingDeleteMob = ref<RecentMobSummary | null>(null)
const deleteMobBusy = ref(false)
const deleteMobError = ref<string | null>(null)

function openMob(mobId: string): void {
  const mob = recentMobs.value.find((m) => m.id === mobId)
  if (mob) {
    router.push(`/projects/${mob.projectId}/mobs/${mob.id}/edit`)
  }
}

function handleMobAction(actionKey: string, mobId: string): void {
  const mob = recentMobs.value.find((m) => m.id === mobId)
  if (!mob) {
    return
  }
  if (actionKey === 'rename') {
    renameMobError.value = null
    renamingMob.value = mob
  } else if (actionKey === 'export') {
    router.push(`/projects/${mob.projectId}/mobs/${mob.id}/export`)
  } else if (actionKey === 'delete') {
    deleteMobError.value = null
    pendingDeleteMob.value = mob
  }
}

async function confirmRenameMob(name: string): Promise<void> {
  if (!renamingMob.value || renameMobBusy.value) {
    return
  }
  renameMobBusy.value = true
  renameMobError.value = null
  try {
    await renameMob(renamingMob.value.id, name)
    renamingMob.value = null
    actionError.value = null
    await loadAll()
  } catch (error) {
    renameMobError.value = error instanceof ApiError ? error.message : 'No se pudo renombrar el mob.'
  } finally {
    renameMobBusy.value = false
  }
}

function cancelRenameMob(): void {
  if (renameMobBusy.value) {
    return
  }
  renamingMob.value = null
}

async function confirmDeleteMob(): Promise<void> {
  if (!pendingDeleteMob.value || deleteMobBusy.value) {
    return
  }
  deleteMobBusy.value = true
  deleteMobError.value = null
  try {
    await deleteMob(pendingDeleteMob.value.id)
    pendingDeleteMob.value = null
    actionError.value = null
    await loadAll()
  } catch (error) {
    deleteMobError.value = error instanceof ApiError ? error.message : 'No se pudo eliminar el mob.'
  } finally {
    deleteMobBusy.value = false
  }
}

function cancelDeleteMob(): void {
  if (deleteMobBusy.value) {
    return
  }
  pendingDeleteMob.value = null
}
</script>

<template>
  <div class="home-shell">
    <GSidebar active="home" @select="handleSidebarSelect" />
    <main class="home app-scroll">
      <h1 class="home__greeting">Buenos días</h1>
      <p class="home__subtitle">¿Qué quieres crear hoy?</p>

      <p v-if="loadError" class="home__error">{{ loadError }}</p>
      <p v-if="actionError" class="home__error">{{ actionError }}</p>

      <div class="home__hero-row">
        <button type="button" class="home__hero-card home__hero-card--ai" @click="openAiWizard">
          <span class="home__hero-eyebrow home__hero-eyebrow--ai"><IconSparkle :size="13" />Crea con IA</span>
          <span class="home__hero-title">Crear un mob con IA</span>
          <span class="home__hero-desc">Convierte una imagen en un modelo de Minecraft.</span>
          <span class="home__hero-cta home__hero-cta--primary">Empezar ahora<IconChevron :size="16" /></span>
        </button>

        <button type="button" class="home__hero-card home__hero-card--project" @click="openCreateModal">
          <span class="home__hero-eyebrow"><IconNewProject :size="13" />Nuevo proyecto</span>
          <span class="home__hero-title">Crear nuevo proyecto</span>
          <span class="home__hero-desc">Organiza varios mobs dentro de un mismo proyecto.</span>
          <span class="home__hero-cta home__hero-cta--secondary">Crear proyecto<IconChevron :size="16" /></span>
        </button>
      </div>

      <section class="home__section">
        <HomeSectionHeader title="Continuar trabajando" to="/projects" />
        <div v-if="recentMobs.length === 0" class="home__empty">
          <p>Aún no has creado ningún mob.</p>
          <button type="button" class="home__empty-cta" @click="openAiWizard"><IconSparkle :size="16" />Crear mi primer mob con IA</button>
        </div>
        <div v-else class="home__mob-row">
          <RecentMobCard v-for="mob in recentMobs" :key="mob.id" :mob="mob" @open="openMob" @action="handleMobAction" />
        </div>
      </section>

      <section class="home__section">
        <HomeSectionHeader title="Proyectos recientes" to="/projects" />
        <div v-if="recentProjects.length === 0" class="home__empty">
          <p>Todavía no tienes proyectos.</p>
          <button type="button" class="home__empty-cta" @click="openCreateModal"><IconNewProject :size="16" />Crear proyecto</button>
        </div>
        <div v-else class="home__project-row">
          <RecentProjectCard v-for="project in recentProjects" :key="project.id" :project="project" @open="openProject" @action="handleProjectAction" />
        </div>
      </section>
    </main>

    <Transition name="app-dialog">
      <AiMobProjectPickerDialog
        v-if="showAiProjectPicker"
        :projects="projects"
        :busy="aiProjectPickerBusy"
        :error="aiProjectPickerError"
        @confirm="confirmAiProjectPicker"
        @cancel="closeAiProjectPicker"
      />
    </Transition>

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
        v-if="pendingDeleteProject"
        title="Eliminar proyecto"
        :message="`¿Eliminar el proyecto &quot;${pendingDeleteProject.name}&quot;? Esta acción no se puede deshacer.`"
        confirm-label="Eliminar"
        danger
        :busy="deleteProjectBusy"
        :error="deleteProjectError"
        @confirm="confirmDeleteProject"
        @cancel="cancelDeleteProject"
      />
    </Transition>

    <Transition name="app-dialog">
      <MobRenameDialog
        v-if="renamingMob"
        :initial-name="renamingMob.name"
        :busy="renameMobBusy"
        :error="renameMobError"
        @confirm="confirmRenameMob"
        @cancel="cancelRenameMob"
      />
    </Transition>

    <Transition name="app-dialog">
      <ConfirmDialog
        v-if="pendingDeleteMob"
        title="Eliminar mob"
        :message="`¿Eliminar el mob &quot;${pendingDeleteMob.name}&quot;? Esta acción no se puede deshacer.`"
        confirm-label="Eliminar"
        danger
        :busy="deleteMobBusy"
        :error="deleteMobError"
        @confirm="confirmDeleteMob"
        @cancel="cancelDeleteMob"
      />
    </Transition>
  </div>
</template>

<style scoped>
.home-shell {
  display: flex;
  height: 100vh;
}

.home {
  /* Curva "ease-out" suave para hover de cards/botones de Inicio (mismo tipo de curva ya usada en GDrawer.vue) -- más agradable que el `var(--transition-fast)` lineal-ish que usa el resto de controles pequeños de la app. */
  --home-ease: cubic-bezier(0.16, 1, 0.3, 1);
  flex: 1;
  padding: var(--space-6) var(--space-8);
  overflow: auto;
}

.home__greeting {
  margin: 0;
  font-size: var(--text-xl);
}

.home__subtitle {
  margin: var(--space-1) 0 var(--space-6);
  color: var(--muted);
}

.home__error {
  color: var(--danger);
}

/* ---------- hero row ---------- */
.home__hero-row {
  display: flex;
  gap: var(--space-4);
  margin-bottom: var(--space-8);
}

.home__hero-card {
  position: relative;
  overflow: hidden;
  flex-basis: 58%;
  min-height: 200px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: var(--space-2);
  padding: var(--space-5) var(--space-6);
  border-radius: var(--radius-lg);
  border: var(--border-width) solid var(--border);
  background-color: var(--panel);
  background-repeat: no-repeat;
  background-position: right center;
  background-size: cover;
  color: var(--text);
  text-align: left;
  cursor: pointer;
  font: inherit;
  transition:
    border-color 220ms var(--home-ease),
    transform 220ms var(--home-ease),
    box-shadow 220ms var(--home-ease);
}

.home__hero-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-md);
}

.home__hero-card:focus-visible {
  outline: none;
  box-shadow: var(--focus-ring), var(--shadow-md);
}

.home__hero-card--ai {
  border-color: var(--accent);
  background-image: linear-gradient(90deg, rgba(6, 15, 11, 0.55) 0%, rgba(6, 15, 11, 0.12) 62%, rgba(6, 15, 11, 0) 100%), v-bind(heroAiBgUrl);
}

.home__hero-card--project {
  flex-basis: 42%;
  background-image: linear-gradient(90deg, rgba(6, 10, 18, 0.6) 0%, rgba(6, 10, 18, 0.18) 60%, rgba(6, 10, 18, 0) 100%), v-bind(heroProjectBgUrl);
}

.home__hero-eyebrow {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  width: fit-content;
  font-size: var(--text-xs);
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--muted);
}

.home__hero-eyebrow--ai {
  color: var(--accent);
}

.home__hero-title {
  font-size: var(--text-xl);
  font-weight: 700;
  max-width: 60%;
}

.home__hero-desc {
  color: var(--muted);
  font-size: var(--text-md);
  max-width: 56%;
  line-height: 1.45;
}

.home__hero-cta {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  min-height: var(--hit-target-min);
  width: fit-content;
  padding: 0 var(--space-4);
  margin-top: var(--space-2);
  border-radius: var(--radius-md);
  font-size: var(--text-base);
  font-weight: 600;
  transition: background-color 220ms var(--home-ease);
}

.home__hero-cta--primary {
  background: var(--accent);
  color: var(--accent-ink);
}

.home__hero-card--ai:hover .home__hero-cta--primary {
  background: var(--accent-hover);
}

.home__hero-cta--secondary {
  background: var(--surface-2);
  border: var(--border-width) solid var(--border);
  color: var(--text);
}

/* ---------- secciones ---------- */
.home__section {
  margin-bottom: var(--space-8);
}

/* `minmax` con un tope fijo (no `1fr`) a propósito: con pocas cards no deben estirarse a ocupar todo el ancho disponible -- crecen hasta el tope y el resto queda vacío, en vez de verse gigantes. */
.home__mob-row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(260px, 360px));
  gap: var(--space-4);
}

.home__project-row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(320px, 420px));
  gap: var(--space-4);
}

.home__empty {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: var(--space-3);
  padding: var(--space-5);
  background: var(--panel);
  border: var(--border-width) dashed var(--border);
  border-radius: var(--radius-lg);
  color: var(--muted);
}

.home__empty-cta {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  min-height: var(--hit-target-min);
  padding: 0 var(--space-4);
  background: var(--accent-soft);
  border: var(--border-width) solid var(--accent);
  border-radius: var(--radius-md);
  color: var(--accent);
  font-weight: 600;
  cursor: pointer;
  transition:
    background-color 220ms var(--home-ease),
    color 220ms var(--home-ease);
}

.home__empty-cta:hover {
  background: var(--accent);
  color: var(--accent-ink);
}

@media (max-width: 980px) {
  .home__hero-row {
    flex-direction: column;
  }

  .home__mob-row {
    grid-template-columns: 1fr;
  }
}
</style>
