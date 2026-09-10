<script setup lang="ts">
/**
 * Confirmación destructiva reutilizable sobre `AppDialog` (ticket 039,
 * puntos 3/14/16) -- Eliminar proyecto/Eliminar mob comparten este
 * mismo componente. `busy` deshabilita ambos botones (bloquea doble
 * submit real, no solo visual) mientras el caller espera la respuesta
 * del backend; `error` muestra el fallo real sin cerrar el diálogo, para
 * que el usuario pueda reintentar sin perder el contexto.
 */
import AppDialog from './AppDialog.vue'
import GButton from './GButton.vue'

withDefaults(
  defineProps<{
    title: string
    message: string
    confirmLabel?: string
    danger?: boolean
    busy?: boolean
    error?: string | null
  }>(),
  { confirmLabel: 'Confirmar', danger: false, busy: false, error: null },
)

const emit = defineEmits<{ confirm: []; cancel: [] }>()

function cancel(): void {
  emit('cancel')
}
</script>

<template>
  <AppDialog :title="title" @cancel="cancel">
    <p class="confirm-dialog__message">{{ message }}</p>
    <p v-if="error" class="confirm-dialog__error">{{ error }}</p>
    <template #actions>
      <GButton variant="ghost" :disabled="busy" @click="cancel">Cancelar</GButton>
      <GButton :variant="danger ? 'danger' : 'primary'" :disabled="busy" @click="emit('confirm')">
        {{ busy ? 'Procesando…' : confirmLabel }}
      </GButton>
    </template>
  </AppDialog>
</template>

<style scoped>
.confirm-dialog__message {
  margin: 0;
  color: var(--text);
}

.confirm-dialog__error {
  margin: 0;
  color: var(--danger);
  font-size: var(--text-sm);
}
</style>
