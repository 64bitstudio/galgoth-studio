<script setup lang="ts">
/** Ver docstring de `IconBrowserChrome.vue` -- cubre "Safari". */
withDefaults(defineProps<{ size?: number }>(), { size: 18 })
</script>

<template>
  <svg :width="size" :height="size" viewBox="0 0 24 24" aria-hidden="true">
    <circle cx="12" cy="12" r="10" fill="#12B0F0" />
    <path fill="#F4511E" d="M12 12 16.5 7.5 13.6 13.6Z" />
    <path fill="#fff" d="M12 12 7.5 16.5 10.4 10.4Z" />
  </svg>
</template>
