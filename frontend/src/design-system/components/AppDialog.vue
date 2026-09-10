<script setup lang="ts">
/**
 * Diálogo genérico del design system (ticket 039, punto 16) -- shell
 * reutilizable para renombrar/confirmar/eliminar/cualquier otra
 * confirmación, extraído del patrón ya probado en `ProjectNameModal.vue`
 * (ticket 021). Nunca `alert()`/`confirm()`/`prompt()` nativos.
 *
 * `<dialog>` nativo a propósito (mismo hallazgo real de Sonar S6819que
 * `ProjectNameModal.vue`/`GMenu.vue`: usar el elemento nativo en vez de
 * emular con `role="dialog"`) -- gratis trae manejo de foco/tab-trap del
 * navegador y cierre con Escape (evento `cancel` nativo).
 */
import { onMounted, ref } from 'vue'

defineProps<{
  title: string
  ariaLabel?: string
}>()

const emit = defineEmits<{ cancel: [] }>()

const dialogEl = ref<HTMLDialogElement>()

defineExpose({ dialogEl })

onMounted(() => {
  dialogEl.value?.showModal()
})

/** Clic en el `::backdrop` nativo aterriza en el propio `<dialog>` (nunca en un hijo) -- mismo patrón que `ProjectNameModal.vue`. */
function handleBackdropClick(event: MouseEvent): void {
  if (event.target === dialogEl.value) {
    emit('cancel')
  }
}
</script>

<template>
  <dialog ref="dialogEl" class="app-dialog" :aria-label="ariaLabel ?? title" @click="handleBackdropClick" @cancel.prevent="emit('cancel')">
    <h2 class="app-dialog__title">{{ title }}</h2>
    <div class="app-dialog__body app-scroll">
      <slot />
    </div>
    <div class="app-dialog__actions">
      <slot name="actions" />
    </div>
  </dialog>
</template>

<style scoped>
.app-dialog {
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

.app-dialog::backdrop {
  background: rgba(0, 0, 0, 0.6);
}

.app-dialog__title {
  margin: 0;
  font-size: var(--text-lg);
}

.app-dialog__body {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  max-height: 60vh;
  overflow-y: auto;
}

.app-dialog__actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--space-2);
}
</style>
