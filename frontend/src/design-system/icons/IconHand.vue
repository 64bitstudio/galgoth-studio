<script setup lang="ts">
/** Ticket 068 -- mano abierta, herramienta de "mover el lienzo" (pan) con un click, en vez de depender únicamente de la barra espaciadora + arrastre. */
import IconBase from './IconBase.vue'
</script>

<template>
  <IconBase>
    <path d="M8 13V5a1.5 1.5 0 013 0v6" />
    <path d="M11 11V4a1.5 1.5 0 013 0v7" />
    <path d="M14 11.5V6a1.5 1.5 0 013 0v8" />
    <path d="M17 13v-1.5a1.5 1.5 0 013 0V16a6 6 0 01-6 6h-2a7 7 0 01-7-7v-2a1.5 1.5 0 013 0V13" />
  </IconBase>
</template>
