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
 */
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import GPanel from '../design-system/components/GPanel.vue'
import GSidebar, { type GSidebarKey } from '../design-system/components/GSidebar.vue'
import AddMobModal from './AddMobModal.vue'
import MobCard from './MobCard.vue'
import { ApiError, getProject, type ProjectDetail as ProjectDetailDto } from './projectsApi'
import { createMob, listMobs, type BaseType, type MobSummary } from './mobsApi'

const route = useRoute()
const router = useRouter()
const projectId = route.params.id as string

const project = ref<ProjectDetailDto | null>(null)
const mobs = ref<MobSummary[]>([])
const loadError = ref<string | null>(null)
const actionError = ref<string | null>(null)
const searchQuery = ref('')
const showAddMobModal = ref(false)

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

function handleSidebarSelect(key: GSidebarKey): void {
  if (key === 'home' || key === 'projects') {
    router.push('/projects')
  }
}
</script>

<template>
  <div class="project-detail-shell">
    <GSidebar active="projects" @select="handleSidebarSelect" />
    <main class="project-detail">
      <GPanel v-if="loadError">
        <p class="project-detail__error">{{ loadError }}</p>
      </GPanel>
      <template v-else-if="project">
        <div class="project-detail__header">
          <div>
            <h1 class="project-detail__title">{{ project.name }}</h1>
            <p class="project-detail__meta">{{ project.mobCount }} criaturas</p>
          </div>
          <div class="project-detail__header-actions">
            <router-link :to="`/projects/${projectId}/mobs/new-ai`" class="project-detail__ai-mob">Crear con IA</router-link>
            <button type="button" class="project-detail__add-mob" @click="showAddMobModal = true">+ Agregar mob</button>
          </div>
        </div>

        <p v-if="actionError" class="project-detail__error">{{ actionError }}</p>

        <input v-model="searchQuery" type="search" class="project-detail__search" aria-label="Buscar mobs por nombre" placeholder="Buscar mobs..." />

        <div class="project-detail__grid">
          <MobCard v-for="mob in filteredMobs" :key="mob.id" :mob="mob" />
          <button type="button" class="project-detail__new-mob-cta" @click="showAddMobModal = true">+ Nuevo mob</button>
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
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-4);
  margin-bottom: var(--space-4);
}

.project-detail__title {
  margin: 0;
}

.project-detail__meta {
  margin: var(--space-1) 0 0;
  color: var(--muted);
}

.project-detail__header-actions {
  display: flex;
  gap: var(--space-2);
}

.project-detail__add-mob {
  min-height: var(--hit-target-min);
  padding: 0 var(--space-4);
  background: var(--accent);
  color: var(--accent-ink);
  border: none;
  border-radius: var(--radius-md);
  font-weight: 600;
  cursor: pointer;
  white-space: nowrap;
}

.project-detail__ai-mob {
  display: inline-flex;
  align-items: center;
  min-height: var(--hit-target-min);
  padding: 0 var(--space-4);
  background: var(--surface-2);
  color: var(--text);
  border: var(--border-width) solid var(--border);
  border-radius: var(--radius-md);
  font-weight: 600;
  text-decoration: none;
  white-space: nowrap;
}

.project-detail__ai-mob:hover {
  border-color: var(--accent);
  color: var(--accent);
}

.project-detail__search {
  width: 100%;
  max-width: 320px;
  min-height: var(--hit-target-min);
  padding: 0 var(--space-3);
  margin-bottom: var(--space-4);
  background: var(--surface);
  border: var(--border-width) solid var(--border);
  border-radius: var(--radius-md);
  color: var(--text);
  font-size: var(--text-base);
}

.project-detail__grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: var(--space-4);
}

.project-detail__new-mob-cta {
  aspect-ratio: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  background: transparent;
  border: 2px dashed var(--border);
  border-radius: var(--radius-lg);
  color: var(--muted);
  cursor: pointer;
  font-size: var(--text-base);
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
