<script setup lang="ts">
/**
 * Botón base. Visual Contract: hit target >= 40px, foco visible por
 * teclado (heredado de reset.css), CTAs primarios en verde menta.
 */
withDefaults(
  defineProps<{
    variant?: 'primary' | 'secondary' | 'danger' | 'ghost' | 'accent'
    disabled?: boolean
    type?: 'button' | 'submit'
  }>(),
  { variant: 'secondary', disabled: false, type: 'button' },
)

defineEmits<{ click: [MouseEvent] }>()
</script>

<template>
  <button
    :type="type"
    class="g-button"
    :class="`g-button--${variant}`"
    :disabled="disabled"
    @click="(e) => $emit('click', e)"
  >
    <slot name="icon" />
    <span><slot /></span>
  </button>
</template>

<style scoped>
.g-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-2);
  min-height: var(--hit-target-min);
  padding: 0 var(--space-4);
  border-radius: var(--radius-md);
  border: var(--border-width) solid transparent;
  font-size: var(--text-base);
  font-weight: 500;
  cursor: pointer;
  transition:
    background-color var(--transition-fast),
    border-color var(--transition-fast),
    color var(--transition-fast);
  white-space: nowrap;
}

.g-button:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

.g-button--primary {
  background: var(--accent);
  color: var(--accent-ink);
}
.g-button--primary:hover:not(:disabled) {
  background: var(--accent-hover);
}

.g-button--secondary {
  background: var(--surface-2);
  border-color: var(--border);
  color: var(--text);
}
.g-button--secondary:hover:not(:disabled) {
  border-color: var(--accent);
}

.g-button--danger {
  background: transparent;
  border-color: var(--danger);
  color: var(--danger);
}
.g-button--danger:hover:not(:disabled) {
  background: var(--danger-soft);
}

.g-button--ghost {
  background: transparent;
  color: var(--muted);
}
.g-button--ghost:hover:not(:disabled) {
  color: var(--text);
  background: var(--surface-2);
}

/* Corrección visual post-058 -- CTA de IA (ej. "Generar con IA"), fidelidad
   al mockup 07 v2 (`.btn--ai` de docs/definiciones/mockups/058-texture-editor-redesign-reference.html):
   contorno en --accent, no un botón "fantasma" más (ese ya es el rol de
   `ghost`, reservado para acciones secundarias tipo "Cancelar"). */
.g-button--accent {
  background: var(--surface);
  border-color: var(--accent);
  color: var(--accent);
}
.g-button--accent:hover:not(:disabled) {
  background: var(--accent-soft);
}
</style>
