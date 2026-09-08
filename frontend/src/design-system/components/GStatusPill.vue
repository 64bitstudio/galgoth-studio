<script setup lang="ts">
/**
 * Indicador de estado. Visual Contract punto 10: el estado nunca se
 * comunica solo por color -- cada status trae su propio ícono además
 * del color.
 */
import { computed } from 'vue'
import IconCheck from '../icons/IconCheck.vue'
import IconInProgress from '../icons/IconInProgress.vue'
import IconDraft from '../icons/IconDraft.vue'

const props = withDefaults(
  defineProps<{
    status: 'ready' | 'in-progress' | 'draft'
  }>(),
  {},
)

const STATUS_META = {
  ready: { label: 'Ready', icon: IconCheck, tone: 'accent' as const },
  'in-progress': {
    label: 'In progress',
    icon: IconInProgress,
    tone: 'warning' as const,
  },
  draft: { label: 'Draft', icon: IconDraft, tone: 'neutral' as const },
} as const

const meta = computed(() => STATUS_META[props.status])
</script>

<template>
  <span class="g-status-pill" :class="`g-status-pill--${meta.tone}`">
    <component :is="meta.icon" :size="13" />
    {{ meta.label }}
  </span>
</template>

<style scoped>
.g-status-pill {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  padding: 2px var(--space-2);
  border-radius: 999px;
  font-size: var(--text-xs);
  font-weight: 600;
  border: var(--border-width) solid var(--border);
  line-height: 1.6;
}

.g-status-pill--accent {
  color: var(--accent);
  background: var(--accent-soft);
  border-color: transparent;
}
.g-status-pill--warning {
  color: var(--warning);
  background: var(--warning-soft);
  border-color: transparent;
}
.g-status-pill--neutral {
  color: var(--muted);
  background: var(--surface-2);
}
</style>
