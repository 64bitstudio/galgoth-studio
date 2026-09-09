<script setup lang="ts">
/**
 * Botón de icono para toolbars/segmented controls (ticket 036, pasada de
 * fidelidad visual -- mockup 05: "Move/Scale/Rotate | Add cuboid/Add
 * bone | Duplicate/Delete | Undo/Redo" deben verse como icon buttons
 * profesionales, nunca `<button>` HTML sin diseñar).
 *
 * `label` es SIEMPRE obligatorio y hace doble función: `aria-label` (no
 * hay texto visible) y tooltip nativo (`title`) -- mismo criterio de
 * accesibilidad que el resto del design system (GTabs/GMenu: el motivo
 * de un estado nunca vive solo en un tooltip que un lector de pantalla
 * no anuncia por sí solo, pero acá SÍ es apropiado porque `aria-label`
 * cubre el caso de lector de pantalla y `title` cubre el mouse).
 *
 * `active` es un estado real con class/aria propios (no una simple
 * variante de color) -- Visual Contract punto 10: nunca comunicar
 * estado solo por color.
 */
withDefaults(
  defineProps<{
    label: string
    active?: boolean
    disabled?: boolean
    size?: 'sm' | 'md'
  }>(),
  { active: false, disabled: false, size: 'md' },
)

defineEmits<{ click: [MouseEvent] }>()
</script>

<template>
  <button
    type="button"
    class="icon-button"
    :class="[`icon-button--${size}`, { 'icon-button--active': active }]"
    :disabled="disabled"
    :aria-pressed="active"
    :aria-label="label"
    :title="label"
    @click="(e) => $emit('click', e)"
  >
    <slot />
  </button>
</template>

<style scoped>
.icon-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  padding: 0;
  border-radius: var(--radius-md);
  border: var(--border-width) solid transparent;
  background: transparent;
  color: var(--muted);
  cursor: pointer;
  transition:
    background-color var(--transition-fast),
    color var(--transition-fast),
    border-color var(--transition-fast);
}

.icon-button--md {
  width: var(--hit-target-min);
  height: var(--hit-target-min);
}

.icon-button--sm {
  width: 28px;
  height: 28px;
}

.icon-button:hover:not(:disabled) {
  background: var(--surface-2);
  color: var(--text);
}

.icon-button:active:not(:disabled) {
  background: var(--surface);
}

.icon-button--active {
  background: var(--accent-soft);
  color: var(--accent);
  border-color: var(--accent);
}

.icon-button--active:hover:not(:disabled) {
  background: var(--accent-soft);
  color: var(--accent);
}

.icon-button:disabled {
  color: var(--muted);
  opacity: 0.4;
  cursor: not-allowed;
}
</style>
