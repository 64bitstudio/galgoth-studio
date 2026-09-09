<script setup lang="ts">
/**
 * Panel lateral de jerarquía (ticket 017, mockup 05: layout
 * `hierarchy | viewport | inspector`) -- muestra bones y sus cuboids
 * hijos en la estructura correcta (AC #1), sincronizado con el viewport
 * vía `useSelectionStore` (compartido con `ThreeViewport.vue`).
 */
import { computed } from 'vue'
import type { MobProjectModel } from '../domain/MobProjectModel'
import { buildHierarchyTree } from './hierarchyTree'
import HierarchyBoneNode from './HierarchyBoneNode.vue'

const props = defineProps<{ model: MobProjectModel }>()

const tree = computed(() => buildHierarchyTree(props.model))
</script>

<template>
  <ul class="hierarchy-panel">
    <HierarchyBoneNode v-for="node in tree" :key="node.bone.id" :node="node" />
  </ul>
</template>

<style scoped>
.hierarchy-panel {
  margin: 0;
  padding: 0.5rem;
  font-family: monospace;
  font-size: 0.85rem;
  overflow-y: auto;
}
</style>
