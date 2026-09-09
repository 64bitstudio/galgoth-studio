<script setup lang="ts">
/**
 * Toolbar de herramientas de transformación + Add/Delete/Duplicate
 * (ticket 018) + Undo/Redo (ticket 019). Los modos Move/Scale/Rotate
 * controlan el gizmo 3D (`TransformControls`, en `ThreeViewportService`)
 * sobre el cuboid seleccionado -- "Select" es el estado por defecto (el
 * click para seleccionar siempre funciona, sin importar el modo activo).
 *
 * Undo/Redo llaman directo a la pila de Command de `draftModelStore` --
 * ver el comentario de cabecera de ese archivo para el diseño. Además de
 * los botones, se cablean los atajos estándar (Cmd/Ctrl+Z, Cmd/Ctrl+Shift+Z)
 * mientras el foco NO esté en un campo de texto/número (para no pelear
 * con el undo nativo del propio input, ej. al editar un pivote).
 *
 * Ticket 023: botón "Guardar" real -- primero commitea la revisión
 * (`saveRevision`, backend del ticket 020); SOLO si eso tiene éxito
 * intenta capturar+subir el thumbnail, en un try/catch INDEPENDIENTE
 * que únicamente loguea con `console.warn` si falla. El thumbnail es un
 * asset derivado best-effort -- su fallo nunca debe revertir ni bloquear
 * el Guardar que ya se completó (ver docstring de `thumbnailApi.ts`).
 */
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { threeViewportService } from '../viewport/ThreeViewportService'
import { useDraftModelStore } from './draftModelStore'
import { useSelectionStore } from './selectionStore'
import { saveRevision } from './draftPersistenceApi'
import { uploadThumbnail } from './thumbnailApi'

type TransformMode = 'translate' | 'scale' | 'rotate'

const draft = useDraftModelStore()
const selection = useSelectionStore()
const mode = ref<TransformMode>('translate')
const saving = ref(false)
const saveMessage = ref<string | null>(null)

function setMode(newMode: TransformMode): void {
  mode.value = newMode
  threeViewportService.setTransformMode(newMode)
}

function isEditableTarget(target: EventTarget | null): boolean {
  return target instanceof HTMLElement && (target.tagName === 'INPUT' || target.tagName === 'TEXTAREA')
}

function handleKeydown(event: KeyboardEvent): void {
  if (isEditableTarget(event.target) || !(event.ctrlKey || event.metaKey) || event.key.toLowerCase() !== 'z') {
    return
  }
  event.preventDefault()
  if (event.shiftKey) {
    draft.redo()
  } else {
    draft.undo()
  }
}

onMounted(() => window.addEventListener('keydown', handleKeydown))
onBeforeUnmount(() => window.removeEventListener('keydown', handleKeydown))

/** El bone del cuboid seleccionado, o el primer bone del modelo si no hay selección -- destino razonable por defecto para "Add cuboid". */
function contextBoneId(): string | undefined {
  const selectedCuboid = draft.model?.cuboids.find((c) => c.id === selection.selectedCuboidId)
  return selectedCuboid?.boneId ?? draft.model?.bones[0]?.id
}

function addCuboid(): void {
  const boneId = contextBoneId()
  if (!boneId) {
    return
  }
  const newId = draft.addCuboid(boneId, 'nuevo_cuboid', [-2, 0, -2], [2, 4, 2], [0, 2, 0])
  if (newId) {
    selection.select(newId)
  }
}

function addBone(): void {
  const parentId = contextBoneId() ?? null
  draft.addBone(parentId, 'nuevo_bone', [0, 0, 0], [0, 0, 0])
}

function duplicateSelected(): void {
  if (!selection.selectedCuboidId) {
    return
  }
  const newId = draft.duplicate(selection.selectedCuboidId)
  if (newId) {
    selection.select(newId)
  }
}

function deleteSelected(): void {
  if (!selection.selectedCuboidId) {
    return
  }
  draft.deleteCuboid(selection.selectedCuboidId)
  selection.select(null)
}

async function handleSave(): Promise<void> {
  const model = draft.model
  if (!model || saving.value) {
    return
  }
  saving.value = true
  saveMessage.value = null
  try {
    const result = await saveRevision(model.mobId, model)
    saveMessage.value = result.created ? `Guardado (revisión ${result.revisionNumber}).` : (result.reason ?? 'Sin cambios.')
  } catch (error) {
    saveMessage.value = error instanceof Error ? error.message : 'No se pudo guardar.'
    saving.value = false
    return
  }

  // El thumbnail es un side-effect independiente: su fallo NUNCA revierte
  // ni bloquea el Guardar que ya se completó arriba.
  try {
    const png = await threeViewportService.captureThumbnail()
    await uploadThumbnail(model.mobId, png)
  } catch (error) {
    console.warn('[EditorToolbar] no se pudo generar/subir el thumbnail:', error)
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="editor-toolbar">
    <button type="button" :class="{ 'editor-toolbar__button--active': mode === 'translate' }" @click="setMode('translate')">
      Move
    </button>
    <button type="button" :class="{ 'editor-toolbar__button--active': mode === 'scale' }" @click="setMode('scale')">
      Scale
    </button>
    <button type="button" :class="{ 'editor-toolbar__button--active': mode === 'rotate' }" @click="setMode('rotate')">
      Rotate
    </button>
    <span class="editor-toolbar__separator" />
    <button type="button" @click="addCuboid">Add cuboid</button>
    <button type="button" @click="addBone">Add bone</button>
    <button type="button" :disabled="!selection.selectedCuboidId" @click="duplicateSelected">Duplicate</button>
    <button type="button" :disabled="!selection.selectedCuboidId" @click="deleteSelected">Delete</button>
    <span class="editor-toolbar__separator" />
    <button type="button" :disabled="!draft.canUndo" @click="draft.undo()">Undo</button>
    <button type="button" :disabled="!draft.canRedo" @click="draft.redo()">Redo</button>
    <span class="editor-toolbar__separator" />
    <button type="button" :disabled="!draft.model || saving" @click="handleSave">{{ saving ? 'Guardando…' : 'Guardar' }}</button>
    <span v-if="saveMessage" class="editor-toolbar__save-message">{{ saveMessage }}</span>
    <span v-if="draft.lastError" class="editor-toolbar__error">{{ draft.lastError }}</span>
  </div>
</template>

<style scoped>
.editor-toolbar {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  flex-wrap: wrap;
}

.editor-toolbar__button--active {
  background: #ffb020;
  color: #1a1a1a;
}

.editor-toolbar__separator {
  width: 1px;
  height: 1.25rem;
  background: #444;
}

.editor-toolbar__error {
  font-family: monospace;
  font-size: 0.8rem;
  color: #ff6b6b;
}

.editor-toolbar__save-message {
  font-family: monospace;
  font-size: 0.8rem;
  color: #7ed6a5;
}
</style>
