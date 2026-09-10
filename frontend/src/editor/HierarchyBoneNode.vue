<script setup lang="ts">
/**
 * Nodo recursivo del árbol de jerarquía (ticket 017) -- Vue permite que
 * un SFC se auto-referencie por su nombre de archivo para recursión, sin
 * import explícito.
 *
 * Ticket 036 (pasada de fidelidad visual, mockup 05): el editor inline
 * de pivote (inputs numéricos sueltos dentro del árbol) se MUEVE al
 * panel de Propiedades (`InspectorPanel.vue`) -- mismo método de store
 * (`draft.setPivot`), sin duplicar la lógica; el árbol ahora solo
 * selecciona/expande. `expanded` es estado puramente visual local (no
 * existía ningún concepto de colapsar antes de este ticket -- el árbol
 * siempre se mostraba completamente abierto), default `true` para no
 * cambiar el comportamiento por defecto ya conocido.
 */
import { ref } from 'vue'
import type { BoneNode } from './hierarchyTree'
import { useDraftModelStore } from './draftModelStore'
import { useSelectionStore } from './selectionStore'
import IconChevron from '../design-system/icons/IconChevron.vue'
import IconBoneJoint from '../design-system/icons/IconBoneJoint.vue'
import IconCuboid from '../design-system/icons/IconCuboid.vue'
import IconTrash from '../design-system/icons/IconTrash.vue'
import GButton from '../design-system/components/GButton.vue'
import IconButton from '../design-system/components/IconButton.vue'

const props = defineProps<{ node: BoneNode }>()

const selection = useSelectionStore()
const draft = useDraftModelStore()

const expanded = ref(true)
const pendingDelete = ref(false)

function hasChildren(): boolean {
  return props.node.cuboids.length > 0 || props.node.children.length > 0
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
  <li class="hierarchy-node">
    <div class="hierarchy-node__row hierarchy-node__row--bone">
      <button v-if="hasChildren() && expanded" type="button" class="hierarchy-node__toggle" aria-label="Contraer" @click="expanded = false"><IconChevron :size="14" :expanded="true" /></button>
      <button v-else-if="hasChildren()" type="button" class="hierarchy-node__toggle" aria-label="Expandir" @click="expanded = true"><IconChevron :size="14" :expanded="false" /></button>
      <span v-else class="hierarchy-node__toggle-spacer" aria-hidden="true"></span>
      <IconBoneJoint :size="15" class="hierarchy-node__icon hierarchy-node__icon--bone" />
      <span class="hierarchy-node__name" :title="node.bone.name">{{ node.bone.name }}</span>
      <IconButton label="Eliminar bone" size="sm" class="hierarchy-node__delete" @click="requestDelete"><IconTrash :size="14" /></IconButton>
    </div>

    <div v-if="pendingDelete" class="hierarchy-node__delete-warning">
      <p>
        Eliminar "{{ node.bone.name }}" también elimina {{ (impact?.affectedBoneIds.length ?? 1) - 1 }} bone(s) hijo(s) y
        {{ impact?.affectedCuboidIds.length ?? 0 }} cuboid(s) en cascada.
      </p>
      <div class="hierarchy-node__delete-actions">
        <GButton variant="ghost" @click="cancelDelete">Cancelar</GButton>
        <GButton variant="danger" @click="confirmDelete">Confirmar</GButton>
      </div>
    </div>

    <ul v-if="expanded" class="hierarchy-node__children">
      <li
        v-for="cuboid in node.cuboids"
        :key="cuboid.id"
        class="hierarchy-node__row hierarchy-node__row--cuboid"
        :class="{ 'hierarchy-node__row--selected': cuboid.id === selection.selectedCuboidId }"
        @click="selection.select(cuboid.id)"
      >
        <span class="hierarchy-node__toggle-spacer" aria-hidden="true"></span>
        <IconCuboid :size="14" class="hierarchy-node__icon hierarchy-node__icon--cuboid" />
        <span class="hierarchy-node__name" :title="cuboid.name">{{ cuboid.name }}</span>
      </li>
      <HierarchyBoneNode v-for="child in node.children" :key="child.bone.id" :node="child" />
    </ul>
  </li>
</template>

<style scoped>
.hierarchy-node {
  list-style: none;
}

.hierarchy-node__row {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  min-height: 30px;
  padding: 0 var(--space-2);
  border-radius: var(--radius-sm);
  cursor: pointer;
}

.hierarchy-node__row--bone {
  cursor: default;
  font-weight: 600;
}

.hierarchy-node__row:hover {
  background: var(--surface-2);
}

.hierarchy-node__row--selected {
  background: var(--accent-soft);
  color: var(--accent);
  font-weight: 600;
}

.hierarchy-node__toggle,
.hierarchy-node__toggle-spacer {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 16px;
  height: 16px;
  flex-shrink: 0;
  padding: 0;
  background: none;
  border: none;
  color: var(--muted);
  cursor: pointer;
}

.hierarchy-node__icon {
  flex-shrink: 0;
}

.hierarchy-node__icon--bone {
  color: var(--muted);
}

.hierarchy-node__icon--cuboid {
  color: inherit;
  opacity: 0.85;
}

.hierarchy-node__name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: var(--text-sm);
}

/* Ticket 039 -- IconButton ya trae su propio tamaño/borde/estados; acá solo se ajusta que quede oculto hasta el hover de la fila (comportamiento propio de este árbol, no del botón genérico). */
.hierarchy-node__delete {
  flex-shrink: 0;
  opacity: 0;
  transition: opacity var(--transition-fast);
}

.hierarchy-node__row--bone:hover .hierarchy-node__delete {
  opacity: 1;
}

.hierarchy-node__delete-warning {
  margin: var(--space-1) 0 var(--space-1) 22px;
  padding: var(--space-3);
  background: var(--danger-soft);
  border: var(--border-width) solid var(--danger);
  border-radius: var(--radius-md);
  font-size: var(--text-xs);
}

.hierarchy-node__delete-warning p {
  margin: 0 0 var(--space-2);
  color: var(--text);
}

.hierarchy-node__delete-actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--space-2);
}

.hierarchy-node__children {
  margin: 0;
  padding-left: 18px;
}
</style>
