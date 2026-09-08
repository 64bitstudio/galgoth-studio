<script setup lang="ts">
/**
 * Tabs de workspace del mob (Visual Contract punto 8): Modelo | Textura |
 * Animación. Textura/Animación se muestran presentes pero deshabilitadas
 * este ciclo (Fase 3/4) -- nunca ocultas, para no romper la estructura
 * de navegación de los mockups. El motivo de "próximamente" es texto
 * visible, no solo un tooltip (regla de accesibilidad del equipo).
 */
export interface GTabItem {
  key: string
  label: string
  disabled?: boolean
  disabledReason?: string
}

defineProps<{
  items: GTabItem[]
  modelValue: string
}>()

const emit = defineEmits<{ 'update:modelValue': [string] }>()
</script>

<template>
  <div class="g-tabs" role="tablist">
    <button
      v-for="item in items"
      :key="item.key"
      type="button"
      role="tab"
      class="g-tabs__tab"
      :class="{
        'g-tabs__tab--active': item.key === modelValue,
        'g-tabs__tab--disabled': item.disabled,
      }"
      :aria-selected="item.key === modelValue"
      :disabled="item.disabled"
      @click="!item.disabled && emit('update:modelValue', item.key)"
    >
      {{ item.label }}
      <span v-if="item.disabled && item.disabledReason" class="g-tabs__hint">{{
        item.disabledReason
      }}</span>
    </button>
  </div>
</template>

<style scoped>
.g-tabs {
  display: flex;
  gap: var(--space-1);
  border-bottom: var(--border-width) solid var(--border);
}

.g-tabs__tab {
  position: relative;
  display: flex;
  align-items: center;
  gap: var(--space-2);
  min-height: var(--hit-target-min);
  padding: 0 var(--space-3);
  background: none;
  border: none;
  border-bottom: 2px solid transparent;
  color: var(--muted);
  font-size: var(--text-base);
  font-weight: 500;
  cursor: pointer;
}

.g-tabs__tab:hover:not(.g-tabs__tab--disabled) {
  color: var(--text);
}

.g-tabs__tab--active {
  color: var(--accent);
  border-bottom-color: var(--accent);
}

.g-tabs__tab--disabled {
  color: var(--muted);
  opacity: 0.55;
  cursor: not-allowed;
}

.g-tabs__hint {
  font-size: var(--text-xs);
  color: var(--muted);
  background: var(--surface-2);
  border: var(--border-width) solid var(--border);
  border-radius: 999px;
  padding: 1px 6px;
}
</style>
