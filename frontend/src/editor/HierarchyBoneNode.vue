<script setup lang="ts">
/**
 * Nodo recursivo del árbol de jerarquía (ticket 017) -- Vue permite que
 * un SFC se auto-referencie por su nombre de archivo para recursión, sin
 * import explícito.
 */
import type { BoneNode } from './hierarchyTree'
import { useSelectionStore } from './selectionStore'

defineProps<{ node: BoneNode }>()

const selection = useSelectionStore()
</script>

<template>
  <li class="hierarchy-bone-node">
    <span class="hierarchy-bone-node__name">{{ node.bone.name }}</span>
    <ul class="hierarchy-bone-node__children">
      <li
        v-for="cuboid in node.cuboids"
        :key="cuboid.id"
        class="hierarchy-cuboid-node"
        :class="{ 'hierarchy-cuboid-node--selected': cuboid.id === selection.selectedCuboidId }"
        @click="selection.select(cuboid.id)"
      >
        {{ cuboid.name }}
      </li>
      <HierarchyBoneNode v-for="child in node.children" :key="child.bone.id" :node="child" />
    </ul>
  </li>
</template>

<style scoped>
.hierarchy-bone-node {
  list-style: none;
}

.hierarchy-bone-node__name {
  font-weight: 600;
}

.hierarchy-bone-node__children {
  margin: 0;
  padding-left: 1rem;
}

.hierarchy-cuboid-node {
  cursor: pointer;
  padding: 0.125rem 0.25rem;
  border-radius: 4px;
}

.hierarchy-cuboid-node:hover {
  background: rgba(255, 255, 255, 0.08);
}

.hierarchy-cuboid-node--selected {
  background: #ffb020;
  color: #1a1a1a;
}
</style>
