<script setup lang="ts">
/**
 * Drawer lateral genérico del design system (ticket 069) -- shell
 * reutilizable para el Asistente IA, compartido entre las tabs Modelo y
 * Textura de `MobEditor.vue` en vez de un panel embebido + una pantalla
 * de ruta separada (ver header de ese archivo). Mismo criterio de
 * `<dialog>` nativo que `AppDialog.vue` (foco/tab-trap/Escape gratis del
 * navegador, mismo hallazgo real de Sonar S6819 ya documentado ahí) --
 * acá además se ancla al borde derecho a todo lo alto en vez de
 * centrarse, y desliza al abrir/cerrar. Mockup con VoBo del PO:
 * https://claude.ai/code/artifact/a3514398-4fc4-430f-96b2-40299c0038a4
 *
 * El cierre (Escape/backdrop/botón ×) anima la salida ANTES de emitir
 * `cancel` -- el padre desmonta este componente recién ahí (mismo patrón
 * `v-if` que `AppDialog`), para no cortar en seco la animación de
 * salida. Se resuelve con un `setTimeout` (`CLOSE_ANIMATION_MS`, mismo
 * valor que la duración de la transición CSS de abajo -- si se cambia
 * uno, hay que cambiar el otro) en vez de escuchar `transitionend`: ese
 * evento no es confiable en todos los casos (interrupciones,
 * `prefers-reduced-motion`, jsdom en tests) y un timeout fijo es el
 * patrón estándar para esto.
 */
import { onMounted, ref } from 'vue'
import IconButton from './IconButton.vue'

defineProps<{
  title: string
  ariaLabel?: string
}>()

const emit = defineEmits<{ cancel: [] }>()

const CLOSE_ANIMATION_MS = 240

const dialogEl = ref<HTMLDialogElement>()
const open = ref(false)
const closing = ref(false)

defineExpose({ dialogEl })

onMounted(() => {
  dialogEl.value?.showModal()
  requestAnimationFrame(() => {
    open.value = true
  })
})

function prefersReducedMotion(): boolean {
  try {
    return window.matchMedia('(prefers-reduced-motion: reduce)').matches
  } catch {
    return false // jsdom (tests) u otro entorno sin matchMedia real -- nunca debe romper el cierre.
  }
}

function requestClose(): void {
  if (closing.value) {
    return
  }
  closing.value = true
  open.value = false
  window.setTimeout(() => emit('cancel'), prefersReducedMotion() ? 0 : CLOSE_ANIMATION_MS)
}

/** Clic en el `::backdrop` nativo aterriza en el propio `<dialog>` (nunca en un hijo) -- mismo patrón que `AppDialog.vue`. */
function handleBackdropClick(event: MouseEvent): void {
  if (event.target === dialogEl.value) {
    requestClose()
  }
}
</script>

<template>
  <dialog
    ref="dialogEl"
    class="g-drawer"
    :class="{ 'g-drawer--open': open }"
    :aria-label="ariaLabel ?? title"
    @click="handleBackdropClick"
    @cancel.prevent="requestClose"
  >
    <div class="g-drawer__header">
      <h2 class="g-drawer__title"><slot name="title-icon" />{{ title }}</h2>
      <IconButton label="Cerrar" @click="requestClose">×</IconButton>
    </div>
    <div class="g-drawer__body app-scroll">
      <slot />
    </div>
    <div v-if="$slots.footer" class="g-drawer__footer">
      <slot name="footer" />
    </div>
  </dialog>
</template>

<style scoped>
.g-drawer {
  position: fixed;
  inset: 0 0 0 auto;
  margin: 0;
  width: min(420px, 92vw);
  max-width: none;
  height: 100%;
  max-height: 100vh;
  background: var(--panel);
  border: none;
  border-left: var(--border-width) solid var(--border);
  box-shadow: var(--shadow-md);
  padding: 0;
  display: flex;
  flex-direction: column;
  color: var(--text);
  transform: translateX(100%);
  transition: transform 240ms cubic-bezier(0.16, 1, 0.3, 1);
}

.g-drawer--open {
  transform: translateX(0);
}

.g-drawer::backdrop {
  background: rgba(0, 0, 0, 0.6);
  opacity: 0;
  transition: opacity 240ms ease;
}

.g-drawer--open::backdrop {
  opacity: 1;
}

@media (prefers-reduced-motion: reduce) {
  .g-drawer,
  .g-drawer::backdrop {
    transition: none;
  }
}

.g-drawer__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  padding: var(--space-4) var(--space-4) var(--space-3);
  border-bottom: var(--border-width) solid var(--border);
  flex-shrink: 0;
}

.g-drawer__title {
  margin: 0;
  display: flex;
  align-items: center;
  gap: var(--space-2);
  color: var(--accent);
  font-size: var(--text-lg);
}

.g-drawer__body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: var(--space-4);
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.g-drawer__footer {
  flex-shrink: 0;
  padding: var(--space-3) var(--space-4) var(--space-4);
  border-top: var(--border-width) solid var(--border);
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}
</style>
