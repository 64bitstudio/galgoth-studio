<script setup lang="ts">
/**
 * Ticket 058 -- selector de color activo del rediseño de la toolbar
 * (mockup 07 v2, VoBo del PO). Reemplaza el input de color VISIBLE de
 * 047 (que ocupaba ancho fijo en la columna lateral) por un trigger
 * compacto (swatch del color activo) que abre un panel con: la paleta
 * fija de 8 colores (`PALETTE`, sin cambios respecto a 047) + una fila
 * "personalizado" para elegir CUALQUIER color -- el PO señaló
 * explícitamente en la ronda de ajustes que la paleta fija sola no
 * alcanza.
 *
 * Único componente de esta pantalla que SÍ usa un control de color
 * nativo del navegador -- excepción explícita ya aceptada por el PO (ver
 * ticket): oculto visualmente (mismo patrón `sr-only`-like que
 * `TextureImportPanel.__file-input`, ver esa docstring para el motivo de
 * no usar `display:none` -- mantiene el elemento fuera de la vista pero
 * disponible para AT), disparado programáticamente por el swatch
 * "Personalizado". El campo de texto hex es el camino accesible/preciso
 * equivalente para quien no puede o no quiere abrir el picker nativo del
 * SO.
 *
 * Cada swatch/trigger lleva, además de `aria-label`, un `span.sr-only`
 * con el mismo texto -- nombre accesible redundante a propósito (no solo
 * por robustez ante distintos lectores de pantalla): un swatch es un
 * cuadrado de color puro sin ningún texto visible, y varios quedan uno
 * al lado del otro sin nada entre medio.
 */
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { PALETTE } from './texturePalette'

const HEX_RE = /^#[0-9a-fA-F]{6}$/

const props = defineProps<{ modelValue: string }>()
const emit = defineEmits<{ 'update:modelValue': [string] }>()

const rootRef = ref<HTMLElement>()
const isOpen = ref(false)
const nativeInputRef = ref<HTMLInputElement>()
const hexDraft = ref(props.modelValue)

watch(
  () => props.modelValue,
  (value) => {
    hexDraft.value = value
  },
)

const isCustomActive = computed(() => !PALETTE.some((c) => c.toLowerCase() === props.modelValue.toLowerCase()))

function toggle(): void {
  isOpen.value = !isOpen.value
}

function close(): void {
  isOpen.value = false
}

function pickSwatch(color: string): void {
  emit('update:modelValue', color)
  close()
}

function openNativePicker(): void {
  if (nativeInputRef.value) {
    nativeInputRef.value.value = props.modelValue
    nativeInputRef.value.click()
  }
}

function handleNativeInput(event: Event): void {
  emit('update:modelValue', (event.target as HTMLInputElement).value)
}

function commitHexDraft(): void {
  let value = hexDraft.value.trim()
  if (!value.startsWith('#')) {
    value = `#${value}`
  }
  if (HEX_RE.test(value)) {
    emit('update:modelValue', value.toLowerCase())
  } else {
    hexDraft.value = props.modelValue
  }
}

function handleHexKeydown(event: KeyboardEvent): void {
  if (event.key === 'Enter') {
    commitHexDraft()
    ;(event.target as HTMLInputElement).blur()
  } else if (event.key === 'Escape') {
    hexDraft.value = props.modelValue
    ;(event.target as HTMLInputElement).blur()
  }
}

/** Mismo patrón que `GSelect.vue`: listener global en `document`, filtrado por `rootRef.contains`, para cerrar el panel al clickear afuera. */
function handleDocumentClick(event: MouseEvent): void {
  if (isOpen.value && rootRef.value && !rootRef.value.contains(event.target as Node)) {
    close()
  }
}

onMounted(() => document.addEventListener('click', handleDocumentClick))
onBeforeUnmount(() => document.removeEventListener('click', handleDocumentClick))

function swatchLabel(color: string): string {
  return `Color ${color}`
}
</script>

<template>
  <div ref="rootRef" class="texture-color-picker">
    <button type="button" class="texture-color-picker__trigger" aria-label="Color activo" aria-haspopup="true" :aria-expanded="isOpen" @click="toggle">
      <span class="texture-color-picker__sr-only">Color activo</span>
      <span class="texture-color-picker__swatch-main" aria-hidden="true" :style="{ background: modelValue }"></span>
    </button>

    <fieldset v-if="isOpen" class="texture-color-picker__panel">
      <legend class="texture-color-picker__sr-only">Elegir color activo</legend>
      <div class="texture-color-picker__swatches">
        <button v-for="color in PALETTE" :key="color" type="button" class="texture-color-picker__swatch" :class="{ 'texture-color-picker__swatch--active': color.toLowerCase() === modelValue.toLowerCase() }" :style="{ background: color }" :aria-label="swatchLabel(color)" :aria-pressed="color.toLowerCase() === modelValue.toLowerCase()" @click="pickSwatch(color)"><span class="texture-color-picker__sr-only">{{ swatchLabel(color) }}</span></button>
      </div>

      <div class="texture-color-picker__custom-row">
        <button type="button" class="texture-color-picker__swatch texture-color-picker__swatch--custom" :class="{ 'texture-color-picker__swatch--active': isCustomActive }" aria-label="Elegir cualquier color" @click="openNativePicker"><span class="texture-color-picker__sr-only">Elegir cualquier color</span></button>
        <label for="texture-color-picker-hex-input" class="texture-color-picker__sr-only">Código hexadecimal del color activo</label>
        <input id="texture-color-picker-hex-input" v-model="hexDraft" type="text" class="texture-color-picker__hex-input" maxlength="7" aria-label="Código hexadecimal del color activo" @keydown="handleHexKeydown" @blur="commitHexDraft" />
      </div>

      <label for="texture-color-picker-native-input" class="texture-color-picker__sr-only">Selector de color nativo del sistema</label>
      <input id="texture-color-picker-native-input" ref="nativeInputRef" type="color" class="texture-color-picker__native-input" aria-label="Selector de color nativo del sistema" tabindex="-1" :value="modelValue" @input="handleNativeInput" />
    </fieldset>
  </div>
</template>

<style scoped>
.texture-color-picker {
  position: relative;
}

.texture-color-picker__sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}

.texture-color-picker__trigger {
  appearance: none;
  border: var(--border-width) solid var(--border);
  background: var(--surface);
  width: var(--hit-target-min);
  height: var(--hit-target-min);
  border-radius: var(--radius-md);
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  transition: var(--transition-fast);
}

.texture-color-picker__trigger:hover {
  border-color: #3a4956;
  background: var(--surface-2);
}

.texture-color-picker__trigger:focus-visible {
  box-shadow: var(--focus-ring);
  outline: none;
}

.texture-color-picker__swatch-main {
  width: 18px;
  height: 18px;
  border-radius: 5px;
  border: 1px solid rgba(255, 255, 255, 0.25);
  display: block;
}

.texture-color-picker__panel {
  position: absolute;
  top: calc(100% + 6px);
  left: 0;
  z-index: 50;
  width: 196px;
  min-width: 0;
  margin: 0;
  background: var(--surface);
  border: var(--border-width) solid var(--border);
  border-radius: var(--radius-md);
  padding: var(--space-2);
  box-shadow: var(--shadow-md);
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
}

.texture-color-picker__swatches {
  display: flex;
  gap: 5px;
  flex-wrap: wrap;
}

.texture-color-picker__swatch {
  width: 24px;
  height: 24px;
  border-radius: var(--radius-sm);
  cursor: pointer;
  border: 2px solid transparent;
  padding: 0;
}

.texture-color-picker__swatch--active {
  border-color: var(--accent);
}

.texture-color-picker__custom-row {
  display: flex;
  align-items: center;
  gap: 6px;
  padding-top: 6px;
  margin-top: 2px;
  border-top: var(--border-width) solid var(--border);
}

.texture-color-picker__swatch--custom {
  background: conic-gradient(from 180deg, #f46b6b, #f2c66d, #48e5a0, #3f6f8f, #a35bd6, #f46b6b);
  position: relative;
  flex: none;
}

.texture-color-picker__swatch--custom::after {
  content: '';
  position: absolute;
  inset: 0;
  border-radius: inherit;
  box-shadow: inset 0 0 0 1px rgba(0, 0, 0, 0.3);
}

.texture-color-picker__hex-input {
  flex: 1;
  min-width: 0;
  height: 28px;
  padding: 0 8px;
  background: var(--surface-2);
  border: var(--border-width) solid var(--border);
  border-radius: var(--radius-sm);
  color: var(--text);
  font-family: var(--font-mono);
  font-size: 12px;
}

.texture-color-picker__hex-input:focus-visible {
  outline: none;
  border-color: var(--accent);
  box-shadow: var(--focus-ring);
}

.texture-color-picker__native-input {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}
</style>
