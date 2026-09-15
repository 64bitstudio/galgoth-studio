<script setup lang="ts">
/**
 * Ticket 093 -- pide el correo nuevo y dispara el flujo de 2 pasos ya
 * construido (`EmailChangeController` de auth-core-mc): esta pantalla
 * solo manda la solicitud (`POST /change-email/request`), el cambio
 * real ocurre cuando el usuario abre el link que llega a esa bandeja
 * nueva (`EmailChangeConfirmView.vue`). Nunca cambia el correo actual
 * de inmediato -- mismo patrón de modal que `ProjectNameModal.vue`.
 */
import { ref } from 'vue'
import AppDialog from '../design-system/components/AppDialog.vue'
import GButton from '../design-system/components/GButton.vue'

const props = defineProps<{
  busy?: boolean
  error?: string | null
}>()

const emit = defineEmits<{ confirm: [string]; cancel: [] }>()

const newEmail = ref('')
const validationError = ref<string | null>(null)

function confirm(): void {
  if (props.busy) {
    return
  }
  const trimmed = newEmail.value.trim()
  if (!trimmed || !trimmed.includes('@')) {
    validationError.value = 'Ingresa un correo válido.'
    return
  }
  validationError.value = null
  emit('confirm', trimmed)
}
</script>

<template>
  <AppDialog title="Cambiar correo" @cancel="emit('cancel')">
    <label class="change-email-modal__label">
      Correo nuevo
      <input v-model="newEmail" type="email" class="change-email-modal__input" aria-label="Correo nuevo" :disabled="busy" @keyup.enter="confirm" />
    </label>
    <p class="change-email-modal__hint">Te mandaremos un link de confirmación a esa dirección. Tu correo actual sigue activo hasta que lo confirmes.</p>
    <p v-if="validationError" class="change-email-modal__error">{{ validationError }}</p>
    <p v-if="error" class="change-email-modal__error">{{ error }}</p>
    <template #actions>
      <GButton variant="ghost" :disabled="busy" @click="emit('cancel')">Cancelar</GButton>
      <GButton variant="primary" :disabled="busy" @click="confirm">{{ busy ? 'Enviando…' : 'Enviar link de confirmación' }}</GButton>
    </template>
  </AppDialog>
</template>

<style scoped>
.change-email-modal__label {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  font-size: var(--text-sm);
  color: var(--muted);
}

.change-email-modal__input {
  min-height: var(--hit-target-min);
  padding: 0 var(--space-3);
  background: var(--surface);
  border: var(--border-width) solid var(--border);
  border-radius: var(--radius-md);
  color: var(--text);
  font-size: var(--text-base);
}

.change-email-modal__hint {
  margin: 0;
  color: var(--muted);
  font-size: var(--text-xs);
}

.change-email-modal__error {
  margin: 0;
  color: var(--danger);
  font-size: var(--text-sm);
}
</style>
