<script setup lang="ts">
/**
 * Envoltorio común para los íconos de línea del sistema — trazo simple,
 * 1.5px, sin relleno, con guiños de voxel/Minecraft donde aplica
 * (ver los íconos concretos). `aria-hidden` por defecto porque estos
 * íconos siempre acompañan una etiqueta de texto visible (nunca la
 * reemplazan — regla de accesibilidad del Visual Contract).
 */
withDefaults(
  defineProps<{
    size?: number
  }>(),
  { size: 18 },
)
</script>

<template>
  <svg
    :width="size"
    :height="size"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    stroke-width="1.5"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
  >
    <slot />
  </svg>
</template>
