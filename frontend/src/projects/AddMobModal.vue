<script setup lang="ts">
/**
 * Modal "Agregar mob" (ticket 022, HU-03). Preserva la idea útil de
 * `add_mob_modal_legacy.png` (una herramienta de otro producto, "Texture
 * Studio MC") -- una selección categorizada y enfocada antes de
 * confirmar -- adaptada al modelo de datos real de Galgoth Studio: ese
 * legacy modal deja elegir un mob ESPECÍFICO de un catálogo de vanilla
 * Minecraft (Creeper, Zombi, ...), algo que no existe en este producto
 * (los mobs de Galgoth son generados/editados libremente, no reskins de
 * mobs vanilla) -- aquí la "categoría" es el campo real del dominio,
 * `baseType` (los 5 valores del CHECK constraint de `mobs.base_type`,
 * ticket 003), con nombre + confirmación explícita.
 *
 * Mismo patrón de `<dialog>` nativo que `ProjectNameModal.vue` (ticket
 * 021, hallazgo de Sonar S6819 -- ver esa nota).
 */
import { onMounted, ref } from 'vue'
import GButton from '../design-system/components/GButton.vue'
import type { BaseType } from './mobsApi'

const emit = defineEmits<{ confirm: [string, BaseType]; cancel: [] }>()

const BASE_TYPE_OPTIONS: Array<{ value: BaseType; label: string }> = [
  { value: 'humanoid', label: 'Humanoide' },
  { value: 'arachnid', label: 'Arácnido' },
  { value: 'quadruped', label: 'Cuadrúpedo' },
  { value: 'flying', label: 'Volador' },
  { value: 'custom', label: 'Personalizado' },
]

const dialogEl = ref<HTMLDialogElement>()
const name = ref('')
const baseType = ref<BaseType>('humanoid')
const validationError = ref<string | null>(null)

onMounted(() => {
  dialogEl.value?.showModal()
})

function confirm(): void {
  const trimmed = name.value.trim()
  if (!trimmed) {
    validationError.value = 'El nombre no puede estar vacío.'
    return
  }
  validationError.value = null
  emit('confirm', trimmed, baseType.value)
}

function handleBackdropClick(event: MouseEvent): void {
  if (event.target === dialogEl.value) {
    emit('cancel')
  }
}
</script>

<template>
  <dialog ref="dialogEl" class="add-mob-modal" aria-label="Agregar mob" @click="handleBackdropClick" @cancel.prevent="emit('cancel')">
    <h2 class="add-mob-modal__title">Agregar mob</h2>
    <label class="add-mob-modal__label">
      Nombre
      <input v-model="name" type="text" class="add-mob-modal__input" aria-label="Nombre del mob" @keyup.enter="confirm" />
    </label>
    <fieldset class="add-mob-modal__fieldset">
      <legend>Tipo</legend>
      <div class="add-mob-modal__base-types">
        <button
          v-for="option in BASE_TYPE_OPTIONS"
          :key="option.value"
          type="button"
          class="add-mob-modal__base-type"
          :class="{ 'add-mob-modal__base-type--selected': baseType === option.value }"
          :aria-pressed="baseType === option.value"
          @click="baseType = option.value"
        >
          {{ option.label }}
        </button>
      </div>
    </fieldset>
    <p v-if="validationError" class="add-mob-modal__error">{{ validationError }}</p>
    <div class="add-mob-modal__actions">
      <GButton variant="ghost" @click="emit('cancel')">Cancelar</GButton>
      <GButton variant="primary" @click="confirm">Agregar mob</GButton>
    </div>
  </dialog>
</template>

<style scoped>
.add-mob-modal {
  background: var(--panel);
  border: var(--border-width) solid var(--border);
  border-radius: var(--radius-lg);
  padding: var(--space-6);
  width: min(400px, 90vw);
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
  color: var(--text);
}

.add-mob-modal::backdrop {
  background: rgba(0, 0, 0, 0.6);
}

.add-mob-modal__title {
  margin: 0;
  font-size: var(--text-lg);
}

.add-mob-modal__label {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  font-size: var(--text-sm);
  color: var(--muted);
}

.add-mob-modal__input {
  min-height: var(--hit-target-min);
  padding: 0 var(--space-3);
  background: var(--surface);
  border: var(--border-width) solid var(--border);
  border-radius: var(--radius-md);
  color: var(--text);
  font-size: var(--text-base);
}

.add-mob-modal__fieldset {
  border: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.add-mob-modal__fieldset legend {
  font-size: var(--text-sm);
  color: var(--muted);
  padding: 0;
}

.add-mob-modal__base-types {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.add-mob-modal__base-type {
  min-height: var(--hit-target-min);
  padding: 0 var(--space-3);
  background: var(--surface);
  border: var(--border-width) solid var(--border);
  border-radius: var(--radius-md);
  color: var(--text);
  cursor: pointer;
  font-size: var(--text-sm);
}

.add-mob-modal__base-type--selected {
  background: var(--accent-soft);
  border-color: var(--accent);
  color: var(--accent);
}

.add-mob-modal__error {
  margin: 0;
  color: var(--danger);
  font-size: var(--text-sm);
}

.add-mob-modal__actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--space-2);
}
</style>
