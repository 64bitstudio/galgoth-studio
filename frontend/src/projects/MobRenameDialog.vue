<script setup lang="ts">
/**
 * Diálogo de "Renombrar" para un mob (ticket 039, punto 5 -- CRUD de
 * mobs) -- mismo patrón exacto que `ProjectNameModal.vue` en modo
 * `rename`, sobre `AppDialog.vue`. Componente separado (en vez de
 * generalizar `ProjectNameModal` a "create proyecto | rename proyecto |
 * rename mob") para no acoplar ese componente ya usado en el flujo de
 * creación de proyectos a un concepto que no le corresponde.
 */
import { ref } from 'vue'
import AppDialog from '../design-system/components/AppDialog.vue'
import GButton from '../design-system/components/GButton.vue'

const props = defineProps<{
  initialName: string
  busy?: boolean
  error?: string | null
}>()

const emit = defineEmits<{ confirm: [string]; cancel: [] }>()

const name = ref(props.initialName)
const validationError = ref<string | null>(null)

function confirm(): void {
  if (props.busy) {
    return
  }
  const trimmed = name.value.trim()
  if (!trimmed) {
    validationError.value = 'El nombre no puede estar vacío.'
    return
  }
  validationError.value = null
  emit('confirm', trimmed)
}
</script>

<template>
  <AppDialog title="Renombrar mob" @cancel="emit('cancel')">
    <label class="mob-rename-dialog__label">
      Nombre
      <input v-model="name" type="text" class="mob-rename-dialog__input" aria-label="Nombre del mob" :disabled="busy" @keyup.enter="confirm" />
    </label>
    <p v-if="validationError" class="mob-rename-dialog__error">{{ validationError }}</p>
    <p v-if="error" class="mob-rename-dialog__error">{{ error }}</p>
    <template #actions>
      <GButton variant="ghost" :disabled="busy" @click="emit('cancel')">Cancelar</GButton>
      <GButton variant="primary" :disabled="busy" @click="confirm">{{ busy ? 'Guardando…' : 'Guardar' }}</GButton>
    </template>
  </AppDialog>
</template>

<style scoped>
.mob-rename-dialog__label {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  font-size: var(--text-sm);
  color: var(--muted);
}

.mob-rename-dialog__input {
  min-height: var(--hit-target-min);
  padding: 0 var(--space-3);
  background: var(--surface);
  border: var(--border-width) solid var(--border);
  border-radius: var(--radius-md);
  color: var(--text);
  font-size: var(--text-base);
}

.mob-rename-dialog__error {
  margin: 0;
  color: var(--danger);
  font-size: var(--text-sm);
}
</style>
