<script setup lang="ts">
// Ticket 073 -- ícono de tipo de mob "Cuadrúpedo" (huella de pata: almohadilla
// central + 4 dedos), usado en MobCard.vue y el filtro "Tipo".
import IconBase from './IconBase.vue'
</script>

<template>
  <IconBase>
    <ellipse cx="12" cy="15" rx="4.5" ry="3.5" fill="currentColor" stroke="none" />
    <circle cx="6.5" cy="9" r="1.6" fill="currentColor" stroke="none" />
    <circle cx="10.5" cy="6.5" r="1.6" fill="currentColor" stroke="none" />
    <circle cx="14.5" cy="6.5" r="1.6" fill="currentColor" stroke="none" />
    <circle cx="18" cy="9" r="1.6" fill="currentColor" stroke="none" />
  </IconBase>
</template>
