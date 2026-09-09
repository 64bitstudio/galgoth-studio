<script setup lang="ts">
/**
 * Panel lateral de jerarquía (ticket 017, mockup 05: layout
 * `hierarchy | viewport | inspector`) -- muestra bones y sus cuboids
 * hijos en la estructura correcta (AC #1), sincronizado con el viewport
 * vía `useSelectionStore` (compartido con `ThreeViewport.vue`).
 *
 * Ticket 018: lee el modelo de `useDraftModelStore` (ya no como prop) --
 * el mismo draft editable que las herramientas de transformación mutan.
 */
import { computed } from 'vue'
import { useDraftModelStore } from './draftModelStore'
import { buildHierarchyTree } from './hierarchyTree'
import HierarchyBoneNode from './HierarchyBoneNode.vue'

const draft = useDraftModelStore()

const tree = computed(() => (draft.model ? buildHierarchyTree(draft.model) : []))
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
