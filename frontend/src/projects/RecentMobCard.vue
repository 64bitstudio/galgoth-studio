<script setup lang="ts">
/**
 * Card horizontal de mob para "Continuar trabajando" (Inicio, ticket 071,
 * mockup rediseno.png). Mismo contenido/acciones que `MobCard.vue`
 * (thumbnail + nombre + estado + menú ⋮ Renombrar/Exportar/Eliminar) pero
 * en layout horizontal -- `MobCard.vue` sigue siendo la card vertical del
 * grid de detalle de proyecto, sin tocar.
 *
 * A diferencia de `MobCard`, el mob puede ser de CUALQUIER proyecto
 * (`RecentMobSummary.projectId`) y se muestra la fecha relativa de
 * edición junto al nombre (la referencia visual la pide ahí, no solo el
 * estado). Mismo criterio de estructura que `MobCard`/`ProjectCard`
 * (hallazgo real de Sonar, S6819): el menú vive como hermano del
 * `<button>` que abre el editor, nunca anidado dentro.
 */
import { thumbnailUrl } from '../api/apiConfig'
import { formatRelativeDate } from '../domain/relativeDate'
import GMenu, { type GMenuItem } from '../design-system/components/GMenu.vue'
import GStatusPill from '../design-system/components/GStatusPill.vue'
import type { MobStatus, RecentMobSummary } from './mobsApi'

defineProps<{ mob: RecentMobSummary }>()

const emit = defineEmits<{ open: [string]; action: [string, string] }>()

const MENU_ITEMS: GMenuItem[] = [
  { key: 'rename', label: 'Renombrar' },
  { key: 'export', label: 'Exportar' },
  { key: 'delete', label: 'Eliminar', danger: true },
]

/** `mobs.status` en la BD usa guion bajo (`in_progress`); GStatusPill usa guion medio -- mismo criterio que `MobCard.vue`. */
function toPillStatus(status: MobStatus): 'draft' | 'in-progress' | 'ready' {
  return status === 'in_progress' ? 'in-progress' : status
}
</script>

<template>
  <div class="recent-mob-card">
    <button type="button" class="recent-mob-card__open" @click="emit('open', mob.id)">
      <span class="recent-mob-card__thumbnail">
        <img v-if="mob.thumbnailKey" :src="thumbnailUrl(mob.thumbnailKey)!" alt="" />
        <span v-else class="recent-mob-card__placeholder" aria-hidden="true" />
      </span>
      <span class="recent-mob-card__body">
        <span class="recent-mob-card__name">{{ mob.name }}</span>
        <span class="recent-mob-card__meta">{{ formatRelativeDate(mob.updatedAt) }}</span>
      </span>
    </button>
    <span class="recent-mob-card__side">
      <GStatusPill :status="toPillStatus(mob.status)" />
      <GMenu :items="MENU_ITEMS" :label="`Acciones de ${mob.name}`" @select="(key) => emit('action', key, mob.id)" />
    </span>
  </div>
</template>

<style scoped>
.recent-mob-card {
  display: flex;
  align-items: stretch;
  gap: var(--space-3);
  padding: var(--space-3);
  background: var(--panel);
  border: var(--border-width) solid var(--border);
  border-radius: var(--radius-lg);
  transition:
    border-color 220ms cubic-bezier(0.16, 1, 0.3, 1),
    transform 220ms cubic-bezier(0.16, 1, 0.3, 1),
    box-shadow 220ms cubic-bezier(0.16, 1, 0.3, 1);
}

.recent-mob-card:hover {
  border-color: var(--accent);
  transform: translateY(-2px);
  box-shadow: var(--shadow-md);
}

.recent-mob-card__open {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  min-width: 0;
  flex: 1;
  background: transparent;
  border: none;
  border-radius: inherit;
  color: var(--text);
  text-align: left;
  cursor: pointer;
  font: inherit;
}

.recent-mob-card__thumbnail {
  width: 56px;
  height: 56px;
  flex-shrink: 0;
  border-radius: var(--radius-md);
  overflow: hidden;
  background: var(--surface);
}

.recent-mob-card__thumbnail img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.recent-mob-card__placeholder {
  display: block;
  width: 100%;
  height: 100%;
  background: var(--surface-2);
}

.recent-mob-card__body {
  min-width: 0;
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.recent-mob-card__name {
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.recent-mob-card__meta {
  font-size: var(--text-xs);
  color: var(--muted);
}

.recent-mob-card__side {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  justify-content: center;
  gap: var(--space-2);
}
</style>
