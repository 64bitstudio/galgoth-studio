<script setup lang="ts">
/**
 * Tarjeta de proyecto del dashboard "Mis proyectos" (HU-02, ticket 021;
 * rediseño de fidelidad visual estricta, ticket 072, VoBo del PO sobre
 * el preview interactivo). Cambios del rediseño frente a la versión
 * anterior: badge de estado del proyecto (`status`, derivado en el
 * backend -- ver `ProjectSummary`), miniaturas en tiles de tamaño fijo
 * (no estiradas a ocupar el ancho) con "+N" como su propio tile en vez
 * de una insignia superpuesta, y el menú ⋮ alineado con el pie de la
 * card en vez de superpuesto en la esquina.
 *
 * Estructura (hallazgo real de Sonar, S6819: usar `<button>` real en vez
 * de `role="button"` sobre un `<div>`): el `<button>` que abre el
 * detalle NO puede envolver también el botón del menú de `GMenu`
 * (contenido interactivo dentro de un `<button>` es HTML inválido) --
 * el menú vive como hermano, superpuesto visualmente en la esquina
 * inferior derecha (mismo criterio que la versión anterior).
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
  <div class="project-card">
    <button type="button" class="project-card__open" @click="emit('open', project.id)">
      <span class="project-card__top">
        <span class="status-pill" :class="`status-pill--${project.status}`">
          <span class="status-pill__dot" aria-hidden="true" />
          {{ project.status === 'active' ? 'Activo' : 'Draft' }}
        </span>
      </span>
      <span class="project-card__thumbnails">
        <span v-for="thumb in project.mobThumbnails" :key="thumb.mobId" class="project-card__thumbnail">
          <img v-if="thumb.thumbnailKey" :src="thumbnailUrl(thumb.thumbnailKey)!" alt="" />
          <span v-else class="project-card__placeholder" aria-hidden="true" />
        </span>
        <span v-if="project.mobThumbnails.length === 0" class="project-card__thumbnail">
          <span class="project-card__placeholder" aria-hidden="true" />
        </span>
        <span v-if="project.mobCount > 3" class="project-card__thumbnail project-card__more">+{{ project.mobCount - 3 }}</span>
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
  transition:
    border-color 220ms cubic-bezier(0.16, 1, 0.3, 1),
    transform 220ms cubic-bezier(0.16, 1, 0.3, 1),
    box-shadow 220ms cubic-bezier(0.16, 1, 0.3, 1);
}

.project-card:hover {
  border-color: var(--accent);
  transform: translateY(-2px);
  box-shadow: var(--shadow-md);
}

.project-card__open {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: var(--space-3);
  width: 100%;
  padding: var(--space-4);
  background: transparent;
  border: none;
  border-radius: inherit;
  color: var(--text);
  text-align: left;
  cursor: pointer;
  font: inherit;
}

.project-card__top {
  display: flex;
  justify-content: flex-end;
}

.status-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 3px var(--space-2) 3px 8px;
  border-radius: 999px;
  font-size: var(--text-xs);
  font-weight: 600;
  line-height: 1.6;
  white-space: nowrap;
}

.status-pill__dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  flex-shrink: 0;
}

.status-pill--active {
  background: var(--accent-soft);
  color: var(--accent);
}

.status-pill--active .status-pill__dot {
  background: var(--accent);
  box-shadow: 0 0 0 3px var(--accent-soft);
}

.status-pill--draft {
  background: var(--surface-2);
  color: var(--muted);
}

.status-pill--draft .status-pill__dot {
  background: var(--muted);
}

.project-card__thumbnails {
  display: flex;
  gap: var(--space-2);
}

.project-card__thumbnail {
  width: 64px;
  height: 64px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
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
  background: var(--surface-2);
  color: var(--muted);
  font-weight: 700;
  font-size: var(--text-md);
}

.project-card__name {
  font-weight: 700;
  font-size: var(--text-md);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.project-card__meta {
  font-size: var(--text-xs);
  color: var(--muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  padding-right: var(--space-8); /* deja espacio al menú superpuesto */
}

.project-card__menu {
  position: absolute;
  right: var(--space-3);
  bottom: var(--space-4);
}
</style>
