<script setup lang="ts">
/**
 * Panel de Propiedades del editor (ticket 036, pasada de fidelidad
 * visual -- mockup 05: layout `Jerarquía | Viewport | Propiedades`, el
 * panel derecho que hasta este ticket prácticamente no existía).
 *
 * Muestra Posición/Tamaño/Rotación del CUBOID seleccionado + Pivot del
 * BONE que lo contiene -- no hay ningún concepto de selección de bone
 * todavía (`useSelectionStore` solo soporta `selectedCuboidId`, ticket
 * 017), así que "Pivot" siempre refleja el bone padre del cuboid activo,
 * nunca un bone elegido aparte. Esto no es una simplificación nueva de
 * este ticket -- es el único mapeo posible contra el dominio real: los
 * cuboids no tienen pivot propio (solo los bones, ver `Bone.pivot`).
 *
 * CERO funcionalidad nueva: los 4 campos son editables porque llaman a
 * métodos que YA EXISTEN en `draftModelStore` (los mismos que ya usa el
 * gizmo 3D de `ThreeViewport.vue` al soltar un drag) -- Posición/Tamaño/
 * Rotación son delta/escala respecto al valor actual (mismo cálculo que
 * hace el gizmo, `moveSelectedCuboid`/`resizeSelectedCuboid`/
 * `rotateSelectedCuboid`), Pivot es un set absoluto (`setPivot`, mismo
 * método que ya usaba el editor inline de `HierarchyBoneNode.vue` antes
 * de este ticket -- movido aquí, no duplicado).
 */
import { computed } from 'vue'
import type { Vec3 } from '../domain/MobProjectModel'
import { useDraftModelStore } from './draftModelStore'
import { useSelectionStore } from './selectionStore'
import InspectorField from './InspectorField.vue'

const draft = useDraftModelStore()
const selection = useSelectionStore()

const selectedCuboid = computed(() => draft.model?.cuboids.find((c) => c.id === selection.selectedCuboidId) ?? null)

const ownerBone = computed(() => {
  const cuboid = selectedCuboid.value
  return cuboid ? draft.model?.bones.find((b) => b.id === cuboid.boneId) ?? null : null
})

const size = computed<Vec3>(() => {
  const cuboid = selectedCuboid.value
  if (!cuboid) {
    return [0, 0, 0]
  }
  return [cuboid.to[0] - cuboid.from[0], cuboid.to[1] - cuboid.from[1], cuboid.to[2] - cuboid.from[2]]
})

function subtract(a: Vec3, b: Vec3): Vec3 {
  return [a[0] - b[0], a[1] - b[1], a[2] - b[2]]
}

function updatePosition(newOrigin: Vec3): void {
  const cuboid = selectedCuboid.value
  if (!cuboid) {
    return
  }
  draft.moveSelectedCuboid(cuboid.id, subtract(newOrigin, cuboid.origin))
}

function updateSize(newSize: Vec3): void {
  const cuboid = selectedCuboid.value
  if (!cuboid) {
    return
  }
  const currentSize = size.value
  const scale: Vec3 = [0, 1, 2].map((i) => (currentSize[i] !== 0 ? newSize[i] / currentSize[i] : 1)) as Vec3
  if (scale.every((s) => s > 0)) {
    draft.resizeSelectedCuboid(cuboid.id, scale)
  }
}

function updateRotation(newRotation: Vec3): void {
  const cuboid = selectedCuboid.value
  if (!cuboid) {
    return
  }
  draft.rotateSelectedCuboid(cuboid.id, subtract(newRotation, cuboid.rotation))
}

function updatePivot(newPivot: Vec3): void {
  const bone = ownerBone.value
  if (!bone) {
    return
  }
  draft.setPivot(bone.id, newPivot)
}
</script>

<template>
  <aside class="inspector-panel">
    <h2 class="inspector-panel__title">Propiedades</h2>
    <template v-if="selectedCuboid">
      <p class="inspector-panel__selection">{{ selectedCuboid.name }}</p>
      <InspectorField label="Posición" :value="selectedCuboid.origin" @update="updatePosition" />
      <InspectorField label="Tamaño" :value="size" @update="updateSize" />
      <InspectorField label="Rotación" :value="selectedCuboid.rotation" @update="updateRotation" />
      <InspectorField v-if="ownerBone" label="Pivot" :value="ownerBone.pivot" @update="updatePivot" />
    </template>
    <div v-else class="inspector-panel__empty">
      <p>Selecciona un elemento en la jerarquía o el viewport para ver y editar sus propiedades.</p>
    </div>
  </aside>
</template>

<style scoped>
.inspector-panel {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
  padding: var(--space-4);
  height: 100%;
  overflow-y: auto;
}

.inspector-panel__title {
  margin: 0;
  font-size: var(--text-sm);
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  color: var(--muted);
}

.inspector-panel__selection {
  margin: calc(var(--space-4) * -1) 0 0;
  font-size: var(--text-md);
  font-weight: 600;
  color: var(--text);
  font-family: var(--font-mono);
}

.inspector-panel__empty {
  flex: 1;
  display: flex;
  align-items: center;
  color: var(--muted);
  font-size: var(--text-sm);
  text-align: center;
  padding: var(--space-4);
}
</style>
