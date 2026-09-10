<script setup lang="ts">
/**
 * Botón de icono para toolbars/segmented controls (ticket 036, pasada de
 * fidelidad visual -- mockup 05: "Move/Scale/Rotate | Add cuboid/Add
 * bone | Duplicate/Delete | Undo/Redo" deben verse como icon buttons
 * profesionales, nunca `<button>` HTML sin diseñar).
 *
 * `label` es SIEMPRE obligatorio y hace doble función: `aria-label` (no
 * hay texto visible, fuente accesible real para lector de pantalla) y
 * texto de un tooltip VISUAL propio del design system (ticket 039,
 * corrección de producto -- `title=""` nativo dejó de ser aceptable
 * como única solución: sin estilos, sin delay consistente, sin
 * aparecer con foco por teclado en algunos navegadores). La burbuja es
 * `aria-hidden` a propósito -- el nombre accesible real es `aria-label`,
 * nunca depende del tooltip.
 *
 * `shortcut` es opcional y NUNCA se inventa -- solo se pasa en botones
 * con un atajo de teclado real ya cableado (ej. Undo/Redo en
 * `EditorToolbar.vue`), para no prometer un atajo que no existe.
 *
 * `active` es un estado real con class/aria propios (no una simple
 * variante de color) -- Visual Contract punto 10: nunca comunicar
 * estado solo por color.
 */
withDefaults(
  defineProps<{
    label: string
    shortcut?: string
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
    @click="(e) => $emit('click', e)"
  >
    <slot />
    <span class="icon-button__tooltip" role="tooltip" aria-hidden="true">
      {{ label }}
      <kbd v-if="shortcut" class="icon-button__tooltip-shortcut">{{ shortcut }}</kbd>
    </span>
  </button>
</template>

<style scoped>
.icon-button {
  position: relative; /* raíz de posicionamiento de .icon-button__tooltip */
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

/* Tooltip visual propio (ticket 039) -- oculto por defecto, aparece en
   :hover Y en :focus-visible (mouse Y teclado, punto 6 del ticket). El
   delay de aparición vive SOLO en la regla que lo muestra -- ocultarlo
   (volver a la regla base, sin delay) es instantáneo al salir del hover
   o perder el foco. */
.icon-button__tooltip {
  position: absolute;
  bottom: calc(100% + 8px);
  left: 50%;
  transform: translateX(-50%) translateY(4px);
  display: flex;
  align-items: center;
  gap: var(--space-1);
  padding: var(--space-1) var(--space-2);
  background: var(--surface-2);
  color: var(--text);
  border: var(--border-width) solid var(--border);
  border-radius: var(--radius-sm);
  box-shadow: var(--shadow-sm);
  font-size: var(--text-xs);
  font-weight: 400;
  white-space: nowrap;
  opacity: 0;
  visibility: hidden;
  pointer-events: none;
  transition:
    opacity var(--transition-fast),
    transform var(--transition-fast),
    visibility var(--transition-fast);
  z-index: 20;
}

.icon-button:hover .icon-button__tooltip,
.icon-button:focus-visible .icon-button__tooltip {
  opacity: 1;
  visibility: visible;
  transform: translateX(-50%) translateY(0);
  transition-delay: 350ms;
}

.icon-button__tooltip-shortcut {
  font-family: var(--font-mono);
  font-size: 10px;
  color: var(--muted);
  background: var(--surface);
  border: var(--border-width) solid var(--border);
  border-radius: 3px;
  padding: 0 4px;
}
</style>
