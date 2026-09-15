<script setup lang="ts">
/**
 * Logo de marca (a color) -- a diferencia del resto de `icons/` (línea
 * simple, `currentColor`, envueltos en `IconBase.vue`), un ícono de marca
 * de terceros usa sus colores reales para seguir siendo reconocible, no
 * el color de texto del tema. Mismo path que el botón "Google" de
 * `LoginView.vue`/`RegisterView.vue` -- reutilizado tal cual acá para
 * "Cuentas conectadas" (ticket 095) en vez de duplicarlo con una versión
 * ligeramente distinta.
 */
withDefaults(defineProps<{ size?: number }>(), { size: 18 })
</script>

<template>
  <svg :width="size" :height="size" viewBox="0 0 24 24" aria-hidden="true">
    <path fill="#EA4335" d="M12 10.2v3.9h5.5c-.24 1.4-1.7 4.1-5.5 4.1-3.3 0-6-2.7-6-6.1s2.7-6.1 6-6.1c1.9 0 3.1.8 3.9 1.5l2.6-2.5C16.8 3.3 14.6 2.3 12 2.3 6.9 2.3 2.7 6.5 2.7 11.6S6.9 21 12 21c6.9 0 8.9-4.9 8.9-7.4 0-.5-.1-.9-.1-1.3H12Z" />
  </svg>
</template>
