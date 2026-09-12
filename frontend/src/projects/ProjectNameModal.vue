<script setup lang="ts">
/**
 * Modal de nombre de proyecto (ticket 021) -- se reutiliza para "Nuevo
 * proyecto" (HU-01) y "Rename" (HU-02, menú de acciones de la tarjeta):
 * ambos flujos son idénticos salvo el título/valor inicial/verbo del
 * botón de confirmar. HU-01 AC #3: solo se pide nombre, sin campos
 * técnicos de IA/geometría -- por diseño, este modal no tiene ningún
 * otro campo EN MODO "create".
 *
 * Ticket 039: reconstruido sobre `AppDialog.vue` (el shell genérico de
 * diálogo del design system) en vez de tener su propio `<dialog>` a
 * mano -- mismo comportamiento exacto, ahora compartido con
 * `ConfirmDialog.vue` y cualquier otro diálogo futuro. `busy`/`error`
 * nuevos: bloquea doble submit real mientras el caller espera al
 * backend (antes se podía apretar "Guardar" varias veces sin ninguna
 * señal).
 *
 * Ticket 073 (rediseño del detalle de proyecto) -- en modo "rename"
 * agrega un campo de descripción opcional (mismo lápiz que ya editaba
 * solo el nombre, ahora "Editar proyecto"). `confirm` emite SIEMPRE
 * `(nombre, descripción)` -- en modo "create" la descripción viaja como
 * `null` sin más, el caller de creación simplemente la ignora (esa
 * pantalla no la usa, `createProject` no la acepta).
 */
import { ref, watch } from 'vue'
import AppDialog from '../design-system/components/AppDialog.vue'
import GButton from '../design-system/components/GButton.vue'

const props = defineProps<{
  mode: 'create' | 'rename'
  initialName?: string
  initialDescription?: string | null
  busy?: boolean
  error?: string | null
}>()

const emit = defineEmits<{ confirm: [string, string | null]; cancel: [] }>()

const name = ref(props.initialName ?? '')
const description = ref(props.initialDescription ?? '')
const validationError = ref<string | null>(null)

watch(
  () => props.initialName,
  (value) => {
    name.value = value ?? ''
  },
)

watch(
  () => props.initialDescription,
  (value) => {
    description.value = value ?? ''
  },
)

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
  emit('confirm', trimmed, description.value.trim() || null)
}
</script>

<template>
  <AppDialog :title="mode === 'create' ? 'Nuevo proyecto' : 'Editar proyecto'" @cancel="emit('cancel')">
    <label class="project-name-modal__label">
      Nombre
      <input v-model="name" type="text" class="project-name-modal__input" aria-label="Nombre del proyecto" :disabled="busy" @keyup.enter="confirm" />
    </label>
    <label v-if="mode === 'rename'" class="project-name-modal__label">
      Descripción (opcional)
      <textarea v-model="description" class="project-name-modal__textarea" aria-label="Descripción del proyecto" :disabled="busy" rows="3" />
    </label>
    <p v-if="validationError" class="project-name-modal__error">{{ validationError }}</p>
    <p v-if="error" class="project-name-modal__error">{{ error }}</p>
    <template #actions>
      <GButton variant="ghost" :disabled="busy" @click="emit('cancel')">Cancelar</GButton>
      <GButton variant="primary" :disabled="busy" @click="confirm">{{ busy ? 'Guardando…' : mode === 'create' ? 'Crear proyecto' : 'Guardar' }}</GButton>
    </template>
  </AppDialog>
</template>

<style scoped>
.project-name-modal__label {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  font-size: var(--text-sm);
  color: var(--muted);
}

.project-name-modal__input {
  min-height: var(--hit-target-min);
  padding: 0 var(--space-3);
  background: var(--surface);
  border: var(--border-width) solid var(--border);
  border-radius: var(--radius-md);
  color: var(--text);
  font-size: var(--text-base);
}

.project-name-modal__textarea {
  padding: var(--space-2) var(--space-3);
  background: var(--surface);
  border: var(--border-width) solid var(--border);
  border-radius: var(--radius-md);
  color: var(--text);
  font-size: var(--text-base);
  font-family: inherit;
  resize: vertical;
}

.project-name-modal__error {
  margin: 0;
  color: var(--danger);
  font-size: var(--text-sm);
}
</style>
