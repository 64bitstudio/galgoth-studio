<script setup lang="ts">
/** Ver docstring de `IconBrowserChrome.vue` -- cubre "Firefox". */
withDefaults(defineProps<{ size?: number }>(), { size: 18 })
</script>

<template>
  <svg :width="size" :height="size" viewBox="0 0 24 24" aria-hidden="true">
    <circle cx="12" cy="12" r="10" fill="#FF9500" />
    <path fill="#D63A0F" d="M12 4.5c3.5 1 6.5 4 6.5 8a6.5 6.5 0 0 1-11.8 3.7c2.7.8 6.4-.3 6.9-3.4.4-2.4-1-4.1-1.6-6.3-.3-1-.2-1.5 0-2Z" />
  </svg>
</template>
