<script setup lang="ts">
/** Ver docstring de `IconBrowserChrome.vue` -- cubre "Opera". */
withDefaults(defineProps<{ size?: number }>(), { size: 18 })
</script>

<template>
  <svg :width="size" :height="size" viewBox="0 0 24 24" aria-hidden="true">
    <circle cx="12" cy="12" r="10" fill="#FF1B2D" />
    <ellipse cx="12" cy="12" rx="4.2" ry="6.5" fill="#fff" />
    <ellipse cx="12" cy="12" rx="2.6" ry="4.8" fill="#FF1B2D" />
  </svg>
</template>
