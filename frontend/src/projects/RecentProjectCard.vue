<script setup lang="ts">
/**
 * Card horizontal de proyecto para "Proyectos recientes" (Inicio, ticket
 * 071, mockup rediseno.png). Mismo contenido/acciones que
 * `ProjectCard.vue` (miniaturas ≤3 + "+N" + nombre + conteo + menú ⋮)
 * pero en layout horizontal -- `ProjectCard.vue` sigue siendo la card
 * vertical del grid de "Mis proyectos", sin tocar.
 */
import { thumbnailUrl } from '../api/apiConfig'
import { formatRelativeDate } from '../domain/relativeDate'
import GMenu, { type GMenuItem } from '../design-system/components/GMenu.vue'
import type { ProjectSummary } from './projectsApi'

function mobCountLabel(count: number): string {
  return count === 1 ? '1 mob' : `${count} mobs`
}

defineProps<{ project: ProjectSummary }>()

const emit = defineEmits<{ open: [string]; action: [string, string] }>()

const MENU_ITEMS: GMenuItem[] = [
  { key: 'rename', label: 'Renombrar' },
  { key: 'duplicate', label: 'Duplicar' },
  { key: 'export', label: 'Exportar', disabled: true, disabledReason: 'Disponible cuando el proyecto tenga mobs exportables' },
  { key: 'delete', label: 'Eliminar', danger: true },
]

function handleAction(projectId: string, actionKey: string): void {
  emit('action', actionKey, projectId)
}
</script>

<template>
  <div class="recent-project-card">
    <button type="button" class="recent-project-card__open" @click="emit('open', project.id)">
      <span class="recent-project-card__thumbnails">
        <span v-for="thumb in project.mobThumbnails" :key="thumb.mobId" class="recent-project-card__thumbnail">
          <img v-if="thumb.thumbnailKey" :src="thumbnailUrl(thumb.thumbnailKey)!" alt="" />
          <span v-else class="recent-project-card__placeholder" aria-hidden="true" />
        </span>
        <span v-if="project.mobThumbnails.length === 0" class="recent-project-card__thumbnail">
          <span class="recent-project-card__placeholder" aria-hidden="true" />
        </span>
        <span v-if="project.mobCount > 3" class="recent-project-card__more">+{{ project.mobCount - 3 }}</span>
      </span>
      <span class="recent-project-card__body">
        <span class="recent-project-card__name">{{ project.name }}</span>
        <span class="recent-project-card__meta">{{ mobCountLabel(project.mobCount) }} · {{ formatRelativeDate(project.updatedAt) }}</span>
      </span>
    </button>
    <span class="recent-project-card__menu">
      <GMenu :items="MENU_ITEMS" :label="`Acciones de ${project.name}`" @select="(key) => handleAction(project.id, key)" />
    </span>
  </div>
</template>

<style scoped>
.recent-project-card {
  position: relative;
  background: var(--panel);
  border: var(--border-width) solid var(--border);
  border-radius: var(--radius-lg);
  transition:
    border-color 220ms cubic-bezier(0.16, 1, 0.3, 1),
    transform 220ms cubic-bezier(0.16, 1, 0.3, 1),
    box-shadow 220ms cubic-bezier(0.16, 1, 0.3, 1);
}

.recent-project-card:hover {
  border-color: var(--accent);
  transform: translateY(-2px);
  box-shadow: var(--shadow-md);
}

.recent-project-card__open {
  display: flex;
  align-items: center;
  gap: var(--space-4);
  width: 100%;
  padding: var(--space-3) var(--space-4);
  padding-right: var(--space-8); /* deja espacio al menú superpuesto */
  background: transparent;
  border: none;
  border-radius: inherit;
  color: var(--text);
  text-align: left;
  cursor: pointer;
  font: inherit;
}

.recent-project-card__thumbnails {
  position: relative;
  display: flex;
  gap: var(--space-1);
  flex-shrink: 0;
}

.recent-project-card__thumbnail {
  width: 52px;
  height: 52px;
  flex-shrink: 0;
  display: block;
  border-radius: var(--radius-sm);
  overflow: hidden;
  background: var(--surface);
}

.recent-project-card__thumbnail img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.recent-project-card__placeholder {
  display: block;
  width: 100%;
  height: 100%;
  background: var(--surface-2);
}

.recent-project-card__more {
  position: absolute;
  bottom: var(--space-1);
  right: var(--space-1);
  background: rgba(0, 0, 0, 0.7);
  color: var(--text);
  font-size: var(--text-xs);
  padding: 0 var(--space-2);
  border-radius: var(--radius-sm);
}

.recent-project-card__body {
  min-width: 0;
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.recent-project-card__name {
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.recent-project-card__meta {
  font-size: var(--text-xs);
  color: var(--muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.recent-project-card__menu {
  position: absolute;
  right: var(--space-2);
  top: 50%;
  transform: translateY(-50%);
}
</style>
