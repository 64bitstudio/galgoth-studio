<script setup lang="ts">
/** Ticket 068 -- flecha hacia una bandeja, invertida respecto a IconUpload.vue (exportar/descargar, en vez de subir). El botón "Exportar" de MobEditor.vue no tenía ningún ícono hasta este ticket. */
import IconBase from './IconBase.vue'
</script>

<template>
  <IconBase>
    <path d="M12 4v12M12 16l-4-4M12 16l4-4" />
    <path d="M4 16v3a1 1 0 001 1h14a1 1 0 001-1v-3" />
  </IconBase>
</template>
