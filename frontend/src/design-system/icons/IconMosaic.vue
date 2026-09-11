<script setup lang="ts">
/** Ticket 067 -- "Pixel Art" (Generador de textura IA): 4 celdas separadas, evoca un mosaico de píxeles (distinto de IconGrid, que son líneas de cuadrícula continuas). */
import IconBase from './IconBase.vue'
</script>

<template>
  <IconBase>
    <rect x="3" y="3" width="7" height="7" />
    <rect x="14" y="3" width="7" height="7" />
    <rect x="3" y="14" width="7" height="7" />
    <rect x="14" y="14" width="7" height="7" />
  </IconBase>
</template>
