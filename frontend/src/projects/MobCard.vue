<script setup lang="ts">
/**
 * Tarjeta de mob del grid de detalle de proyecto (HU-04, mockup 12).
 * Miniatura (placeholder genérico si el mob todavía no tiene thumbnail
 * -- ver pipeline del ticket 023, mismo criterio que `ProjectCard.vue`)
 * + nombre + `GStatusPill` de estado.
 *
 * Ticket 039: agrega el menú ⋮ de acciones (Renombrar/Eliminar/Exportar)
 * -- mismo patrón exacto que `ProjectCard.vue` (ticket 021): el
 * `<button>` que abre el editor NO puede envolver también el botón del
 * menú de `GMenu` (contenido interactivo dentro de un `<button>` es
 * HTML inválido), así que el menú vive como hermano, superpuesto
 * visualmente en la esquina. `open`/`action` se emiten hacia
 * `ProjectDetail.vue`, que es quien navega/abre los diálogos reales
 * (mismo criterio de responsabilidad que `ProjectCard.vue`).
 */
import { thumbnailUrl } from '../api/apiConfig'
import GMenu, { type GMenuItem } from '../design-system/components/GMenu.vue'
import GStatusPill from '../design-system/components/GStatusPill.vue'
import type { MobStatus, MobSummary } from './mobsApi'

const props = defineProps<{ mob: MobSummary }>()

const emit = defineEmits<{ open: [string]; action: [string, string] }>()

const MENU_ITEMS: GMenuItem[] = [
  { key: 'rename', label: 'Renombrar' },
  { key: 'export', label: 'Exportar' },
  { key: 'delete', label: 'Eliminar', danger: true },
]

function handleAction(actionKey: string): void {
  emit('action', actionKey, props.mob.id)
}

/** `mobs.status` en la BD usa guion bajo (`in_progress`); GStatusPill usa guion medio -- mismo valor, distinta convención de cada capa. */
function toPillStatus(status: MobStatus): 'draft' | 'in-progress' | 'ready' {
  return status === 'in_progress' ? 'in-progress' : status
}
</script>

<template>
  <div class="mob-card">
    <button type="button" class="mob-card__open" @click="emit('open', mob.id)">
      <span class="mob-card__thumbnail">
        <img v-if="mob.thumbnailKey" :src="thumbnailUrl(mob.thumbnailKey)!" alt="" />
        <span v-else class="mob-card__placeholder" aria-hidden="true" />
      </span>
      <span class="mob-card__footer">
        <span class="mob-card__name">{{ mob.name }}</span>
        <GStatusPill :status="toPillStatus(mob.status)" />
      </span>
    </button>
    <span class="mob-card__menu">
      <GMenu :items="MENU_ITEMS" :label="`Acciones de ${mob.name}`" @select="handleAction" />
    </span>
  </div>
</template>

<style scoped>
.mob-card {
  position: relative;
}

.mob-card__open {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: var(--space-2);
  width: 100%;
  padding: var(--space-3);
  background: var(--panel);
  border: var(--border-width) solid var(--border);
  border-radius: var(--radius-lg);
  color: inherit;
  text-align: left;
  cursor: pointer;
  font: inherit;
}

.mob-card__open:hover {
  border-color: var(--accent);
}

.mob-card__thumbnail {
  display: block;
  aspect-ratio: 1;
  border-radius: var(--radius-md);
  overflow: hidden;
  background: var(--surface);
}

.mob-card__thumbnail img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.mob-card__placeholder {
  display: block;
  width: 100%;
  height: 100%;
  background: var(--surface-2);
}

.mob-card__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
  padding-right: var(--space-8); /* deja espacio al menú superpuesto */
}

.mob-card__name {
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.mob-card__menu {
  position: absolute;
  right: var(--space-2);
  top: var(--space-2);
}
</style>
