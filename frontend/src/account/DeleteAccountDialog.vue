<script setup lang="ts">
/**
 * Ticket 093 -- confirmación explícita antes de una acción irreversible
 * (ticket 064 de auth-core-mc): exige reescribir el propio correo/
 * teléfono, no solo un clic en "Confirmar" (`ConfirmDialog.vue` no
 * alcanza para esto). El backend vuelve a validar esa misma coincidencia
 * -- este chequeo del lado del cliente es solo para no gastar un
 * roundtrip en el caso obvio de un typo.
 */
import { ref } from 'vue'
import AppDialog from '../design-system/components/AppDialog.vue'
import GButton from '../design-system/components/GButton.vue'

const props = defineProps<{
  identifier: string
  busy?: boolean
  error?: string | null
}>()

const emit = defineEmits<{ confirm: []; cancel: [] }>()

const typed = ref('')

function confirm(): void {
  if (props.busy || typed.value.trim() !== props.identifier) {
    return
  }
  emit('confirm')
}
</script>

<template>
  <AppDialog title="Eliminar cuenta" @cancel="emit('cancel')">
    <p class="delete-account-dialog__warning">
      Esta acción es <strong>irreversible</strong>. Se eliminarán todos tus proyectos de Galgoth Studio (públicos y privados) y no podrás volver a iniciar sesión con esta cuenta.
    </p>
    <label class="delete-account-dialog__label">
      Escribe <strong>{{ identifier }}</strong> para confirmar
      <input v-model="typed" type="text" class="delete-account-dialog__input" aria-label="Confirmar correo o teléfono" :disabled="busy" @keyup.enter="confirm" />
    </label>
    <p v-if="error" class="delete-account-dialog__error">{{ error }}</p>
    <template #actions>
      <GButton variant="ghost" :disabled="busy" @click="emit('cancel')">Cancelar</GButton>
      <GButton variant="danger" :disabled="busy || typed.trim() !== identifier" @click="confirm">{{ busy ? 'Eliminando…' : 'Eliminar mi cuenta' }}</GButton>
    </template>
  </AppDialog>
</template>

<style scoped>
.delete-account-dialog__warning {
  margin: 0;
  color: var(--text);
  font-size: var(--text-sm);
}

.delete-account-dialog__label {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  font-size: var(--text-sm);
  color: var(--muted);
}

.delete-account-dialog__input {
  min-height: var(--hit-target-min);
  padding: 0 var(--space-3);
  background: var(--surface);
  border: var(--border-width) solid var(--border);
  border-radius: var(--radius-md);
  color: var(--text);
  font-size: var(--text-base);
}

.delete-account-dialog__error {
  margin: 0;
  color: var(--danger);
  font-size: var(--text-sm);
}
</style>
