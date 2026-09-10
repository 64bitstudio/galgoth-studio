<script setup lang="ts">
/**
 * Ticket 058 -- indicador de guardado de 4 estados de la toolbar
 * rediseñada (mockup 07 v2, VoBo del PO): guardado / cambios sin
 * guardar / guardando… / error al guardar.
 *
 * Componente propio y chico en vez de reutilizar `GStatusPill.vue`: ese
 * componente ya tiene su propio contrato semántico ("ready"/"in-progress"/
 * "draft", usado por `MobCard.vue` para el estado de un MOB) -- forzarle
 * 4 estados nuevos de guardado hubiese significado ramificar su
 * significado sin necesidad real de compartir markup/lógica (el patrón
 * visual -- punto + texto -- es simple y no vale la pena generalizar
 * entre dos conceptos de dominio distintos).
 *
 * El estado NUNCA se comunica solo por color (Visual Contract punto 10):
 * el texto (`LABELS`) es siempre la fuente primaria, el punto de color es
 * decorativo/redundante -- por eso lleva `aria-hidden`.
 */
import { computed } from 'vue'

export type TextureSaveState = 'saved' | 'dirty' | 'saving' | 'error'

const props = defineProps<{ state: TextureSaveState }>()

const LABELS: Record<TextureSaveState, string> = {
  saved: 'Guardado',
  dirty: 'Cambios sin guardar',
  saving: 'Guardando…',
  error: 'Error al guardar',
}

const label = computed(() => LABELS[props.state])
</script>

<template>
  <output class="texture-save-status" :class="`texture-save-status--${state}`">
    <span class="texture-save-status__dot" aria-hidden="true"></span>
    <span>{{ label }}</span>
  </output>
</template>

<style scoped>
.texture-save-status {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: var(--text-xs);
  color: var(--muted);
  padding: 0 var(--space-2);
  white-space: nowrap;
}

.texture-save-status__dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--muted);
  flex: none;
}

.texture-save-status--saved .texture-save-status__dot {
  background: var(--accent);
}

.texture-save-status--dirty .texture-save-status__dot {
  background: var(--warning);
}

.texture-save-status--saving .texture-save-status__dot {
  background: var(--accent);
  animation: texture-save-status-pulse 900ms ease-in-out infinite;
}

.texture-save-status--error {
  color: var(--danger);
}

.texture-save-status--error .texture-save-status__dot {
  background: var(--danger);
}

@keyframes texture-save-status-pulse {
  0%,
  100% {
    opacity: 1;
  }
  50% {
    opacity: 0.25;
  }
}
</style>
