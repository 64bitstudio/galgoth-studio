<script setup lang="ts">
/** Ver docstring de `IconBrowserChrome.vue` -- cubre "Edge". */
withDefaults(defineProps<{ size?: number }>(), { size: 18 })
</script>

<template>
  <svg :width="size" :height="size" viewBox="0 0 24 24" aria-hidden="true">
    <circle cx="12" cy="12" r="10" fill="#0078D4" />
    <path fill="#00B7C3" d="M4 13.5c.5 4 4.3 6.8 8.3 6.4 3-.3 5.2-2 6.2-4.3-1.6 1.2-3.8 1.7-5.9 1-3-1-4.8-4.1-4.2-7.3-2.6 1-4.7 2.3-4.4 4.2Z" />
  </svg>
</template>
