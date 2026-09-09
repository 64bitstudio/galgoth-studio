<script setup lang="ts">
/**
 * Detalle de proyecto (ticket 021) -- MÍNIMO a propósito. HU-01 AC #1
 * exige que "Nuevo proyecto" redirija a un detalle real (no un 404); el
 * grid completo de mobs, búsqueda y estado por mob es HU-04 (ticket
 * 022), que reemplaza este componente.
 */
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import GPanel from '../design-system/components/GPanel.vue'
import GSidebar, { type GSidebarKey } from '../design-system/components/GSidebar.vue'
import { ApiError, getProject, type ProjectDetail } from './projectsApi'

const route = useRoute()
const router = useRouter()
const project = ref<ProjectDetail | null>(null)
const loadError = ref<string | null>(null)

onMounted(async () => {
  try {
    project.value = await getProject(route.params.id as string)
  } catch (error) {
    loadError.value = error instanceof ApiError ? error.message : 'No se pudo cargar el proyecto.'
  }
})

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
      <GPanel v-else-if="project">
        <h1>{{ project.name }}</h1>
        <p class="project-detail__meta">{{ project.mobCount }} mob(s)</p>
        <p class="project-detail__placeholder-note">
          Grid de mobs, búsqueda y estado por mob llegan en el ticket
          <code>022-crud-mobs-detalle-proyecto-agregar-mob</code>.
        </p>
      </GPanel>
      <GPanel v-else>
        <p>Cargando…</p>
      </GPanel>
    </main>
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

.project-detail__meta {
  color: var(--muted);
}

.project-detail__placeholder-note {
  color: var(--muted);
  font-size: var(--text-sm);
}

.project-detail__error {
  color: var(--danger);
}
</style>
