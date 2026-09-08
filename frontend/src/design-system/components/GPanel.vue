<script setup lang="ts">
/**
 * Superficie base (panel/surface). Visual Contract punto 5: evitar
 * cards anidadas innecesarias -- este componente es deliberadamente
 * austero (un borde, sin sombra por defecto) para que anidar dos capas
 * de GPanel sea visualmente obvio y así se piense dos veces antes de
 * hacerlo. Úsalo solo por nivel real de agrupación, no por reflejo.
 */
withDefaults(
  defineProps<{
    surface?: 'panel' | 'surface' | 'surface-2'
    elevated?: boolean
  }>(),
  { surface: 'panel', elevated: false },
)
</script>

<template>
  <div
    class="g-panel"
    :class="[`g-panel--${surface}`, { 'g-panel--elevated': elevated }]"
  >
    <slot />
  </div>
</template>

<style scoped>
.g-panel {
  border: var(--border-width) solid var(--border);
  border-radius: var(--radius-lg);
  padding: var(--space-4);
}

.g-panel--panel {
  background: var(--panel);
}
.g-panel--surface {
  background: var(--surface);
}
.g-panel--surface-2 {
  background: var(--surface-2);
}

.g-panel--elevated {
  box-shadow: var(--shadow-md);
}
</style>
