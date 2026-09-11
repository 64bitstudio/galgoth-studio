<script setup lang="ts">
/** Ticket 068 -- panel con divisor + flecha, colapsar/expandir el sidebar de navegación (GSidebar.vue). Se espeja horizontalmente por CSS cuando el sidebar está colapsado (la misma forma sirve para "expandir"). */
import IconBase from './IconBase.vue'
</script>

<template>
  <IconBase>
    <rect x="3" y="4" width="18" height="16" rx="2" />
    <path d="M9 4v16" />
    <path d="M14 10l-2 2 2 2" />
  </IconBase>
</template>
