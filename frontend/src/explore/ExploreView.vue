<script setup lang="ts">
/**
 * Explorar (ticket 088, HU-5) -- galería pública de proyectos PUBLIC de
 * cualquier dueño, alcanzable SIN sesión (a diferencia de "Mis
 * proyectos", esta ruta no lleva `meta.requiresAuth` -- ver `router.ts`,
 * ticket 087). Sin búsqueda/filtros/paginación en esta primera pasada
 * (mismo criterio ya documentado en el backend, ticket 086).
 */
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import GSidebar, { type GSidebarKey } from '../design-system/components/GSidebar.vue'
import IconChevron from '../design-system/icons/IconChevron.vue'
import { ApiError, listExploreProjects, type ProjectSummary } from '../projects/projectsApi'
import ExploreProjectCard from './ExploreProjectCard.vue'

const router = useRouter()

const projects = ref<ProjectSummary[]>([])
const loadError = ref<string | null>(null)
const loaded = ref(false)

async function load(): Promise<void> {
  try {
    projects.value = await listExploreProjects()
    loadError.value = null
  } catch (error) {
    loadError.value = error instanceof ApiError ? error.message : 'No se pudieron cargar los proyectos públicos.'
  } finally {
    loaded.value = true
  }
}

onMounted(load)

function openProject(projectId: string): void {
  router.push(`/explore/${projectId}`)
}

function handleSidebarSelect(key: GSidebarKey): void {
  if (key === 'home') {
    router.push('/')
  } else if (key === 'projects') {
    router.push('/projects')
  } else if (key === 'explore') {
    router.push('/explore')
  } else if (key === 'user') {
    router.push('/usuario')
  }
}
</script>

<template>
  <div class="explore-view-shell">
    <GSidebar active="explore" @select="handleSidebarSelect" />
    <main class="explore-view app-scroll">
      <nav class="explore-view__breadcrumb" aria-label="Ruta de navegación">
        <router-link to="/">Galgoth Studio</router-link>
        <IconChevron :size="12" />
        <span>Explorar</span>
      </nav>

      <h1 class="explore-view__title">Explorar</h1>
      <p class="explore-view__subtitle">Descubre mobs creados por otros miembros de la comunidad.</p>

      <p v-if="loadError" class="explore-view__error">{{ loadError }}</p>
      <p v-else-if="loaded && projects.length === 0" class="explore-view__empty">Todavía no hay proyectos públicos.</p>

      <div v-if="!loadError" class="explore-view__grid">
        <ExploreProjectCard v-for="project in projects" :key="project.id" :project="project" @open="openProject" />
      </div>
    </main>
  </div>
</template>

<style scoped>
.explore-view-shell {
  display: flex;
  height: 100vh;
}

.explore-view {
  flex: 1;
  padding: var(--space-6) var(--space-8);
  overflow: auto;
}

.explore-view__breadcrumb {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: var(--text-sm);
  color: var(--muted);
  margin-bottom: var(--space-4);
}

.explore-view__breadcrumb a {
  color: var(--muted);
  text-decoration: none;
  transition: color 160ms cubic-bezier(0.16, 1, 0.3, 1);
}

.explore-view__breadcrumb a:hover {
  color: var(--text);
}

.explore-view__breadcrumb span {
  color: var(--text);
  font-weight: 600;
}

.explore-view__title {
  margin: 0;
  font-size: var(--text-2xl);
  font-weight: 800;
  letter-spacing: -0.01em;
}

.explore-view__subtitle {
  margin: var(--space-1) 0 var(--space-6);
  color: var(--muted);
  font-size: var(--text-md);
}

.explore-view__grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(300px, 380px));
  gap: var(--space-4);
}

.explore-view__empty,
.explore-view__error {
  color: var(--muted);
  margin-bottom: var(--space-4);
}

.explore-view__error {
  color: var(--danger);
}
</style>
