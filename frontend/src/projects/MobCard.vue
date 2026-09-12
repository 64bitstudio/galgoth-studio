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
 *
 * Ticket 073 (rediseño del detalle de proyecto) -- vuelca la card al
 * patrón visual de `ProjectCard.vue`: el `GStatusPill` pasa del footer a
 * superpuesto sobre la miniatura (esquina superior derecha), y el footer
 * ahora muestra el tipo de base del mob (ícono + etiqueta, mismos 5
 * valores de `AddMobModal.vue`) más la fecha relativa de edición, mismo
 * criterio que `ProjectCard.vue`/`RecentMobCard.vue`. Mismas transiciones
 * de hover (borde/elevación/sombra) que el resto de las cards del
 * rediseño -- consistencia visual explícita pedida por el PO.
 */
import { thumbnailUrl } from '../api/apiConfig'
import { formatRelativeDate } from '../domain/relativeDate'
import GMenu, { type GMenuItem } from '../design-system/components/GMenu.vue'
import GStatusPill from '../design-system/components/GStatusPill.vue'
import IconArachnid from '../design-system/icons/IconArachnid.vue'
import IconCustomBase from '../design-system/icons/IconCustomBase.vue'
import IconFlying from '../design-system/icons/IconFlying.vue'
import IconHumanoid from '../design-system/icons/IconHumanoid.vue'
import IconQuadruped from '../design-system/icons/IconQuadruped.vue'
import type { BaseType, MobStatus, MobSummary } from './mobsApi'

const props = defineProps<{ mob: MobSummary }>()

const emit = defineEmits<{ open: [string]; action: [string, string] }>()

const MENU_ITEMS: GMenuItem[] = [
  { key: 'rename', label: 'Renombrar' },
  { key: 'export', label: 'Exportar' },
  { key: 'delete', label: 'Eliminar', danger: true },
]

/** Mismos 5 valores/etiquetas que `AddMobModal.vue`/`ConfigurationStep.vue` -- una sola fuente de íconos para no divergir. */
const BASE_TYPE_META: Record<BaseType, { label: string; icon: unknown }> = {
  humanoid: { label: 'Humanoide', icon: IconHumanoid },
  arachnid: { label: 'Arácnido', icon: IconArachnid },
  quadruped: { label: 'Cuadrúpedo', icon: IconQuadruped },
  flying: { label: 'Volador', icon: IconFlying },
  custom: { label: 'Personalizado', icon: IconCustomBase },
}

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
        <span class="mob-card__status"><GStatusPill :status="toPillStatus(mob.status)" /></span>
      </span>
      <span class="mob-card__body">
        <span class="mob-card__name">{{ mob.name }}</span>
        <span class="mob-card__footer">
          <span class="mob-card__type">
            <component :is="BASE_TYPE_META[mob.baseType].icon" :size="14" />
            {{ BASE_TYPE_META[mob.baseType].label }} · {{ formatRelativeDate(mob.updatedAt) }}
          </span>
        </span>
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
  background: var(--panel);
  border: var(--border-width) solid var(--border);
  border-radius: var(--radius-lg);
  overflow: hidden;
  transition:
    border-color 220ms cubic-bezier(0.16, 1, 0.3, 1),
    transform 220ms cubic-bezier(0.16, 1, 0.3, 1),
    box-shadow 220ms cubic-bezier(0.16, 1, 0.3, 1);
}

.mob-card:hover {
  border-color: var(--accent);
  transform: translateY(-2px);
  box-shadow: var(--shadow-md);
}

.mob-card__open {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  width: 100%;
  padding: 0;
  background: transparent;
  border: none;
  border-radius: inherit;
  color: inherit;
  text-align: left;
  cursor: pointer;
  font: inherit;
}

.mob-card__thumbnail {
  position: relative;
  display: block;
  aspect-ratio: 16 / 11;
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

.mob-card__status {
  position: absolute;
  top: var(--space-3);
  right: var(--space-3);
}

.mob-card__body {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: var(--space-4);
}

.mob-card__name {
  font-weight: 700;
  font-size: var(--text-md);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.mob-card__footer {
  display: flex;
  align-items: center;
  padding-right: var(--space-8); /* deja espacio al menú superpuesto */
}

.mob-card__type {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--muted);
  font-size: var(--text-xs);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.mob-card__type svg {
  flex-shrink: 0;
}

.mob-card__menu {
  position: absolute;
  right: var(--space-2);
  bottom: var(--space-3);
}
</style>
