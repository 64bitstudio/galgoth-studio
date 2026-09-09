<script setup lang="ts">
/** Chevron de expand/collapse -- rota 90° via CSS cuando `expanded` es true (una sola forma, sin duplicar el path). */
import IconBase from './IconBase.vue'

withDefaults(defineProps<{ size?: number; expanded?: boolean }>(), { size: 18, expanded: false })
</script>

<template>
  <IconBase :size="size" :style="{ transform: expanded ? 'rotate(90deg)' : 'none', transition: 'transform 120ms ease' }">
    <path d="M9 5l7 7-7 7" />
  </IconBase>
</template>
