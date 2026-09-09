<script setup lang="ts">
/**
 * Grupo de 3 inputs numéricos (X/Y/Z) para el panel de Propiedades
 * (ticket 036, mockup 05: "Posición/Tamaño/Rotación/Pivot", cada uno
 * con X/Y/Z visibles como campos editables, nunca un panel vacío).
 *
 * Puramente de presentación -- no sabe nada de geometría/deltas, solo
 * emite `update` con el vector completo cuando un eje cambia. Quien lo
 * usa (`InspectorPanel.vue`) decide qué método YA EXISTENTE de
 * `draftModelStore` llamar.
 */
import type { Vec3 } from '../domain/MobProjectModel'

const props = defineProps<{
  label: string
  value: Vec3
  disabled?: boolean
}>()

const emit = defineEmits<{ update: [Vec3] }>()

const AXES = ['X', 'Y', 'Z'] as const

function axisLabel(axis: string): string {
  return props.label + ' ' + axis
}

function onInput(axis: 0 | 1 | 2, event: Event): void {
  const raw = (event.target as HTMLInputElement).value
  const parsed = Number(raw)
  if (raw === '' || Number.isNaN(parsed)) {
    return
  }
  const next: Vec3 = [...props.value]
  next[axis] = parsed
  emit('update', next)
}

/** Redondeado a 2 decimales solo para mostrar -- el valor real que se envía en `onInput` es el que el usuario escribió, sin redondear. */
function displayValue(n: number): number {
  return Math.round(n * 100) / 100
}
</script>

<template>
  <div class="inspector-field">
    <span class="inspector-field__label">{{ label }}</span>
    <div class="inspector-field__axes">
      <label v-for="(axis, i) in AXES" :key="axis" class="inspector-field__axis">
        <span class="inspector-field__axis-letter" :class="`inspector-field__axis-letter--${axis.toLowerCase()}`">{{ axis }}</span>
        <input type="number" class="inspector-field__input" :value="displayValue(value[i])" :disabled="disabled" :aria-label="axisLabel(axis)" placeholder="0" step="0.5" @change="onInput(i as 0 | 1 | 2, $event)" />
      </label>
    </div>
  </div>
</template>

<style scoped>
.inspector-field {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.inspector-field__label {
  font-size: var(--text-sm);
  font-weight: 600;
  color: var(--muted);
}

.inspector-field__axes {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--space-2);
}

.inspector-field__axis {
  position: relative;
  display: flex;
  align-items: center;
}

.inspector-field__axis-letter {
  position: absolute;
  left: var(--space-2);
  font-size: var(--text-xs);
  font-weight: 700;
  color: var(--muted);
  pointer-events: none;
}

/* Colores de eje coherentes con los gizmos del viewport (X rojo, Y verde, Z azul) -- ayuda a leer la tabla numérica sin tener que releer la etiqueta cada vez. */
.inspector-field__axis-letter--x {
  color: #e5726b;
}
.inspector-field__axis-letter--y {
  color: #6bd97f;
}
.inspector-field__axis-letter--z {
  color: #6ba8e5;
}

.inspector-field__input {
  width: 100%;
  min-height: 34px;
  padding: 0 var(--space-2) 0 22px;
  background: var(--surface);
  border: var(--border-width) solid var(--border);
  border-radius: var(--radius-sm);
  color: var(--text);
  font-size: var(--text-sm);
  font-variant-numeric: tabular-nums;
}

.inspector-field__input:hover:not(:disabled) {
  border-color: var(--muted);
}

.inspector-field__input:disabled {
  color: var(--muted);
  cursor: not-allowed;
}

/* Quita las flechas nativas del spinner -- se ven fuera de lugar en una densidad de herramienta creativa (no son necesarias, el usuario escribe el valor). */
.inspector-field__input::-webkit-outer-spin-button,
.inspector-field__input::-webkit-inner-spin-button {
  -webkit-appearance: none;
  margin: 0;
}
.inspector-field__input[type='number'] {
  appearance: textfield;
}
</style>
