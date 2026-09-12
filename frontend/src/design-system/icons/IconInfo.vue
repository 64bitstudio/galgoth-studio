<script setup lang="ts">
// Rediseño del wizard "Crear con IA" -- ícono de información (línea "al elegir
// una imagen válida..." de ReferenceStep.vue y el recuadro "Tip" del panel de
// consejos), mismo patrón IconBase.vue que el resto del design system.
import IconBase from './IconBase.vue'
</script>

<template>
  <IconBase>
    <circle cx="12" cy="12" r="9" />
    <path d="M12 8h.01" />
    <path d="M11 12h1v4h1" />
  </IconBase>
</template>
