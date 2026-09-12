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
 *
 * Ticket 071 (hallazgo real reportado en vivo: los diálogos abrían/
 * cerraban sin ninguna transición) -- las clases de transición viven en
 * un `<style>` SIN `scoped` a propósito: quien usa este componente debe
 * envolver su `v-if`/`v-else` en `<Transition name="app-dialog">` (nunca
 * dentro de este archivo, que no controla el `v-if` real -- vive en cada
 * pantalla que decide cuándo montar/desmontar el diálogo concreto). Un
 * nombre de transición SOLO funciona como clases CSS planas, no scoped
 * -- de ahí el bloque de estilo aparte.
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
  opacity: 1;
}

.app-dialog__title {
  margin: 0;
  font-size: var(--text-lg);
}

/* Ticket 071 (hallazgo real reportado en vivo): un hijo enfocado (`--focus-ring`,
   `box-shadow` que se extiende 6px hacia afuera) quedaba recortado en los
   bordes -- `overflow-y: auto` fuerza a los navegadores a computar
   `overflow-x` también como scroll container (no `visible`), y este
   `<div>` no tenía padding propio: sus hijos tocaban el borde de recorte
   exacto, sin margen para que nada se extendiera hacia afuera. `padding`
   le da a esos 6px un lugar donde existir; el `margin` negativo a
   juego cancela el desplazamiento visual (mismo ancho/alto percibido
   que antes). */
.app-dialog__body {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  max-height: 60vh;
  overflow-y: auto;
  padding: 6px;
  margin: -6px;
}

.app-dialog__actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--space-2);
}
</style>

<!--
  Ticket 071 -- SIN `scoped`: `<Transition name="app-dialog">` en el
  caller aplica estas clases como texto plano al elemento `<dialog>` real
  (`.app-dialog`), fuera del árbol de este componente -- un `<style
  scoped>` de acá no las alcanzaría de forma confiable. Contrato del
  design system: cualquier pantalla que monte/desmonte un diálogo basado
  en `AppDialog` (ConfirmDialog/ProjectNameModal/MobRenameDialog/
  AiMobProjectPickerDialog/AddMobModal) debe envolver su `v-if` en
  `<Transition name="app-dialog">` para heredar esta animación gratis.
-->
<style>
.app-dialog-enter-active,
.app-dialog-leave-active {
  transition:
    opacity 200ms cubic-bezier(0.16, 1, 0.3, 1),
    transform 200ms cubic-bezier(0.16, 1, 0.3, 1);
}

.app-dialog-enter-active::backdrop,
.app-dialog-leave-active::backdrop {
  transition: opacity 200ms cubic-bezier(0.16, 1, 0.3, 1);
}

/* Sin `scale()` a propósito: escalar un elemento con `border`/`border-radius`
   puede dejar una costura de anti-aliasing visible en las esquinas mientras
   la transformación está en curso (hallazgo real, reportado en vivo) --
   `translateY` sola no distorsiona la geometría del borde. */
.app-dialog-enter-from,
.app-dialog-leave-to {
  opacity: 0;
  transform: translateY(8px);
}

.app-dialog-enter-from::backdrop,
.app-dialog-leave-to::backdrop {
  opacity: 0;
}

@media (prefers-reduced-motion: reduce) {
  .app-dialog-enter-active,
  .app-dialog-leave-active,
  .app-dialog-enter-active::backdrop,
  .app-dialog-leave-active::backdrop {
    transition: none;
  }
}
</style>
