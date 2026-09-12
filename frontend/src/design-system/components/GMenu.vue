<script setup lang="ts">
/**
 * Menú de acciones desplegable (ticket 021, primer uso: acciones de la
 * tarjeta de proyecto -- Rename/Duplicate/Export/Delete, HU-02). Un ítem
 * deshabilitado muestra su razón como texto visible bajo la etiqueta,
 * nunca solo un tooltip -- mismo criterio de accesibilidad ya establecido
 * en GTabs (ticket 002: "el motivo de 'próximamente' es texto visible,
 * no solo un tooltip").
 *
 * Sin `<ul>`/`<li>` a propósito (hallazgo real de Sonar, S6819): el
 * patrón ARIA `role="menu"` con hijos envueltos en `<li role="presentation">`
 * dispara la misma regla que prohíbe emular roles nativos con ARIA --
 * los botones de cada ítem son hijos DIRECTOS del contenedor `role="menu"`,
 * sin ningún wrapper que necesite neutralizar su semántica.
 *
 * Ticket 071 (hallazgos reales reportados en vivo sobre Inicio, pero el
 * componente es compartido por toda la app):
 * 1. Nunca se cerraba al hacer click afuera ni con Escape -- mismo patrón
 *    `rootRef` + `handleDocumentClick` ya establecido por `GSelect.vue`
 *    (ticket 058).
 * 2. Sin transición de entrada/salida, y sin detección de espacio --
 *    en una card cerca del borde inferior de la ventana, la lista podía
 *    desbordar el viewport hacia abajo. `rendered` (existe en el DOM,
 *    incluye mientras anima la salida) se separa de `isOpen` (estado
 *    visual + intención lógica de abierto/cerrado) -- mismo patrón de
 *    "animar la salida antes de desmontar" que `GDrawer.vue` (ticket 069),
 *    con `computePlacement()` decidiendo arriba/abajo según el espacio
 *    real disponible (`getBoundingClientRect`) justo antes de mostrarse.
 *
 * Post-076 (pedido explícito del PO -- "agregale iconos a esas opciones",
 * sobre el menú "⋮" del editor de mob): `icon` opcional por ítem, un
 * componente de ícono cualquiera del design system (mismo patrón laxo
 * `unknown` + `<component :is>` ya usado en `ConfigurationStep.vue` para
 * los botones de tipo de entidad) -- backward-compatible, ningún
 * consumidor existente que no lo pase (`ProjectCard.vue`/`MobCard.vue`/
 * `ProjectDetail.vue`/`ProjectsDashboard.vue`/`HomeView.vue`) cambia
 * visualmente.
 */
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'

export interface GMenuItem {
  key: string
  label: string
  icon?: unknown
  disabled?: boolean
  disabledReason?: string
  danger?: boolean
}

defineProps<{
  items: GMenuItem[]
  label: string
}>()

const emit = defineEmits<{ select: [string] }>()

/** Mismo valor que la duración de la transición CSS de `.g-menu__list` -- si se cambia uno, hay que cambiar el otro (igual que `GDrawer.vue`). */
const CLOSE_ANIMATION_MS = 140

const rootRef = ref<HTMLElement>()
const listRef = ref<HTMLElement>()
const rendered = ref(false)
const isOpen = ref(false)
const openUpward = ref(false)
let closeTimeoutId: ReturnType<typeof setTimeout> | undefined

function prefersReducedMotion(): boolean {
  try {
    return window.matchMedia('(prefers-reduced-motion: reduce)').matches
  } catch {
    return false // jsdom (tests) u otro entorno sin matchMedia real -- nunca debe romper el cierre.
  }
}

/** Decide arriba/abajo según el espacio real contra el viewport -- nunca debe desbordar la ventana. */
function computePlacement(): void {
  const trigger = rootRef.value?.querySelector<HTMLButtonElement>('.g-menu__trigger')
  if (!trigger || !listRef.value) {
    return
  }
  const triggerRect = trigger.getBoundingClientRect()
  const listHeight = listRef.value.getBoundingClientRect().height
  const spaceBelow = window.innerHeight - triggerRect.bottom
  const spaceAbove = triggerRect.top
  openUpward.value = spaceBelow < listHeight && spaceAbove > spaceBelow
}

function open(): void {
  if (closeTimeoutId !== undefined) {
    clearTimeout(closeTimeoutId)
    closeTimeoutId = undefined
  }
  rendered.value = true
  nextTick(() => {
    computePlacement()
    requestAnimationFrame(() => {
      isOpen.value = true
    })
  })
}

function close(): void {
  if (!rendered.value) {
    return
  }
  isOpen.value = false
  closeTimeoutId = setTimeout(
    () => {
      rendered.value = false
      closeTimeoutId = undefined
    },
    prefersReducedMotion() ? 0 : CLOSE_ANIMATION_MS,
  )
}

function toggle(): void {
  if (isOpen.value) {
    close()
  } else {
    open()
  }
}

function select(item: GMenuItem): void {
  if (item.disabled) {
    return
  }
  emit('select', item.key)
  close()
}

function handleDocumentClick(event: MouseEvent): void {
  if (rootRef.value && !rootRef.value.contains(event.target as Node)) {
    close()
  }
}

function handleKeydown(event: KeyboardEvent): void {
  // `rendered` (no `isOpen`): debe poder cerrarse con Escape apenas se abre, sin esperar a que
  // la transición de entrada (requestAnimationFrame) termine de pintar.
  if (event.key === 'Escape' && rendered.value) {
    close()
    rootRef.value?.querySelector<HTMLButtonElement>('.g-menu__trigger')?.focus()
  }
}

onMounted(() => {
  document.addEventListener('click', handleDocumentClick)
  document.addEventListener('keydown', handleKeydown)
})
onBeforeUnmount(() => {
  document.removeEventListener('click', handleDocumentClick)
  document.removeEventListener('keydown', handleKeydown)
  if (closeTimeoutId !== undefined) {
    clearTimeout(closeTimeoutId)
  }
})
</script>

<template>
  <div ref="rootRef" class="g-menu">
    <button type="button" class="g-menu__trigger" :aria-label="label" aria-haspopup="true" :aria-expanded="isOpen" @click="toggle">
      ⋮
    </button>
    <div
      v-if="rendered"
      ref="listRef"
      class="g-menu__list"
      :class="{ 'g-menu__list--open': isOpen, 'g-menu__list--above': openUpward, 'g-menu__list--below': !openUpward }"
      role="menu"
    >
      <button
        v-for="item in items"
        :key="item.key"
        type="button"
        role="menuitem"
        class="g-menu__item"
        :class="{ 'g-menu__item--danger': item.danger, 'g-menu__item--disabled': item.disabled }"
        :disabled="item.disabled"
        @click="select(item)"
      >
        <span class="g-menu__item-row">
          <component :is="item.icon" v-if="item.icon" :size="16" aria-hidden="true" />
          <span>{{ item.label }}</span>
        </span>
        <span v-if="item.disabled && item.disabledReason" class="g-menu__hint">{{ item.disabledReason }}</span>
      </button>
    </div>
  </div>
</template>

<style scoped>
.g-menu {
  position: relative;
  display: inline-block;
}

.g-menu__trigger {
  min-width: var(--hit-target-min);
  min-height: var(--hit-target-min);
  border: var(--border-width) solid transparent;
  background: transparent;
  color: var(--text);
  border-radius: var(--radius-md);
  cursor: pointer;
  font-size: var(--text-lg);
  line-height: 1;
}

.g-menu__trigger:hover {
  background: var(--surface-2);
}

.g-menu__list {
  position: absolute;
  right: 0;
  z-index: 10;
  display: flex;
  flex-direction: column;
  margin: 0;
  padding: var(--space-1);
  background: var(--surface);
  border: var(--border-width) solid var(--border);
  border-radius: var(--radius-md);
  min-width: 160px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.4);
  opacity: 0;
  pointer-events: none;
  transition:
    opacity var(--transition-fast),
    transform var(--transition-fast);
}

.g-menu__list--below {
  top: calc(100% + var(--space-1));
  transform-origin: top right;
  transform: translateY(-4px) scale(0.98);
}

.g-menu__list--above {
  bottom: calc(100% + var(--space-1));
  transform-origin: bottom right;
  transform: translateY(4px) scale(0.98);
}

.g-menu__list--open {
  opacity: 1;
  pointer-events: auto;
  transform: translateY(0) scale(1);
}

@media (prefers-reduced-motion: reduce) {
  .g-menu__list {
    transition: none;
  }
}

.g-menu__item {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  width: 100%;
  min-height: var(--hit-target-min);
  padding: var(--space-2) var(--space-3);
  background: transparent;
  border: none;
  border-radius: var(--radius-sm);
  color: var(--text);
  text-align: left;
  cursor: pointer;
  font-size: var(--text-sm);
}

.g-menu__item-row {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.g-menu__item-row svg {
  flex-shrink: 0;
  color: var(--muted);
}

.g-menu__item--danger .g-menu__item-row svg {
  color: var(--danger);
}

.g-menu__item:hover:not(:disabled) {
  background: var(--surface-2);
}

.g-menu__item--danger {
  color: var(--danger);
}

.g-menu__item--disabled {
  color: var(--muted);
  cursor: not-allowed;
}

.g-menu__hint {
  font-size: var(--text-xs);
  color: var(--muted);
}
</style>
