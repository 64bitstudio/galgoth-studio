<script setup lang="ts">
/**
 * Tarjeta de mob de la ficha pública de Explorar (ticket 088, HU-6).
 * A diferencia de `MobCard.vue` (detalle de "Mis proyectos"), esta es
 * puramente informativa: sin menú ⋮, sin `<button>` que abra nada --
 * el ticket excluye explícitamente el visor 3D en modo lectura, así
 * que no hay ningún destino al que navegar desde acá.
 */
import { thumbnailUrl } from '../api/apiConfig'
import GStatusPill from '../design-system/components/GStatusPill.vue'
import IconArachnid from '../design-system/icons/IconArachnid.vue'
import IconCustomBase from '../design-system/icons/IconCustomBase.vue'
import IconFlying from '../design-system/icons/IconFlying.vue'
import IconHumanoid from '../design-system/icons/IconHumanoid.vue'
import IconQuadruped from '../design-system/icons/IconQuadruped.vue'
import type { BaseType, MobStatus, MobSummary } from '../projects/mobsApi'

defineProps<{ mob: MobSummary }>()

/** Mismos 5 valores/etiquetas que `MobCard.vue`/`AddMobModal.vue` -- una sola fuente de íconos para no divergir. */
const BASE_TYPE_META: Record<BaseType, { label: string; icon: unknown }> = {
  humanoid: { label: 'Humanoide', icon: IconHumanoid },
  arachnid: { label: 'Arácnido', icon: IconArachnid },
  quadruped: { label: 'Cuadrúpedo', icon: IconQuadruped },
  flying: { label: 'Volador', icon: IconFlying },
  custom: { label: 'Personalizado', icon: IconCustomBase },
}

function toPillStatus(status: MobStatus): 'draft' | 'in-progress' | 'ready' {
  return status === 'in_progress' ? 'in-progress' : status
}
</script>

<template>
  <div class="explore-mob-card">
    <span class="explore-mob-card__thumbnail">
      <img v-if="mob.thumbnailKey" :src="thumbnailUrl(mob.thumbnailKey)!" alt="" />
      <span v-else class="explore-mob-card__placeholder" aria-hidden="true" />
      <span class="explore-mob-card__status"><GStatusPill :status="toPillStatus(mob.status)" /></span>
    </span>
    <span class="explore-mob-card__body">
      <span class="explore-mob-card__name">{{ mob.name }}</span>
      <span class="explore-mob-card__type">
        <component :is="BASE_TYPE_META[mob.baseType].icon" :size="14" />
        {{ BASE_TYPE_META[mob.baseType].label }}
      </span>
    </span>
  </div>
</template>

<style scoped>
.explore-mob-card {
  background: var(--panel);
  border: var(--border-width) solid var(--border);
  border-radius: var(--radius-lg);
  overflow: hidden;
}

.explore-mob-card__thumbnail {
  position: relative;
  display: block;
  aspect-ratio: 16 / 11;
  background: var(--surface);
}

.explore-mob-card__thumbnail img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.explore-mob-card__placeholder {
  display: block;
  width: 100%;
  height: 100%;
  background: var(--surface-2);
}

.explore-mob-card__status {
  position: absolute;
  top: var(--space-3);
  right: var(--space-3);
}

.explore-mob-card__body {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: var(--space-4);
}

.explore-mob-card__name {
  font-weight: 700;
  font-size: var(--text-md);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.explore-mob-card__type {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--muted);
  font-size: var(--text-xs);
}

.explore-mob-card__type svg {
  flex-shrink: 0;
}
</style>
