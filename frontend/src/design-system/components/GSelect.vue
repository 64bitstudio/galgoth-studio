<script setup lang="ts">
/**
 * Ticket 058 -- select-con-valor genérico del design system. Hasta este
 * ticket NINGÚN componente cubría este patrón: `GMenu.vue` es un menú de
 * ACCIONES (sin estado de "opción elegida" persistente, cada click
 * dispara un evento y listo), no un select. `TextureCanvas.vue` (047)
 * usaba `<select>` nativo para región/tamaño de pincel -- el rediseño de
 * este ticket (mockup 07 v2, VoBo del PO) prohíbe explícitamente
 * `<select>` nativo en toda esta pantalla (consistencia visual con el
 * resto de controles del toolbar), así que se generaliza el patrón en
 * vez de duplicar la lógica de dropdown en cada selector nuevo (región,
 * tamaño de pincel, presets de zoom, estilo/parte del panel de IA).
 *
 * Mismo patrón ARIA ya establecido por `GMenu.vue`/`EditorToolbar.vue`
 * (S6819: sin `<ul>`/`<li>`, los botones de cada opción son hijos
 * DIRECTOS del contenedor `role="listbox"`) pero con la semántica de
 * SELECT real: `role="listbox"` + `aria-selected` por opción,
 * `aria-haspopup="listbox"` en el trigger, navegación por teclado
 * completa (ArrowUp/ArrowDown/Enter/Escape) tanto desde el trigger
 * (abre y mueve el foco a la lista) como dentro de la lista.
 *
 * `modelValue`/`update:modelValue` son siempre `string` -- todos los
 * consumidores reales de este ticket (regionKey, tamaño de pincel en
 * píxeles, porcentaje de zoom) ya modelan su valor como string o se
 * serializan trivialmente a uno; mantiene el componente simple sin
 * genéricos de TypeScript sobre `<script setup>` (limitación real de Vue
 * SFC con `defineProps` genérico + `withDefaults`).
 *
 * Corrección visual post-058 -- `.g-select__list` usa `app-scroll` (misma
 * utilidad del design system del ticket 039) para que su scroll interno
 * (listas largas, ej. todas las caras UV) use la scrollbar delgada/oscura
 * del proyecto en vez de la nativa del navegador, que desentonaba con el
 * resto de la UI.
 */
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'

export interface GSelectOption {
  value: string
  label: string
}

const props = defineProps<{
  modelValue: string
  options: GSelectOption[]
  label: string
  /** Placeholder mostrado si `modelValue` no matchea ninguna opción (ej. antes de la primera carga). */
  placeholder?: string
}>()

const emit = defineEmits<{ 'update:modelValue': [string] }>()

const rootRef = ref<HTMLElement>()
const listRef = ref<HTMLElement>()
const isOpen = ref(false)
const focusedIndex = ref(-1)

const selectedOption = computed(() => props.options.find((o) => o.value === props.modelValue) ?? null)
const triggerText = computed(() => selectedOption.value?.label ?? props.placeholder ?? '')

function close(): void {
  isOpen.value = false
  focusedIndex.value = -1
}

function open(): void {
  focusedIndex.value = props.options.findIndex((o) => o.value === props.modelValue)
  isOpen.value = true
  nextTick(() => {
    const target = Math.max(focusedIndex.value, 0)
    const buttons = listRef.value?.querySelectorAll<HTMLButtonElement>('.g-select__option')
    buttons?.[target]?.focus()
  })
}

function toggle(): void {
  if (isOpen.value) {
    close()
  } else {
    open()
  }
}

function pick(option: GSelectOption): void {
  emit('update:modelValue', option.value)
  close()
}

function focusOptionAt(index: number): void {
  const buttons = listRef.value?.querySelectorAll<HTMLButtonElement>('.g-select__option')
  const clamped = Math.max(0, Math.min((buttons?.length ?? 1) - 1, index))
  focusedIndex.value = clamped
  buttons?.[clamped]?.focus()
}

function handleTriggerKeydown(event: KeyboardEvent): void {
  if (event.key === 'ArrowDown' || event.key === 'Enter' || event.key === ' ') {
    event.preventDefault()
    open()
  }
}

function handleListKeydown(event: KeyboardEvent): void {
  if (event.key === 'ArrowDown') {
    event.preventDefault()
    focusOptionAt(focusedIndex.value + 1)
  } else if (event.key === 'ArrowUp') {
    event.preventDefault()
    focusOptionAt(focusedIndex.value - 1)
  } else if (event.key === 'Escape') {
    event.preventDefault()
    close()
    rootRef.value?.querySelector<HTMLButtonElement>('.g-select__trigger')?.focus()
  } else if (event.key === 'Enter' || event.key === ' ') {
    event.preventDefault()
    const option = props.options[focusedIndex.value]
    if (option) {
      pick(option)
    }
  }
}

function handleDocumentClick(event: MouseEvent): void {
  if (rootRef.value && !rootRef.value.contains(event.target as Node)) {
    close()
  }
}

onMounted(() => document.addEventListener('click', handleDocumentClick))
onBeforeUnmount(() => document.removeEventListener('click', handleDocumentClick))
</script>

<template>
  <div ref="rootRef" class="g-select" :class="{ 'g-select--open': isOpen }">
    <button
      type="button"
      class="g-select__trigger"
      :aria-label="label"
      aria-haspopup="listbox"
      :aria-expanded="isOpen"
      @click="toggle"
      @keydown="handleTriggerKeydown"
    >
      <span class="g-select__value">{{ triggerText }}</span>
      <svg class="g-select__chevron" viewBox="0 0 20 20" fill="none" aria-hidden="true">
        <path d="M5 7.5l5 5 5-5" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" />
      </svg>
    </button>
    <div v-if="isOpen" ref="listRef" class="g-select__list app-scroll" role="listbox" :aria-label="label" @keydown="handleListKeydown">
      <button
        v-for="(option, index) in options"
        :key="option.value"
        type="button"
        role="option"
        class="g-select__option"
        :class="{ 'g-select__option--active': option.value === modelValue, 'g-select__option--focus': index === focusedIndex }"
        :aria-selected="option.value === modelValue"
        @click="pick(option)"
        @mouseenter="focusedIndex = index"
      >
        <span>{{ option.label }}</span>
        <svg v-if="option.value === modelValue" class="g-select__check" viewBox="0 0 20 20" fill="none" aria-hidden="true">
          <path d="M4 10.5l4 4 8-9" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
        </svg>
      </button>
    </div>
  </div>
</template>

<style scoped>
.g-select {
  position: relative;
  min-width: 0;
}

.g-select__trigger {
  appearance: none;
  display: inline-flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
  width: 100%;
  min-height: var(--hit-target-min);
  padding: 0 var(--space-2) 0 var(--space-3);
  background: var(--surface);
  border: var(--border-width) solid var(--border);
  border-radius: var(--radius-md);
  color: var(--text);
  font-size: var(--text-sm);
  font-family: inherit;
  font-weight: 500;
  cursor: pointer;
  transition: var(--transition-fast);
}

.g-select__trigger:hover {
  border-color: #3a4956;
  background: var(--surface-2);
}

.g-select__trigger:focus-visible {
  box-shadow: var(--focus-ring);
  outline: none;
}

.g-select--open .g-select__trigger {
  border-color: var(--accent);
  background: var(--surface-2);
}

.g-select__value {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.g-select__chevron {
  width: 14px;
  height: 14px;
  color: var(--muted);
  flex: none;
  transition: transform 140ms ease;
}

.g-select--open .g-select__chevron {
  transform: rotate(180deg);
  color: var(--accent);
}

.g-select__list {
  position: absolute;
  top: calc(100% + 6px);
  left: 0;
  z-index: 50;
  min-width: 100%;
  background: var(--surface);
  border: var(--border-width) solid var(--border);
  border-radius: var(--radius-md);
  padding: var(--space-1);
  box-shadow: var(--shadow-md);
  max-height: 280px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 1px;
}

.g-select__option {
  appearance: none;
  border: none;
  background: transparent;
  text-align: left;
  color: var(--text);
  font-size: var(--text-sm);
  font-family: inherit;
  padding: var(--space-2) var(--space-3);
  border-radius: var(--radius-sm);
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: var(--space-2);
  white-space: nowrap;
  min-height: var(--hit-target-min);
}

.g-select__option:hover,
.g-select__option--focus {
  background: var(--surface-2);
}

.g-select__option--active {
  color: var(--accent);
  font-weight: 600;
  background: var(--accent-soft);
}

.g-select__option--active:hover {
  background: var(--accent-soft);
}

.g-select__check {
  margin-left: auto;
  width: 14px;
  height: 14px;
  flex: none;
}
</style>
