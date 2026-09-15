<script setup lang="ts">
/**
 * Ícono de marca (ver `IconGoogle.vue`) -- identifica el navegador en cada
 * fila de `SessionsModal.vue` (ticket 095). Valores posibles vienen de
 * `UserAgentParser.browser()` en auth-core-mc: "Chrome"/"Firefox"/
 * "Safari"/"Edge"/"Opera"/"Desconocido" -- este cubre "Chrome".
 */
withDefaults(defineProps<{ size?: number }>(), { size: 18 })
</script>

<template>
  <svg :width="size" :height="size" viewBox="0 0 24 24" aria-hidden="true">
    <path fill="#EA4335" d="M12 12 3.34 7A10 10 0 0 1 20.66 7Z" />
    <path fill="#FBBC05" d="M12 12 20.66 7A10 10 0 0 1 12 22Z" />
    <path fill="#34A853" d="M12 12 12 22A10 10 0 0 1 3.34 17Z" />
    <circle cx="12" cy="12" r="6.5" fill="#fff" />
    <circle cx="12" cy="12" r="4" fill="#4285F4" />
  </svg>
</template>
