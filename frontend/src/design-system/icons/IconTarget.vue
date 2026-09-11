<script setup lang="ts">
/** Ticket 067 -- "Fiel a la referencia" (Generador de textura IA): blanco/target, evoca precisión respecto a la imagen de referencia. */
import IconBase from './IconBase.vue'
</script>

<template>
  <IconBase>
    <circle cx="12" cy="12" r="8" />
    <circle cx="12" cy="12" r="3" />
    <path d="M12 2v3M12 19v3M2 12h3M19 12h3" />
  </IconBase>
</template>
