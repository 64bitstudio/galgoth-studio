<script setup lang="ts">
/**
 * Tarjeta de proyecto del dashboard "Mis proyectos" (HU-02, mockup 01).
 * Hasta 3 miniaturas + indicador "+N" cuando el proyecto tiene más de 3
 * mobs (AC #3); miniatura ausente/fallida -> placeholder genérico (AC
 * #5, nunca bloquea el listado). "Export" del menú de acciones queda
 * deshabilitado con su razón visible -- todavía no existe ningún
 * endpoint de exportación expuesto (decisión del Product Owner, ticket
 * 021).
 */
import GMenu, { type GMenuItem } from '../design-system/components/GMenu.vue'
import type { ProjectSummary } from './projectsApi'

defineProps<{ project: ProjectSummary }>()

const emit = defineEmits<{ open: [string]; action: [string, string] }>()

const MENU_ITEMS: GMenuItem[] = [
  { key: 'rename', label: 'Rename' },
  { key: 'duplicate', label: 'Duplicate' },
  { key: 'export', label: 'Export', disabled: true, disabledReason: 'Disponible cuando el proyecto tenga mobs exportables' },
  { key: 'delete', label: 'Delete', danger: true },
]

function handleAction(projectId: string, actionKey: string): void {
  emit('action', actionKey, projectId)
}
</script>

<template>
  <div class="project-card" role="button" tabindex="0" @click="emit('open', project.id)" @keyup.enter="emit('open', project.id)">
    <div class="project-card__thumbnails">
      <div v-for="thumb in project.mobThumbnails" :key="thumb.mobId" class="project-card__thumbnail">
        <img v-if="thumb.thumbnailKey" :src="thumb.thumbnailKey" alt="" />
        <span v-else class="project-card__placeholder" aria-hidden="true" />
      </div>
      <div v-if="project.mobThumbnails.length === 0" class="project-card__thumbnail">
        <span class="project-card__placeholder" aria-hidden="true" />
      </div>
      <span v-if="project.mobCount > 3" class="project-card__more">+{{ project.mobCount - 3 }}</span>
    </div>
    <div class="project-card__footer">
      <span class="project-card__name">{{ project.name }}</span>
      <span class="project-card__menu" @click.stop @keyup.stop>
        <GMenu :items="MENU_ITEMS" :label="`Acciones de ${project.name}`" @select="(key) => handleAction(project.id, key)" />
      </span>
    </div>
  </div>
</template>

<style scoped>
.project-card {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  padding: var(--space-3);
  background: var(--panel);
  border: var(--border-width) solid var(--border);
  border-radius: var(--radius-lg);
  cursor: pointer;
}

.project-card:hover {
  border-color: var(--accent);
}

.project-card__thumbnails {
  position: relative;
  display: flex;
  gap: var(--space-1);
}

.project-card__thumbnail {
  flex: 1;
  aspect-ratio: 1;
  border-radius: var(--radius-md);
  overflow: hidden;
  background: var(--surface);
}

.project-card__thumbnail img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.project-card__placeholder {
  display: block;
  width: 100%;
  height: 100%;
  background: var(--surface-2);
}

.project-card__more {
  position: absolute;
  bottom: var(--space-1);
  right: var(--space-1);
  background: rgba(0, 0, 0, 0.7);
  color: var(--text);
  font-size: var(--text-xs);
  padding: 0 var(--space-2);
  border-radius: var(--radius-sm);
}

.project-card__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
}

.project-card__name {
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
