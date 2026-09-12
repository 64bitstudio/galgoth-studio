<script setup lang="ts">
// Ticket 073 -- ícono de tipo de mob "Arácnido" (cuerpo central + 8 patas
// radiando en diagonal), usado en MobCard.vue y el filtro "Tipo".
import IconBase from './IconBase.vue'
</script>

<template>
  <IconBase>
    <ellipse cx="12" cy="12" rx="3" ry="2.5" />
    <path d="M9.5 10.5 L4 7" />
    <path d="M9 12 L3 12" />
    <path d="M9.5 13.5 L4 17" />
    <path d="M9.7 11.5 L5 15" />
    <path d="M14.5 10.5 L20 7" />
    <path d="M15 12 L21 12" />
    <path d="M14.5 13.5 L20 17" />
    <path d="M14.3 11.5 L19 15" />
  </IconBase>
</template>
