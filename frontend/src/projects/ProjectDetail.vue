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
 */
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import ConfirmDialog from '../design-system/components/ConfirmDialog.vue'
import GPanel from '../design-system/components/GPanel.vue'
import GButton from '../design-system/components/GButton.vue'
import GSidebar, { type GSidebarKey } from '../design-system/components/GSidebar.vue'
import IconButton from '../design-system/components/IconButton.vue'
import IconEdit from '../design-system/icons/IconEdit.vue'
import IconSearch from '../design-system/icons/IconSearch.vue'
import IconPlus from '../design-system/icons/IconPlus.vue'
import IconSparkle from '../design-system/icons/IconSparkle.vue'
import AddMobModal from './AddMobModal.vue'
import MobCard from './MobCard.vue'
import MobRenameDialog from './MobRenameDialog.vue'
import ProjectNameModal from './ProjectNameModal.vue'
import { ApiError, getProject, renameProject, type ProjectDetail as ProjectDetailDto } from './projectsApi'
import { createMob, deleteMob, listMobs, renameMob, type BaseType, type MobSummary } from './mobsApi'

const route = useRoute()
const router = useRouter()
const projectId = route.params.id as string

const project = ref<ProjectDetailDto | null>(null)
const mobs = ref<MobSummary[]>([])
const loadError = ref<string | null>(null)
const actionError = ref<string | null>(null)
const searchQuery = ref('')
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

const filteredMobs = computed(() => {
  const query = searchQuery.value.trim().toLowerCase()
  if (!query) {
    return mobs.value
  }
  return mobs.value.filter((mob) => mob.name.toLowerCase().includes(query))
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

async function confirmRenameProject(name: string): Promise<void> {
  if (renameProjectBusy.value) {
    return
  }
  renameProjectBusy.value = true
  renameProjectError.value = null
  try {
    await renameProject(projectId, name)
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

function handleSidebarSelect(key: GSidebarKey): void {
  if (key === 'home' || key === 'projects') {
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
        <div class="project-detail__header">
          <div class="project-detail__title-row">
            <h1 class="project-detail__title">{{ project.name }}</h1>
            <IconButton label="Renombrar proyecto" size="sm" @click="showRenameProject = true"><IconEdit :size="16" /></IconButton>
          </div>
          <p class="project-detail__meta">{{ project.mobCount }} criaturas</p>
        </div>
        <div class="project-detail__header-actions">
          <router-link :to="`/projects/${projectId}/mobs/new-ai`" class="project-detail__ai-mob">
            <IconSparkle :size="16" />
            Crear con IA
          </router-link>
          <GButton variant="secondary" @click="showAddMobModal = true"><template #icon><IconPlus :size="16" /></template>Agregar mob</GButton>
        </div>

        <p v-if="actionError" class="project-detail__error">{{ actionError }}</p>

        <div class="project-detail__search-wrap">
          <IconSearch :size="16" class="project-detail__search-icon" />
          <input v-model="searchQuery" type="search" class="project-detail__search" aria-label="Buscar mobs por nombre" placeholder="Buscar mobs..." />
        </div>

        <div class="project-detail__grid">
          <MobCard v-for="mob in filteredMobs" :key="mob.id" :mob="mob" @open="openMob" @action="handleMobAction" />
          <button type="button" class="project-detail__new-mob-cta" @click="showAddMobModal = true">
            <IconPlus :size="22" />
            <span>Nuevo mob</span>
          </button>
        </div>
        <p v-if="mobs.length > 0 && filteredMobs.length === 0" class="project-detail__empty">
          Ningún mob coincide con "{{ searchQuery }}".
        </p>
      </template>
      <GPanel v-else>
        <p>Cargando…</p>
      </GPanel>
    </main>

    <AddMobModal v-if="showAddMobModal" @confirm="confirmAddMob" @cancel="showAddMobModal = false" />

    <ProjectNameModal
      v-if="showRenameProject && project"
      mode="rename"
      :initial-name="project.name"
      :busy="renameProjectBusy"
      :error="renameProjectError"
      @confirm="confirmRenameProject"
      @cancel="showRenameProject = false"
    />

    <MobRenameDialog
      v-if="renamingMob"
      :initial-name="renamingMob.name"
      :busy="renameMobBusy"
      :error="renameMobError"
      @confirm="confirmRenameMob"
      @cancel="cancelRenameMob"
    />

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
  </div>
</template>

<style scoped>
.project-detail-shell {
  display: flex;
  height: 100vh;
}

.project-detail {
  flex: 1;
  padding: var(--space-6);
  overflow: auto;
}

.project-detail__header {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
  margin-bottom: var(--space-4);
}

.project-detail__title-row {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.project-detail__title {
  margin: 0;
}

.project-detail__meta {
  margin: 0;
  color: var(--muted);
}

.project-detail__header-actions {
  display: flex;
  gap: var(--space-2);
  margin-bottom: var(--space-4);
}

.project-detail__ai-mob {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  min-height: var(--hit-target-min);
  padding: 0 var(--space-4);
  background: var(--accent-soft);
  color: var(--accent);
  border: var(--border-width) solid var(--accent);
  border-radius: var(--radius-md);
  font-weight: 600;
  text-decoration: none;
  white-space: nowrap;
}

.project-detail__ai-mob:hover {
  background: var(--accent);
  color: var(--accent-ink);
}

.project-detail__search-wrap {
  position: relative;
  width: 100%;
  max-width: 320px;
  margin-bottom: var(--space-4);
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
}

.project-detail__search:hover {
  border-color: var(--muted);
}

.project-detail__search:focus-visible {
  border-color: var(--accent);
}

.project-detail__grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: var(--space-4);
}

.project-detail__new-mob-cta {
  aspect-ratio: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--space-2);
  background: transparent;
  border: 2px dashed var(--border);
  border-radius: var(--radius-lg);
  color: var(--muted);
  cursor: pointer;
  font-size: var(--text-sm);
  font-weight: 600;
}

.project-detail__new-mob-cta:hover {
  border-color: var(--accent);
  color: var(--accent);
}

.project-detail__empty {
  color: var(--muted);
  margin-top: var(--space-4);
}

.project-detail__error {
  color: var(--danger);
}
</style>
