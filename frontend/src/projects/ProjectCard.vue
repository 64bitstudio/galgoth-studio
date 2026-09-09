<script setup lang="ts">
/**
 * Tarjeta de proyecto del dashboard "Mis proyectos" (HU-02, mockup 01).
 * Hasta 3 miniaturas + indicador "+N" cuando el proyecto tiene más de 3
 * mobs (AC #3); miniatura ausente/fallida -> placeholder genérico (AC
 * #5, nunca bloquea el listado). "Export" del menú de acciones queda
 * deshabilitado con su razón visible -- todavía no existe ningún
 * endpoint de exportación expuesto (decisión del Product Owner, ticket
 * 021).
 *
 * Estructura (hallazgo real de Sonar, S6819: usar `<button>` real en vez
 * de `role="button"` sobre un `<div>`): el `<button>` que abre el
 * detalle NO puede envolver también el botón del menú de `GMenu`
 * (contenido interactivo dentro de un `<button>` es HTML inválido) --
 * el menú vive como hermano, superpuesto visualmente en la esquina.
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
  <div class="project-card">
    <button type="button" class="project-card__open" @click="emit('open', project.id)">
      <span class="project-card__thumbnails">
        <span v-for="thumb in project.mobThumbnails" :key="thumb.mobId" class="project-card__thumbnail">
          <img v-if="thumb.thumbnailKey" :src="thumbnailUrl(thumb.thumbnailKey)!" alt="" />
          <span v-else class="project-card__placeholder" aria-hidden="true" />
        </span>
        <span v-if="project.mobThumbnails.length === 0" class="project-card__thumbnail">
          <span class="project-card__placeholder" aria-hidden="true" />
        </span>
        <span v-if="project.mobCount > 3" class="project-card__more">+{{ project.mobCount - 3 }}</span>
      </span>
      <span class="project-card__name">{{ project.name }}</span>
      <span class="project-card__meta">{{ mobCountLabel(project.mobCount) }} · {{ formatRelativeDate(project.updatedAt) }}</span>
    </button>
    <span class="project-card__menu">
      <GMenu :items="MENU_ITEMS" :label="`Acciones de ${project.name}`" @select="(key) => handleAction(project.id, key)" />
    </span>
  </div>
</template>

<style scoped>
.project-card {
  position: relative;
  background: var(--panel);
  border: var(--border-width) solid var(--border);
  border-radius: var(--radius-lg);
}

.project-card:hover {
  border-color: var(--accent);
}

.project-card__open {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: var(--space-2);
  width: 100%;
  padding: var(--space-3);
  background: transparent;
  border: none;
  border-radius: inherit;
  color: var(--text);
  text-align: left;
  cursor: pointer;
  font: inherit;
}

.project-card__thumbnails {
  position: relative;
  display: flex;
  gap: var(--space-1);
}

.project-card__thumbnail {
  flex: 1;
  aspect-ratio: 1;
  display: block;
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

.project-card__name {
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  padding-right: var(--space-8); /* deja espacio al menú superpuesto */
}

.project-card__meta {
  font-size: var(--text-xs);
  color: var(--muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.project-card__menu {
  position: absolute;
  right: var(--space-2);
  bottom: var(--space-2);
}
</style>
