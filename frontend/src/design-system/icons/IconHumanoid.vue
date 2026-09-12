<script setup lang="ts">
// Ticket 073 -- ícono de tipo de mob "Humanoide" (silueta de persona), usado
// en el footer de MobCard.vue y en el filtro "Tipo" de ProjectDetail.vue.
import IconBase from './IconBase.vue'
</script>

<template>
  <IconBase>
    <circle cx="12" cy="5.5" r="2.5" />
    <path d="M12 9 v6" />
    <path d="M8 12 h8" />
    <path d="M12 15 l-3.5 6" />
    <path d="M12 15 l3.5 6" />
  </IconBase>
</template>
