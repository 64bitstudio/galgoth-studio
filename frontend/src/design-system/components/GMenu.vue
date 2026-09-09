<script setup lang="ts">
/**
 * Menú de acciones desplegable (ticket 021, primer uso: acciones de la
 * tarjeta de proyecto -- Rename/Duplicate/Export/Delete, HU-02). Un ítem
 * deshabilitado muestra su razón como texto visible bajo la etiqueta,
 * nunca solo un tooltip -- mismo criterio de accesibilidad ya establecido
 * en GTabs (ticket 002: "el motivo de 'próximamente' es texto visible,
 * no solo un tooltip").
 *
 * Sin `<ul>`/`<li>` a propósito (hallazgo real de Sonar, S6819): el
 * patrón ARIA `role="menu"` con hijos envueltos en `<li role="presentation">`
 * dispara la misma regla que prohíbe emular roles nativos con ARIA --
 * los botones de cada ítem son hijos DIRECTOS del contenedor `role="menu"`,
 * sin ningún wrapper que necesite neutralizar su semántica.
 */
import { ref } from 'vue'

export interface GMenuItem {
  key: string
  label: string
  disabled?: boolean
  disabledReason?: string
  danger?: boolean
}

defineProps<{
  items: GMenuItem[]
  label: string
}>()

const emit = defineEmits<{ select: [string] }>()

const isOpen = ref(false)

function toggle(): void {
  isOpen.value = !isOpen.value
}

function select(item: GMenuItem): void {
  if (item.disabled) {
    return
  }
  emit('select', item.key)
  isOpen.value = false
}
</script>

<template>
  <div class="g-menu">
    <button type="button" class="g-menu__trigger" :aria-label="label" aria-haspopup="true" :aria-expanded="isOpen" @click="toggle">
      ⋮
    </button>
    <div v-if="isOpen" class="g-menu__list" role="menu">
      <button
        v-for="item in items"
        :key="item.key"
        type="button"
        role="menuitem"
        class="g-menu__item"
        :class="{ 'g-menu__item--danger': item.danger, 'g-menu__item--disabled': item.disabled }"
        :disabled="item.disabled"
        @click="select(item)"
      >
        <span>{{ item.label }}</span>
        <span v-if="item.disabled && item.disabledReason" class="g-menu__hint">{{ item.disabledReason }}</span>
      </button>
    </div>
  </div>
</template>

<style scoped>
.g-menu {
  position: relative;
  display: inline-block;
}

.g-menu__trigger {
  min-width: var(--hit-target-min);
  min-height: var(--hit-target-min);
  border: var(--border-width) solid transparent;
  background: transparent;
  color: var(--text);
  border-radius: var(--radius-md);
  cursor: pointer;
  font-size: var(--text-lg);
  line-height: 1;
}

.g-menu__trigger:hover {
  background: var(--surface-2);
}

.g-menu__list {
  position: absolute;
  right: 0;
  top: calc(100% + var(--space-1));
  z-index: 10;
  display: flex;
  flex-direction: column;
  margin: 0;
  padding: var(--space-1);
  background: var(--surface);
  border: var(--border-width) solid var(--border);
  border-radius: var(--radius-md);
  min-width: 160px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.4);
}

.g-menu__item {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  width: 100%;
  min-height: var(--hit-target-min);
  padding: var(--space-2) var(--space-3);
  background: transparent;
  border: none;
  border-radius: var(--radius-sm);
  color: var(--text);
  text-align: left;
  cursor: pointer;
  font-size: var(--text-sm);
}

.g-menu__item:hover:not(:disabled) {
  background: var(--surface-2);
}

.g-menu__item--danger {
  color: var(--danger);
}

.g-menu__item--disabled {
  color: var(--muted);
  cursor: not-allowed;
}

.g-menu__hint {
  font-size: var(--text-xs);
  color: var(--muted);
}
</style>
