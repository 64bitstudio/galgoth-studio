<script setup lang="ts">
/**
 * Nodo recursivo del árbol de jerarquía (ticket 017) -- Vue permite que
 * un SFC se auto-referencie por su nombre de archivo para recursión, sin
 * import explícito.
 *
 * Ticket 018: agrega edición del pivote del bone (AC #5 -- inputs
 * numéricos, no un gizmo 3D, ya que los bones no son seleccionables en
 * el viewport, ver ticket 017) y borrado con advertencia explícita de
 * impacto en cascada ANTES de confirmar (AC #3).
 */
import { ref } from 'vue'
import type { BoneNode } from './hierarchyTree'
import { useDraftModelStore } from './draftModelStore'
import { useSelectionStore } from './selectionStore'

const props = defineProps<{ node: BoneNode }>()

const selection = useSelectionStore()
const draft = useDraftModelStore()

const isEditingPivot = ref(false)
const pendingDelete = ref(false)

function commitPivot(axis: 0 | 1 | 2, event: Event): void {
  const value = Number((event.target as HTMLInputElement).value)
  if (Number.isNaN(value)) {
    return
  }
  const pivot: [number, number, number] = [...props.node.bone.pivot]
  pivot[axis] = value
  draft.setPivot(props.node.bone.id, pivot)
}

function requestDelete(): void {
  pendingDelete.value = true
}

function confirmDelete(): void {
  draft.deleteBoneCascade(props.node.bone.id)
  pendingDelete.value = false
}

function cancelDelete(): void {
  pendingDelete.value = false
}

const impact = draft.boneRemovalImpact(props.node.bone.id)
</script>

<template>
  <li class="hierarchy-bone-node">
    <div class="hierarchy-bone-node__header">
      <span class="hierarchy-bone-node__name">{{ node.bone.name }}</span>
      <button type="button" class="hierarchy-bone-node__icon-button" @click="isEditingPivot = !isEditingPivot">
        pivot
      </button>
      <button type="button" class="hierarchy-bone-node__icon-button" @click="requestDelete">✕</button>
    </div>

    <div v-if="isEditingPivot" class="hierarchy-bone-node__pivot-editor">
      <label>X <input type="number" :value="node.bone.pivot[0]" aria-label="pivot x" @change="commitPivot(0, $event)" /></label>
      <label>Y <input type="number" :value="node.bone.pivot[1]" aria-label="pivot y" @change="commitPivot(1, $event)" /></label>
      <label>Z <input type="number" :value="node.bone.pivot[2]" aria-label="pivot z" @change="commitPivot(2, $event)" /></label>
    </div>

    <div v-if="pendingDelete" class="hierarchy-bone-node__delete-warning">
      <p>
        Eliminar '{{ node.bone.name }}' también elimina {{ (impact?.affectedBoneIds.length ?? 1) - 1 }} bone(s)
        hijo(s) y {{ impact?.affectedCuboidIds.length ?? 0 }} cuboid(s) en cascada. Esta acción no se puede deshacer
        todavía (Command stack de undo/redo llega en el ticket 019).
      </p>
      <button type="button" @click="confirmDelete">Confirmar</button>
      <button type="button" @click="cancelDelete">Cancelar</button>
    </div>

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

.hierarchy-bone-node__header {
  display: flex;
  align-items: center;
  gap: 0.25rem;
}

.hierarchy-bone-node__name {
  font-weight: 600;
}

.hierarchy-bone-node__icon-button {
  font-size: 0.7rem;
  padding: 0 0.25rem;
}

.hierarchy-bone-node__pivot-editor {
  display: flex;
  gap: 0.5rem;
  padding: 0.25rem 0 0.25rem 1rem;
  font-size: 0.75rem;
}

.hierarchy-bone-node__pivot-editor input {
  width: 3.5rem;
}

.hierarchy-bone-node__delete-warning {
  background: rgba(255, 107, 107, 0.15);
  border: 1px solid #ff6b6b;
  border-radius: 4px;
  padding: 0.5rem;
  margin: 0.25rem 0;
  font-size: 0.8rem;
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
