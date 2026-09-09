<script setup lang="ts">
/**
 * Tarjeta de mob del grid de detalle de proyecto (HU-04, mockup 12).
 * Miniatura (placeholder genérico mientras no exista pipeline de
 * thumbnails, mismo criterio que `ProjectCard.vue`, ticket 021) + nombre
 * + `GStatusPill` de estado.
 */
import GStatusPill from '../design-system/components/GStatusPill.vue'
import type { MobStatus, MobSummary } from './mobsApi'

defineProps<{ mob: MobSummary }>()

/** `mobs.status` en la BD usa guion bajo (`in_progress`); GStatusPill usa guion medio -- mismo valor, distinta convención de cada capa. */
function toPillStatus(status: MobStatus): 'draft' | 'in-progress' | 'ready' {
  return status === 'in_progress' ? 'in-progress' : status
}
</script>

<template>
  <div class="mob-card">
    <div class="mob-card__thumbnail">
      <img v-if="mob.thumbnailKey" :src="mob.thumbnailKey" alt="" />
      <span v-else class="mob-card__placeholder" aria-hidden="true" />
    </div>
    <div class="mob-card__footer">
      <span class="mob-card__name">{{ mob.name }}</span>
      <GStatusPill :status="toPillStatus(mob.status)" />
    </div>
  </div>
</template>

<style scoped>
.mob-card {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  padding: var(--space-3);
  background: var(--panel);
  border: var(--border-width) solid var(--border);
  border-radius: var(--radius-lg);
}

.mob-card__thumbnail {
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
}

.mob-card__name {
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
