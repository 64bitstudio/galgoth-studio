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
  <div class="hierarchy-panel">
    <h2 class="hierarchy-panel__title">Jerarquía</h2>
    <ul class="hierarchy-panel__tree app-scroll">
      <HierarchyBoneNode v-for="node in tree" :key="node.bone.id" :node="node" />
    </ul>
  </div>
</template>

<style scoped>
.hierarchy-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
}

.hierarchy-panel__title {
  margin: 0;
  padding: var(--space-4) var(--space-4) var(--space-2);
  font-size: var(--text-sm);
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  color: var(--muted);
  flex-shrink: 0;
}

.hierarchy-panel__tree {
  margin: 0;
  padding: 0 var(--space-2) var(--space-2);
  overflow-y: auto;
  flex: 1;
}
</style>
