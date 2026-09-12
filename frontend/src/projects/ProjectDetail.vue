<script setup lang="ts">
/**
 * Detalle de proyecto (HU-04, mockup 12 -- ticket 022). Reemplaza el
 * placeholder mínimo del ticket 021 (`ProjectDetailPlaceholder.vue`,
 * borrado): header (nombre + conteo + "Agregar mob") + buscador +
 * grid de mobs + tarjeta CTA "+ Nuevo mob" al final del grid.
 *
 * El filtro del buscador (AC #3) es client-side sobre la lista ya
 * cargada -- sin parámetro de búsqueda en el backend, consistente con
 * que el dashboard de proyectos (021) tampoco lo necesitó: no hay
 * ningún requisito de escala/paginación todavía que lo justifique.
 *
 * Ticket 039 (corrección de producto): "Crear con IA" pasa a ser la CTA
 * primaria (antes un link con estilo secundario, "Agregar mob" era la
 * `GButton` primaria -- exactamente al revés de lo pedido: la IA es el
 * diferencial del producto). Se agrega edición rápida del nombre del
 * proyecto (icono junto al título, reusa `ProjectNameModal` en modo
 * `rename`) y el menú ⋮ real de cada `MobCard` (Renombrar/Exportar/
 * Eliminar), con diálogos del design system y loading/bloqueo de doble
 * submit reales -- mismo criterio que `ProjectsDashboard.vue`.
 *
 * Ticket 073 (rediseño del detalle de proyecto, VoBo del PO sobre el
 * preview interactivo -- condición explícita: "NO uses componentes
 * nativos, utiliza componentes personalizados que hemos venido
 * trabajando, agrega transiciones... todo debe llevar transiciones"):
 * - Breadcrumb "Galgoth Studio > Mis proyectos > {nombre}".
 * - Header: descripción (campo nuevo real y editable, mismo lápiz que
 *   el nombre -- ver `ProjectNameModal.vue`) debajo de la meta-línea.
 * - Menú ⋮ de acciones DEL PROYECTO (Duplicar/Eliminar -- decisión del
 *   PO vía AskUserQuestion; Renombrar no está en el menú porque ya
 *   tiene su propio lápiz junto al título), mismo `GMenu.vue` que ya
 *   usa `ProjectCard.vue`/`MobCard.vue` -- ninguna lógica nueva de
 *   dropdown, reusa el componente existente.
 * - Toolbar con filtros de Tipo/Estado/Ordenar via `GSelect.vue`
 *   (NUNCA `<select>` nativo -- instrucción explícita del PO), mismos 5
 *   valores de `AddMobModal.vue` para tipo y los 3 estados reales de
 *   `MobStatus` para estado.
 * - `MobCard.vue` rediseñada (ver ese archivo) con el pill de estado
 *   superpuesto a la miniatura y el tipo de base en el footer.
 * - TODOS los diálogos (existentes y nuevos) envueltos en
 *   `<Transition name="app-dialog">` -- ninguno se salta la regla.
 */
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import ConfirmDialog from '../design-system/components/ConfirmDialog.vue'
import GPanel from '../design-system/components/GPanel.vue'
import GButton from '../design-system/components/GButton.vue'
import GMenu, { type GMenuItem } from '../design-system/components/GMenu.vue'
import GSelect, { type GSelectOption } from '../design-system/components/GSelect.vue'
import GSidebar, { type GSidebarKey } from '../design-system/components/GSidebar.vue'
import IconButton from '../design-system/components/IconButton.vue'
import IconChevron from '../design-system/icons/IconChevron.vue'
import IconEdit from '../design-system/icons/IconEdit.vue'
import IconSearch from '../design-system/icons/IconSearch.vue'
import IconPlus from '../design-system/icons/IconPlus.vue'
import IconSparkle from '../design-system/icons/IconSparkle.vue'
import { formatRelativeDate } from '../domain/relativeDate'
import AddMobModal from './AddMobModal.vue'
import MobCard from './MobCard.vue'
import MobRenameDialog from './MobRenameDialog.vue'
import ProjectNameModal from './ProjectNameModal.vue'
import {
  ApiError,
  deleteProject,
  duplicateProject,
  getProject,
  renameProject,
  type ProjectDetail as ProjectDetailDto,
} from './projectsApi'
import { createMob, deleteMob, listMobs, renameMob, type BaseType, type MobStatus, type MobSummary } from './mobsApi'

type TypeFilter = BaseType | 'all'
type StatusFilter = MobStatus | 'all'
type SortOption = 'recent' | 'name-asc' | 'name-desc'

const TYPE_OPTIONS: GSelectOption[] = [
  { value: 'all', label: 'Tipo' },
  { value: 'humanoid', label: 'Humanoide' },
  { value: 'arachnid', label: 'Arácnido' },
  { value: 'quadruped', label: 'Cuadrúpedo' },
  { value: 'flying', label: 'Volador' },
  { value: 'custom', label: 'Personalizado' },
]

const STATUS_OPTIONS: GSelectOption[] = [
  { value: 'all', label: 'Estado' },
  { value: 'draft', label: 'Draft' },
  { value: 'in_progress', label: 'En progreso' },
  { value: 'ready', label: 'Listo' },
]

const SORT_OPTIONS: GSelectOption[] = [
  { value: 'recent', label: 'Más recientes' },
  { value: 'name-asc', label: 'Nombre A-Z' },
  { value: 'name-desc', label: 'Nombre Z-A' },
]

const PROJECT_MENU_ITEMS: GMenuItem[] = [
  { key: 'duplicate', label: 'Duplicar' },
  { key: 'delete', label: 'Eliminar', danger: true },
]

const route = useRoute()
const router = useRouter()
const projectId = route.params.id as string

const project = ref<ProjectDetailDto | null>(null)
const mobs = ref<MobSummary[]>([])
const loadError = ref<string | null>(null)
const actionError = ref<string | null>(null)
const searchQuery = ref('')
const typeFilter = ref<TypeFilter>('all')
const statusFilter = ref<StatusFilter>('all')
const sortBy = ref<SortOption>('recent')
const showAddMobModal = ref(false)

const showRenameProject = ref(false)
const renameProjectBusy = ref(false)
const renameProjectError = ref<string | null>(null)

const renamingMob = ref<MobSummary | null>(null)
const renameMobBusy = ref(false)
const renameMobError = ref<string | null>(null)

const pendingDeleteMob = ref<MobSummary | null>(null)
const deleteMobBusy = ref(false)
const deleteMobError = ref<string | null>(null)

const duplicatingProject = ref(false)
const pendingDeleteProject = ref(false)
const deleteProjectBusy = ref(false)
const deleteProjectError = ref<string | null>(null)

function mobCountLabel(count: number): string {
  return count === 1 ? '1 criatura' : `${count} criaturas`
}

function sortMobs(list: MobSummary[], sort: SortOption): MobSummary[] {
  const copy = [...list]
  if (sort === 'name-asc') {
    copy.sort((a, b) => a.name.localeCompare(b.name))
  } else if (sort === 'name-desc') {
    copy.sort((a, b) => b.name.localeCompare(a.name))
  } else {
    copy.sort((a, b) => new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime())
  }
  return copy
}

const filteredMobs = computed(() => {
  const query = searchQuery.value.trim().toLowerCase()
  let result = mobs.value
  if (query) {
    result = result.filter((mob) => mob.name.toLowerCase().includes(query))
  }
  if (typeFilter.value !== 'all') {
    result = result.filter((mob) => mob.baseType === typeFilter.value)
  }
  if (statusFilter.value !== 'all') {
    result = result.filter((mob) => mob.status === statusFilter.value)
  }
  return sortMobs(result, sortBy.value)
})

/** Distingue "sin resultados por el buscador" (mensaje cita el término) de "sin resultados por los filtros" -- mismo dato (`filteredMobs` vacío), redacción distinta según qué lo causó. */
const noMatchesMessage = computed(() => {
  if (mobs.value.length === 0 || filteredMobs.value.length > 0) {
    return null
  }
  if (searchQuery.value.trim()) {
    return `Ningún mob coincide con "${searchQuery.value.trim()}".`
  }
  return 'Ningún mob coincide con los filtros aplicados.'
})

async function load(): Promise<void> {
  try {
    const [projectResult, mobsResult] = await Promise.all([getProject(projectId), listMobs(projectId)])
    project.value = projectResult
    mobs.value = mobsResult
    loadError.value = null
  } catch (error) {
    loadError.value = error instanceof ApiError ? error.message : 'No se pudo cargar el proyecto.'
  }
}

onMounted(load)

async function confirmAddMob(name: string, baseType: BaseType): Promise<void> {
  try {
    await createMob(projectId, name, baseType)
    showAddMobModal.value = false
    actionError.value = null
    await load() // AC #2: el mob nuevo debe aparecer en el grid
  } catch (error) {
    actionError.value = error instanceof ApiError ? error.message : 'No se pudo agregar el mob.'
  }
}

async function confirmRenameProject(name: string, description: string | null): Promise<void> {
  if (renameProjectBusy.value) {
    return
  }
  renameProjectBusy.value = true
  renameProjectError.value = null
  try {
    await renameProject(projectId, name, description)
    showRenameProject.value = false
    await load()
  } catch (error) {
    renameProjectError.value = error instanceof ApiError ? error.message : 'No se pudo renombrar el proyecto.'
  } finally {
    renameProjectBusy.value = false
  }
}

function openMob(mobId: string): void {
  router.push(`/projects/${projectId}/mobs/${mobId}/edit`)
}

function handleMobAction(actionKey: string, mobId: string): void {
  const mob = mobs.value.find((m) => m.id === mobId)
  if (!mob) {
    return
  }
  if (actionKey === 'rename') {
    renameMobError.value = null
    renamingMob.value = mob
  } else if (actionKey === 'export') {
    router.push(`/projects/${projectId}/mobs/${mobId}/export`)
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
    await load()
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
    await load()
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

/** Ticket 073 -- menú ⋮ del PROYECTO (no de un mob): Duplicar/Eliminar, mismo criterio de responsabilidad que `ProjectsDashboard.vue`. */
async function handleProjectAction(actionKey: string): Promise<void> {
  if (actionKey === 'duplicate') {
    await handleDuplicateProject()
  } else if (actionKey === 'delete') {
    deleteProjectError.value = null
    pendingDeleteProject.value = true
  }
}

async function handleDuplicateProject(): Promise<void> {
  if (duplicatingProject.value) {
    return // bloquea doble submit real -- un segundo clic mientras el primero todavía no respondió no dispara otra duplicación.
  }
  duplicatingProject.value = true
  try {
    const duplicated = await duplicateProject(projectId)
    actionError.value = null
    await router.push(`/projects/${duplicated.id}`)
  } catch (error) {
    actionError.value = error instanceof ApiError ? error.message : 'No se pudo duplicar el proyecto.'
  } finally {
    duplicatingProject.value = false
  }
}

async function confirmDeleteProject(): Promise<void> {
  if (!pendingDeleteProject.value || deleteProjectBusy.value) {
    return
  }
  deleteProjectBusy.value = true
  deleteProjectError.value = null
  try {
    await deleteProject(projectId)
    pendingDeleteProject.value = false
    await router.push('/projects') // se está eliminando el proyecto actualmente abierto -- no queda nada que mostrar acá.
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
  pendingDeleteProject.value = false
}

/** Ticket 071 -- "Inicio" ya NO es sinónimo de "Mis proyectos": navega a "/" (HomeView.vue), no a este dashboard. */
function handleSidebarSelect(key: GSidebarKey): void {
  if (key === 'home') {
    router.push('/')
  } else if (key === 'projects') {
    router.push('/projects')
  }
}
</script>

<template>
  <div class="project-detail-shell">
    <GSidebar active="projects" @select="handleSidebarSelect" />
    <main class="project-detail app-scroll">
      <GPanel v-if="loadError">
        <p class="project-detail__error">{{ loadError }}</p>
      </GPanel>
      <template v-else-if="project">
        <nav class="project-detail__breadcrumb" aria-label="Ruta de navegación">
          <router-link to="/">Galgoth Studio</router-link>
          <IconChevron :size="12" />
          <router-link to="/projects">Mis proyectos</router-link>
          <IconChevron :size="12" />
          <span>{{ project.name }}</span>
        </nav>

        <div class="project-detail__header">
          <div class="project-detail__header-main">
            <div class="project-detail__title-row">
              <h1 class="project-detail__title">{{ project.name }}</h1>
              <IconButton label="Editar proyecto" size="sm" @click="showRenameProject = true"><IconEdit :size="16" /></IconButton>
            </div>
            <p class="project-detail__meta">{{ mobCountLabel(project.mobCount) }} · {{ formatRelativeDate(project.updatedAt) }}</p>
            <p v-if="project.description" class="project-detail__description">{{ project.description }}</p>
          </div>
          <div class="project-detail__header-actions">
            <router-link :to="`/projects/${projectId}/mobs/new-ai`" class="project-detail__ai-mob">
              <IconSparkle :size="16" />
              Crear con IA
            </router-link>
            <GButton variant="secondary" @click="showAddMobModal = true"><template #icon><IconPlus :size="16" /></template>Agregar mob</GButton>
            <span class="project-detail__project-menu">
              <GMenu :items="PROJECT_MENU_ITEMS" label="Más acciones del proyecto" @select="handleProjectAction" />
            </span>
          </div>
        </div>

        <p v-if="actionError" class="project-detail__error">{{ actionError }}</p>

        <div class="project-detail__toolbar">
          <div class="project-detail__search-wrap">
            <IconSearch :size="16" class="project-detail__search-icon" />
            <input v-model="searchQuery" type="search" class="project-detail__search" aria-label="Buscar criaturas" placeholder="Buscar criaturas..." />
          </div>
          <GSelect
            class="project-detail__filter"
            :model-value="typeFilter"
            :options="TYPE_OPTIONS"
            label="Filtrar por tipo"
            @update:model-value="(v) => (typeFilter = v as TypeFilter)"
          />
          <GSelect
            class="project-detail__filter"
            :model-value="statusFilter"
            :options="STATUS_OPTIONS"
            label="Filtrar por estado"
            @update:model-value="(v) => (statusFilter = v as StatusFilter)"
          />
          <GSelect
            class="project-detail__filter"
            :model-value="sortBy"
            :options="SORT_OPTIONS"
            label="Ordenar"
            @update:model-value="(v) => (sortBy = v as SortOption)"
          />
        </div>

        <div class="project-detail__grid">
          <MobCard v-for="mob in filteredMobs" :key="mob.id" :mob="mob" @open="openMob" @action="handleMobAction" />
          <button type="button" class="project-detail__new-mob-cta" @click="showAddMobModal = true">
            <span class="project-detail__new-mob-cta-icon"><IconPlus :size="20" /></span>
            <span class="project-detail__new-mob-cta-title">Nuevo mob</span>
            <span class="project-detail__new-mob-cta-desc">Agrega una nueva criatura a este proyecto</span>
          </button>
        </div>
        <p v-if="noMatchesMessage" class="project-detail__empty">{{ noMatchesMessage }}</p>
      </template>
      <GPanel v-else>
        <p>Cargando…</p>
      </GPanel>
    </main>

    <Transition name="app-dialog">
      <AddMobModal v-if="showAddMobModal" @confirm="confirmAddMob" @cancel="showAddMobModal = false" />
    </Transition>

    <Transition name="app-dialog">
      <ProjectNameModal
        v-if="showRenameProject && project"
        mode="rename"
        :initial-name="project.name"
        :initial-description="project.description"
        :busy="renameProjectBusy"
        :error="renameProjectError"
        @confirm="confirmRenameProject"
        @cancel="showRenameProject = false"
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

    <Transition name="app-dialog">
      <ConfirmDialog
        v-if="pendingDeleteProject && project"
        title="Eliminar proyecto"
        :message="`¿Eliminar el proyecto &quot;${project.name}&quot;? Esta acción no se puede deshacer.`"
        confirm-label="Eliminar"
        danger
        :busy="deleteProjectBusy"
        :error="deleteProjectError"
        @confirm="confirmDeleteProject"
        @cancel="cancelDeleteProject"
      />
    </Transition>
  </div>
</template>

<style scoped>
.project-detail-shell {
  display: flex;
  height: 100vh;
}

.project-detail {
  flex: 1;
  padding: var(--space-6) var(--space-8);
  overflow: auto;
}

.project-detail__breadcrumb {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: var(--text-sm);
  color: var(--muted);
  margin-bottom: var(--space-4);
}

.project-detail__breadcrumb a {
  color: var(--muted);
  text-decoration: none;
  transition: color 160ms cubic-bezier(0.16, 1, 0.3, 1);
}

.project-detail__breadcrumb a:hover {
  color: var(--text);
}

.project-detail__breadcrumb span {
  color: var(--text);
  font-weight: 600;
}

.project-detail__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-4);
  margin-bottom: var(--space-5);
}

.project-detail__title-row {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.project-detail__title {
  margin: 0;
  font-size: var(--text-2xl);
  font-weight: 800;
  letter-spacing: -0.01em;
}

.project-detail__meta {
  margin: 6px 0 0;
  color: var(--muted);
  font-size: var(--text-md);
}

.project-detail__description {
  margin: var(--space-2) 0 0;
  color: var(--muted);
  max-width: 640px;
}

.project-detail__header-actions {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  flex-shrink: 0;
}

.project-detail__project-menu {
  display: inline-flex;
}

.project-detail__ai-mob {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  min-height: var(--hit-target-min);
  padding: 0 var(--space-4);
  background: var(--accent);
  color: var(--accent-ink);
  border: var(--border-width) solid transparent;
  border-radius: var(--radius-md);
  font-weight: 700;
  text-decoration: none;
  white-space: nowrap;
  transition: background-color 220ms cubic-bezier(0.16, 1, 0.3, 1);
}

.project-detail__ai-mob:hover {
  background: var(--accent-hover);
}

.project-detail__toolbar {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  margin-bottom: var(--space-6);
  flex-wrap: wrap;
}

.project-detail__search-wrap {
  position: relative;
  flex: 1;
  min-width: 220px;
  max-width: 380px;
}

.project-detail__search-icon {
  position: absolute;
  left: var(--space-3);
  top: 50%;
  transform: translateY(-50%);
  color: var(--muted);
  pointer-events: none;
}

.project-detail__search {
  width: 100%;
  min-height: var(--hit-target-min);
  padding: 0 var(--space-3) 0 38px;
  background: var(--surface);
  border: var(--border-width) solid var(--border);
  border-radius: var(--radius-md);
  color: var(--text);
  font-size: var(--text-base);
  transition: border-color 160ms cubic-bezier(0.16, 1, 0.3, 1);
}

.project-detail__search:hover {
  border-color: var(--muted);
}

.project-detail__search:focus-visible {
  border-color: var(--accent);
}

.project-detail__filter {
  width: 168px;
  flex-shrink: 0;
}

.project-detail__grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(260px, 340px));
  gap: var(--space-4);
}

.project-detail__new-mob-cta {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  gap: var(--space-3);
  min-height: 260px;
  padding: var(--space-6);
  background: transparent;
  border: 2px dashed var(--border);
  border-radius: var(--radius-lg);
  color: var(--muted);
  cursor: pointer;
  font: inherit;
  transition: color 200ms cubic-bezier(0.16, 1, 0.3, 1), border-color 200ms cubic-bezier(0.16, 1, 0.3, 1);
}

.project-detail__new-mob-cta:hover {
  border-color: var(--accent);
  color: var(--accent);
}

.project-detail__new-mob-cta-icon {
  width: 48px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--radius-md);
  border: var(--border-width) solid var(--border);
  color: inherit;
}

.project-detail__new-mob-cta-title {
  font-weight: 700;
  font-size: var(--text-md);
  color: var(--text);
}

.project-detail__new-mob-cta-desc {
  font-size: var(--text-sm);
  max-width: 220px;
}

.project-detail__empty {
  color: var(--muted);
  margin-top: var(--space-4);
}

.project-detail__error {
  color: var(--danger);
}
</style>
