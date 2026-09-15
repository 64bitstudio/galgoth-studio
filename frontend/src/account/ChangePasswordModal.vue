<script setup lang="ts">
/**
 * Ticket 095 -- extraído de la fila inline "Cambiar contraseña"/"Establecer
 * contraseña" de `UserView.vue` (093) a un modal aparte, para apegarse al
 * mockup original (decisión confirmada por Marco vía `AskUserQuestion`:
 * "filas que abren un modal/vista aparte"). Mismo patrón que
 * `ChangeEmailModal.vue` -- este componente solo junta el formulario y
 * emite `confirm`, la llamada real a `accountApi.changePassword`/
 * `setPassword` sigue viviendo en `UserView.vue` (`hasPassword` decide
 * cuál de las dos, igual que antes).
 */
import { ref } from 'vue'
import AppDialog from '../design-system/components/AppDialog.vue'
import GButton from '../design-system/components/GButton.vue'

const props = defineProps<{
  hasPassword: boolean
  busy?: boolean
  error?: string | null
}>()

const emit = defineEmits<{ confirm: [{ currentPassword: string; newPassword: string }]; cancel: [] }>()

const currentPassword = ref('')
const newPassword = ref('')
const validationError = ref<string | null>(null)

function confirm(): void {
  if (props.busy) {
    return
  }
  if (props.hasPassword && !currentPassword.value) {
    validationError.value = 'Ingresa tu contraseña actual.'
    return
  }
  if (newPassword.value.length < 8) {
    validationError.value = 'La nueva contraseña debe tener al menos 8 caracteres.'
    return
  }
  validationError.value = null
  emit('confirm', { currentPassword: currentPassword.value, newPassword: newPassword.value })
}
</script>

<template>
  <AppDialog :title="hasPassword ? 'Cambiar contraseña' : 'Establecer contraseña'" @cancel="emit('cancel')">
    <label v-if="hasPassword" class="change-password-modal__label">
      Contraseña actual
      <input v-model="currentPassword" type="password" class="change-password-modal__input" autocomplete="current-password" aria-label="Contraseña actual" :disabled="busy" />
    </label>
    <label class="change-password-modal__label">
      Nueva contraseña
      <input v-model="newPassword" type="password" class="change-password-modal__input" autocomplete="new-password" minlength="8" aria-label="Nueva contraseña" :disabled="busy" @keyup.enter="confirm" />
    </label>
    <p v-if="validationError" class="change-password-modal__error">{{ validationError }}</p>
    <p v-if="error" class="change-password-modal__error">{{ error }}</p>
    <template #actions>
      <GButton variant="ghost" :disabled="busy" @click="emit('cancel')">Cancelar</GButton>
      <GButton variant="primary" :disabled="busy" @click="confirm">{{ busy ? 'Guardando…' : 'Actualizar contraseña' }}</GButton>
    </template>
  </AppDialog>
</template>

<style scoped>
.change-password-modal__label {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  font-size: var(--text-sm);
  color: var(--muted);
}

.change-password-modal__input {
  min-height: var(--hit-target-min);
  padding: 0 var(--space-3);
  background: var(--surface);
  border: var(--border-width) solid var(--border);
  border-radius: var(--radius-md);
  color: var(--text);
  font-size: var(--text-base);
}

.change-password-modal__error {
  margin: 0;
  color: var(--danger);
  font-size: var(--text-sm);
}
</style>
