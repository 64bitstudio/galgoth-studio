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
import { useGeometryApplyStore } from './geometryApplyStore'
import { useSelectionStore } from './selectionStore'
import { saveRevision } from './draftPersistenceApi'
import { uploadThumbnail } from './thumbnailApi'
import IconButton from '../design-system/components/IconButton.vue'
import GButton from '../design-system/components/GButton.vue'
import IconMove from '../design-system/icons/IconMove.vue'
import IconScale from '../design-system/icons/IconScale.vue'
import IconRotate from '../design-system/icons/IconRotate.vue'
import IconCuboid from '../design-system/icons/IconCuboid.vue'
import IconBoneJoint from '../design-system/icons/IconBoneJoint.vue'
import IconDuplicate from '../design-system/icons/IconDuplicate.vue'
import IconTrash from '../design-system/icons/IconTrash.vue'
import IconUndo from '../design-system/icons/IconUndo.vue'
import IconRedo from '../design-system/icons/IconRedo.vue'

type TransformMode = 'translate' | 'scale' | 'rotate'

/** Ticket 039 -- el tooltip de Undo/Redo muestra el atajo REAL ya cableado en `handleKeydown` (nunca uno inventado): `metaKey` en Mac, `ctrlKey` en el resto. */
const MODIFIER_KEY = /mac/i.test(navigator.platform || navigator.userAgent) ? 'Cmd' : 'Ctrl'
const UNDO_SHORTCUT = `${MODIFIER_KEY}+Z`
const REDO_SHORTCUT = `${MODIFIER_KEY}+Shift+Z`

const draft = useDraftModelStore()
const geometryApply = useGeometryApplyStore()
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

/** Ticket 043: "Agregar cuboide" pasa a ser server-side (`POST /geometry/apply`, mismo servicio que Resize) -- el id real lo asigna el backend, no se puede predecir client-side. */
async function addCuboid(): Promise<void> {
  const boneId = contextBoneId()
  const mobId = draft.model?.mobId
  if (!boneId || !mobId) {
    return
  }
  const newId = await geometryApply.createCuboid(mobId, boneId, 'nuevo_cuboid', [-2, 0, -2], [2, 4, 2], [0, 2, 0])
  if (newId) {
    selection.select(newId)
  }
}

function addBone(): void {
  const parentId = contextBoneId() ?? null
  draft.addBone(parentId, 'nuevo_bone', [0, 0, 0], [0, 0, 0])
}

/**
 * Duplicar NO está en el alcance del ticket 043 (solo Resize/Add/Remove) --
 * sigue exactamente igual que antes, 100% client-side vía
 * `geometryOperations.ts`. Gap real, ya señalado en el ticket: internamente
 * duplicar es equivalente a un `createCuboid`, así que hereda el mismo
 * Hallazgo B (UV client-side sin revalidar) que 043 cierra para "Agregar" --
 * queda pendiente para un ticket futuro si el Product Owner decide cerrarlo.
 */
function duplicateSelected(): void {
  if (!selection.selectedCuboidId) {
    return
  }
  const newId = draft.duplicate(selection.selectedCuboidId)
  if (newId) {
    selection.select(newId)
  }
}

/** Ticket 043: "Eliminar" pasa a ser server-side (`POST /geometry/apply`, mismas reglas de 041 -- caras PAINTED del cuboid eliminado pasan a ORPHAN). */
async function deleteSelected(): Promise<void> {
  const cuboidId = selection.selectedCuboidId
  const mobId = draft.model?.mobId
  if (!cuboidId || !mobId) {
    return
  }
  await geometryApply.removeCuboid(mobId, cuboidId)
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
    <fieldset class="editor-toolbar__group" aria-label="Modo de transformación">
      <IconButton label="Mover" :active="mode === 'translate'" @click="setMode('translate')"><IconMove :size="18" /></IconButton>
      <IconButton label="Escalar" :active="mode === 'scale'" @click="setMode('scale')"><IconScale :size="18" /></IconButton>
      <IconButton label="Rotar" :active="mode === 'rotate'" @click="setMode('rotate')"><IconRotate :size="18" /></IconButton>
    </fieldset>
    <span class="editor-toolbar__separator" />
    <fieldset class="editor-toolbar__group" aria-label="Agregar elementos">
      <IconButton label="Agregar cuboide" @click="addCuboid"><IconCuboid :size="18" /></IconButton>
      <IconButton label="Agregar bone" @click="addBone"><IconBoneJoint :size="18" /></IconButton>
    </fieldset>
    <span class="editor-toolbar__separator" />
    <fieldset class="editor-toolbar__group" aria-label="Duplicar y eliminar">
      <IconButton label="Duplicar" :disabled="!selection.selectedCuboidId" @click="duplicateSelected"><IconDuplicate :size="18" /></IconButton>
      <IconButton label="Eliminar" :disabled="!selection.selectedCuboidId" @click="deleteSelected"><IconTrash :size="18" /></IconButton>
    </fieldset>
    <span class="editor-toolbar__separator" />
    <fieldset class="editor-toolbar__group" aria-label="Undo y redo">
      <IconButton label="Deshacer" :shortcut="UNDO_SHORTCUT" :disabled="!draft.canUndo" @click="draft.undo()"><IconUndo :size="18" /></IconButton>
      <IconButton label="Rehacer" :shortcut="REDO_SHORTCUT" :disabled="!draft.canRedo" @click="draft.redo()"><IconRedo :size="18" /></IconButton>
    </fieldset>
    <div class="editor-toolbar__spacer" />
    <span v-if="draft.lastError || geometryApply.lastError" class="editor-toolbar__error">{{ draft.lastError ?? geometryApply.lastError }}</span>
    <span v-if="saveMessage" class="editor-toolbar__save-message">{{ saveMessage }}</span>
    <GButton variant="primary" :disabled="!draft.model || saving" @click="handleSave">{{ saving ? 'Guardando…' : 'Guardar' }}</GButton>
  </div>
</template>

<style scoped>
.editor-toolbar {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-2);
  background: var(--panel);
  border: var(--border-width) solid var(--border);
  border-radius: var(--radius-md);
}

.editor-toolbar__group {
  /* <fieldset> real en vez de role="group" (hallazgo real de Sonar,
     S6819 -- mismo criterio ya documentado en GMenu.vue) -- resetea el
     borde/padding/min-width nativos que un <fieldset> trae por defecto. */
  display: flex;
  align-items: center;
  gap: 2px;
  margin: 0;
  padding: 0;
  border: none;
  min-width: 0;
}

.editor-toolbar__separator {
  width: var(--border-width);
  height: 24px;
  background: var(--border);
  flex-shrink: 0;
}

.editor-toolbar__spacer {
  flex: 1;
}

.editor-toolbar__error {
  font-family: var(--font-mono);
  font-size: var(--text-xs);
  color: var(--danger);
}

.editor-toolbar__save-message {
  font-family: var(--font-mono);
  font-size: var(--text-xs);
  color: var(--accent);
}
</style>
