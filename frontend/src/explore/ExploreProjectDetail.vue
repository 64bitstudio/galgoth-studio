<script setup lang="ts">
/**
 * Ficha pública de un proyecto (ticket 088, HU-6) -- modo estrictamente
 * lectura: sin renombrar, sin menú de acciones, sin "Agregar mob"/"Crear
 * con IA", sin toggle de visibilidad (todas acciones de dueño, fuera de
 * alcance acá). Reutiliza `getProject`/`listMobs` tal cual -- son los
 * mismos endpoints que "Mis proyectos" ya usa (`GET /api/projects/{id}`
 * y `GET /api/projects/{id}/mobs`), públicamente legibles para un
 * proyecto PUBLIC desde el ticket 085 -- sin cliente HTTP nuevo.
 *
 * Alcanzable sin sesión (esta ruta no lleva `meta.requiresAuth`, ver
 * `router.ts`). A diferencia de `ProjectDetail.vue` (ticket 087), acá
 * una carga fallida SIEMPRE es "no encontrado" -- no hay ambigüedad que
 * resolver con un login, porque esta pantalla nunca asume que el
 * visitante es el dueño.
 */
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { avatarUrl } from '../api/apiConfig'
import GSidebar, { type GSidebarKey } from '../design-system/components/GSidebar.vue'
import IconChevron from '../design-system/icons/IconChevron.vue'
import { ApiError, getProject, type ProjectDetail } from '../projects/projectsApi'
import { listMobs, type MobSummary } from '../projects/mobsApi'
import ExploreMobCard from './ExploreMobCard.vue'

/** Ticket 092 -- fallback sin avatar subido (ticket 091), mismo criterio que `ExploreProjectCard.vue`. */
function initial(name: string): string {
  return name.trim().charAt(0).toUpperCase()
}

const route = useRoute()
const router = useRouter()
const projectId = route.params.id as string

const project = ref<ProjectDetail | null>(null)
const mobs = ref<MobSummary[]>([])
const loadError = ref<string | null>(null)

function mobCountLabel(count: number): string {
  return count === 1 ? '1 criatura' : `${count} criaturas`
}

async function load(): Promise<void> {
  try {
    const [projectResult, mobsResult] = await Promise.all([getProject(projectId), listMobs(projectId)])
    project.value = projectResult
    mobs.value = mobsResult
    loadError.value = null
  } catch (error) {
    // Ticket 085: el backend nunca distingue "no existe" de "existe pero es privado" -- mismo 404 para ambos, así que este mensaje es siempre "no encontrado", nunca el texto crudo del backend.
    loadError.value = error instanceof ApiError && error.status === 404 ? 'Este proyecto no existe o ya no está disponible.' : 'No se pudo cargar el proyecto público.'
  }
}

onMounted(load)

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
  <div class="explore-detail-shell">
    <GSidebar active="explore" @select="handleSidebarSelect" />
    <main class="explore-detail app-scroll">
      <p v-if="loadError" class="explore-detail__error">{{ loadError }}</p>
      <template v-else-if="project">
        <nav class="explore-detail__breadcrumb" aria-label="Ruta de navegación">
          <router-link to="/">Galgoth Studio</router-link>
          <IconChevron :size="12" />
          <router-link to="/explore">Explorar</router-link>
          <IconChevron :size="12" />
          <span>{{ project.name }}</span>
        </nav>

        <div class="explore-detail__header">
          <h1 class="explore-detail__title">{{ project.name }}</h1>
          <p class="explore-detail__meta">{{ mobCountLabel(project.mobCount) }}</p>
          <p v-if="project.ownerDisplayName" class="explore-detail__author">
            <span class="explore-detail__avatar">
              <img v-if="avatarUrl(project.avatarUrl)" :src="avatarUrl(project.avatarUrl)!" alt="" />
              <span v-else class="explore-detail__avatar-initial" aria-hidden="true">{{ initial(project.ownerDisplayName) }}</span>
            </span>
            por {{ project.ownerDisplayName }}
          </p>
          <p v-if="project.description" class="explore-detail__description">{{ project.description }}</p>
        </div>

        <p v-if="mobs.length === 0" class="explore-detail__empty">Este proyecto todavía no tiene mobs.</p>
        <div v-else class="explore-detail__grid">
          <ExploreMobCard v-for="mob in mobs" :key="mob.id" :mob="mob" />
        </div>
      </template>
    </main>
  </div>
</template>

<style scoped>
.explore-detail-shell {
  display: flex;
  height: 100vh;
}

.explore-detail {
  flex: 1;
  padding: var(--space-6) var(--space-8);
  overflow: auto;
}

.explore-detail__breadcrumb {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: var(--text-sm);
  color: var(--muted);
  margin-bottom: var(--space-4);
}

.explore-detail__breadcrumb a {
  color: var(--muted);
  text-decoration: none;
  transition: color 160ms cubic-bezier(0.16, 1, 0.3, 1);
}

.explore-detail__breadcrumb a:hover {
  color: var(--text);
}

.explore-detail__breadcrumb span {
  color: var(--text);
  font-weight: 600;
}

.explore-detail__header {
  margin-bottom: var(--space-5);
}

.explore-detail__title {
  margin: 0;
  font-size: var(--text-2xl);
  font-weight: 800;
  letter-spacing: -0.01em;
}

.explore-detail__meta {
  margin: 6px 0 0;
  color: var(--muted);
  font-size: var(--text-sm);
}

.explore-detail__author {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  margin: var(--space-2) 0 0;
  color: var(--text);
  font-weight: 600;
  font-size: var(--text-sm);
}

.explore-detail__avatar {
  flex-shrink: 0;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  overflow: hidden;
  background: var(--accent-soft);
}

.explore-detail__avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.explore-detail__avatar-initial {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  color: var(--accent);
  font-size: var(--text-xs);
  font-weight: 700;
}

.explore-detail__description {
  margin: var(--space-2) 0 0;
  color: var(--text);
  max-width: 60em;
}

.explore-detail__grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 300px));
  gap: var(--space-4);
}

.explore-detail__empty,
.explore-detail__error {
  color: var(--muted);
}
</style>
