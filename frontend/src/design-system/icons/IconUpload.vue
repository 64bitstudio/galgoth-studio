<script setup lang="ts">
/** Corrección visual post-058 -- flecha hacia una bandeja (Importar PNG en la toolbar de textura), fidelidad al mockup 07 v2 (línea 340 de `docs/definiciones/mockups/058-texture-editor-redesign-reference.html`). */
import IconBase from './IconBase.vue'
</script>

<template>
  <IconBase>
    <path d="M12 16V4M12 4l-4 4M12 4l4 4" />
    <path d="M4 16v3a1 1 0 001 1h14a1 1 0 001-1v-3" />
  </IconBase>
</template>
