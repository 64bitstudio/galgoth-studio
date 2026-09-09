<script setup lang="ts">
/**
 * Paso 2 "Configuración" del wizard (ticket 027, HU-10 AC #2, mockup 02):
 * nombre + tipo base + resolución de textura.
 *
 * Simplificación consciente y documentada (misma sesión que decidió el
 * alcance del ticket, VoBo del PO): el mockup muestra "Detectar con IA"
 * como una opción más de tipo base, pre-seleccionada por defecto y
 * reemplazada por la propuesta real de 028 -- ese ticket no existe
 * todavía, así que esa opción se omite este ciclo (no hay nada real que
 * "detectar") y el tipo base es una selección manual, igual que en
 * `AddMobModal.vue` (022). "Resolución de textura" es solo un campo de
 * UI por ahora -- nada en el backend lo consume todavía (`AutoUv`, 006,
 * calcula el atlas real a partir de la geometría, no de una preferencia
 * elegida de antemano).
 */
import { ref } from 'vue'
import GButton from '../../design-system/components/GButton.vue'
import type { BaseType } from '../../projects/mobsApi'

const BASE_TYPE_OPTIONS: Array<{ value: BaseType; label: string }> = [
  { value: 'humanoid', label: 'Humanoide' },
  { value: 'arachnid', label: 'Arácnido' },
  { value: 'quadruped', label: 'Cuadrúpedo' },
  { value: 'flying', label: 'Volador' },
  { value: 'custom', label: 'Personalizado' },
]

const TEXTURE_RESOLUTIONS = ['64×64', '128×128', '256×256']

const props = defineProps<{ referencePreviewUrl: string; submitting: boolean; submitError: string | null }>()
const emit = defineEmits<{ confirm: [{ name: string; baseType: BaseType }]; back: [] }>()

const name = ref('')
const baseType = ref<BaseType>('humanoid')
const textureResolution = ref('128×128')
const validationError = ref<string | null>(null)

function confirm(): void {
  const trimmed = name.value.trim()
  if (!trimmed) {
    validationError.value = 'El nombre no puede estar vacío.'
    return
  }
  validationError.value = null
  emit('confirm', { name: trimmed, baseType: baseType.value })
}
</script>

<template>
  <div class="configuration-step">
    <div class="configuration-step__preview">
      <img :src="props.referencePreviewUrl" alt="Imagen de referencia elegida" />
      <button type="button" class="configuration-step__back" @click="emit('back')">← Cambiar imagen</button>
    </div>

    <div class="configuration-step__form">
      <h2 class="configuration-step__title">Información del mob</h2>

      <label class="configuration-step__label">
        Nombre
        <input v-model="name" type="text" class="configuration-step__input" aria-label="Nombre del mob" @keyup.enter="confirm" />
      </label>

      <fieldset class="configuration-step__fieldset">
        <legend>Tipo de entidad</legend>
        <div class="configuration-step__base-types">
          <button
            v-for="option in BASE_TYPE_OPTIONS"
            :key="option.value"
            type="button"
            class="configuration-step__base-type"
            :class="{ 'configuration-step__base-type--selected': baseType === option.value }"
            :aria-pressed="baseType === option.value"
            @click="baseType = option.value"
          >
            {{ option.label }}
          </button>
        </div>
      </fieldset>

      <label class="configuration-step__label">
        Resolución de textura
        <select v-model="textureResolution" class="configuration-step__select" aria-label="Resolución de textura">
          <option v-for="resolution in TEXTURE_RESOLUTIONS" :key="resolution" :value="resolution">
            {{ resolution }}{{ resolution === '128×128' ? ' (recomendado)' : '' }}
          </option>
        </select>
      </label>

      <p v-if="validationError" class="configuration-step__error">{{ validationError }}</p>
      <p v-if="props.submitError" class="configuration-step__error">{{ props.submitError }}</p>

      <div class="configuration-step__actions">
        <GButton variant="primary" :disabled="props.submitting" @click="confirm">
          {{ props.submitting ? 'Creando…' : 'Generar con IA →' }}
        </GButton>
      </div>
    </div>
  </div>
</template>

<style scoped>
.configuration-step {
  display: flex;
  gap: var(--space-6);
  flex-wrap: wrap;
}

.configuration-step__preview {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  width: 220px;
  flex-shrink: 0;
}

.configuration-step__preview img {
  width: 100%;
  aspect-ratio: 1;
  object-fit: cover;
  border-radius: var(--radius-lg);
  border: var(--border-width) solid var(--border);
}

.configuration-step__back {
  background: none;
  border: none;
  color: var(--muted);
  cursor: pointer;
  font-size: var(--text-sm);
  padding: 0;
  text-align: left;
}

.configuration-step__back:hover {
  color: var(--accent);
}

.configuration-step__form {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
  flex: 1;
  min-width: 240px;
}

.configuration-step__title {
  margin: 0;
}

.configuration-step__label {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  font-size: var(--text-sm);
  color: var(--muted);
}

.configuration-step__input,
.configuration-step__select {
  min-height: var(--hit-target-min);
  padding: 0 var(--space-3);
  background: var(--surface);
  border: var(--border-width) solid var(--border);
  border-radius: var(--radius-md);
  color: var(--text);
  font-size: var(--text-base);
}

.configuration-step__fieldset {
  border: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.configuration-step__fieldset legend {
  font-size: var(--text-sm);
  color: var(--muted);
  padding: 0;
}

.configuration-step__base-types {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.configuration-step__base-type {
  min-height: var(--hit-target-min);
  padding: 0 var(--space-3);
  background: var(--surface);
  border: var(--border-width) solid var(--border);
  border-radius: var(--radius-md);
  color: var(--text);
  cursor: pointer;
  font-size: var(--text-sm);
}

.configuration-step__base-type--selected {
  background: var(--accent-soft);
  border-color: var(--accent);
  color: var(--accent);
}

.configuration-step__error {
  margin: 0;
  color: var(--danger);
  font-size: var(--text-sm);
}

.configuration-step__actions {
  display: flex;
  justify-content: flex-end;
}
</style>
