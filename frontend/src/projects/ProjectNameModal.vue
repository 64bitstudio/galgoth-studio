<script setup lang="ts">
/**
 * Modal de nombre de proyecto (ticket 021) -- se reutiliza para "Nuevo
 * proyecto" (HU-01) y "Rename" (HU-02, menú de acciones de la tarjeta):
 * ambos flujos son idénticos salvo el título/valor inicial/verbo del
 * botón de confirmar. HU-01 AC #3: solo se pide nombre, sin campos
 * técnicos de IA/geometría -- por diseño, este modal no tiene ningún
 * otro campo.
 *
 * `<dialog>` nativo (hallazgo real de Sonar, S6819: usar el elemento
 * nativo en vez de emular con `role="dialog"`) -- gratis trae manejo de
 * foco/tab-trap del navegador y cierre con Escape, que la versión
 * anterior (un `<div>` con overlay a mano) no tenía.
 */
import { onMounted, ref, watch } from 'vue'
import GButton from '../design-system/components/GButton.vue'

const props = defineProps<{
  mode: 'create' | 'rename'
  initialName?: string
}>()

const emit = defineEmits<{ confirm: [string]; cancel: [] }>()

const dialogEl = ref<HTMLDialogElement>()
const name = ref(props.initialName ?? '')
const validationError = ref<string | null>(null)

watch(
  () => props.initialName,
  (value) => {
    name.value = value ?? ''
  },
)

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
  emit('confirm', trimmed)
}

/** Clic en el `::backdrop` nativo aterriza en el propio `<dialog>` (nunca en un hijo) -- mismo patrón que un backdrop a mano, sin necesitar un div extra. */
function handleBackdropClick(event: MouseEvent): void {
  if (event.target === dialogEl.value) {
    emit('cancel')
  }
}
</script>

<template>
  <dialog
    ref="dialogEl"
    class="project-name-modal"
    :aria-label="mode === 'create' ? 'Nuevo proyecto' : 'Renombrar proyecto'"
    @click="handleBackdropClick"
    @cancel.prevent="emit('cancel')"
  >
    <h2 class="project-name-modal__title">{{ mode === 'create' ? 'Nuevo proyecto' : 'Renombrar proyecto' }}</h2>
    <label class="project-name-modal__label">
      Nombre
      <input v-model="name" type="text" class="project-name-modal__input" aria-label="Nombre del proyecto" @keyup.enter="confirm" />
    </label>
    <p v-if="validationError" class="project-name-modal__error">{{ validationError }}</p>
    <div class="project-name-modal__actions">
      <GButton variant="ghost" @click="emit('cancel')">Cancelar</GButton>
      <GButton variant="primary" @click="confirm">{{ mode === 'create' ? 'Crear proyecto' : 'Guardar' }}</GButton>
    </div>
  </dialog>
</template>

<style scoped>
.project-name-modal {
  background: var(--panel);
  border: var(--border-width) solid var(--border);
  border-radius: var(--radius-lg);
  padding: var(--space-6);
  width: min(360px, 90vw);
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
  color: var(--text);
}

.project-name-modal::backdrop {
  background: rgba(0, 0, 0, 0.6);
}

.project-name-modal__title {
  margin: 0;
  font-size: var(--text-lg);
}

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

.project-name-modal__error {
  margin: 0;
  color: var(--danger);
  font-size: var(--text-sm);
}

.project-name-modal__actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--space-2);
}
</style>
