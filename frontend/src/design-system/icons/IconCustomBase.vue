<script setup lang="ts">
// Ticket 073 -- ícono de tipo de mob "Personalizado" (bloque/voxel abierto,
// eco del guiño Minecraft del resto del sistema de íconos), usado en
// MobCard.vue y el filtro "Tipo". Nombre "CustomBase" (no "IconCustom") para
// no chocar con el uso genérico de la palabra en otros contextos del design system.
import IconBase from './IconBase.vue'
</script>

<template>
  <IconBase>
    <path d="M12 3 L20 7.5 V16.5 L12 21 L4 16.5 V7.5 Z" />
    <path d="M4 7.5 L12 12 L20 7.5" />
    <path d="M12 12 V21" />
  </IconBase>
</template>
