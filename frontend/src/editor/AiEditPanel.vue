<script setup lang="ts">
/**
 * "Asistente IA" (ticket 031, HU-17/HU-18, mockup 06) -- edición
 * conversacional sobre el mob YA cargado en `MobEditor.vue` (034):
 * instrucción en lenguaje natural → plan (resumen + elementos
 * cambiados + Antes/Después) → Aplicar/Cancelar. El plan generado
 * NUNCA toca `mob_drafts`/`mob_revisions` -- eso solo pasa al
 * confirmar "Aplicar cambios" (AC #2), y el propio backend rechaza con
 * 409 si el draft/revisión base avanzaron mientras tanto (AC #5) --
 * acá se traduce ese 409 en un mensaje explícito con la opción de
 * regenerar, nunca un error genérico.
 *
 * Este componente NUNCA renderiza el viewport 3D él mismo: emite
 * `preview-model-changed` con el modelo a mostrar (o `null` para
 * "sin overlay, mostrar el editor real") y deja que `MobEditor.vue`
 * decida qué montar en el canvas principal. Motivo: `ThreeViewportService`
 * es un singleton deliberado (un solo canvas WebGL del proceso, ver
 * ese servicio y ticket 008 AC#3) que `MobEditor.vue` YA usa para el
 * viewport editable real (`ThreeViewport.vue`) -- si este panel también
 * intentara montar su propio `GenerationPreviewViewport.vue` de forma
 * simultánea e independiente, competiría por el mismo canvas. Por eso
 * el mockup de Antes/Después lado-a-lado se resuelve acá como un
 * toggle sobre UN solo modelo (decisión de producto confirmada
 * explícitamente), y es `MobEditor.vue` quien efectivamente monta
 * `GenerationPreviewViewport.vue` en el canvas principal mientras haya
 * un plan activo, en vez de `ThreeViewport.vue`.
 *
 * Ticket 069: este componente vive embebido dentro de `GDrawer.vue`
 * (drawer compartido con el generador de textura de Textura) -- ya NO
 * renderiza su propio título ("Asistente IA"), lo hace el `title` del
 * drawer para no duplicarlo.
 */
import { computed, ref, watch } from 'vue'
import { applyEdit, requestEditPlan, type EditGeometryPlan } from './aiEditApi'
import { ApiError } from '../api/ApiError'
import GButton from '../design-system/components/GButton.vue'
import type { MobProjectModel } from '../domain/MobProjectModel'

const props = defineProps<{ mobId: string }>()
const emit = defineEmits<{
  applied: [model: MobProjectModel]
  'preview-model-changed': [model: MobProjectModel | null]
}>()

const instruction = ref('')
const plan = ref<EditGeometryPlan | null>(null)
const requesting = ref(false)
const requestError = ref<string | null>(null)
const applying = ref(false)
const applyError = ref<string | null>(null)
const staleBase = ref(false)
const showingAfter = ref(true)

const previewModel = computed<MobProjectModel | null>(() => {
  if (!plan.value) {
    return null
  }
  return showingAfter.value ? plan.value.afterModel : plan.value.beforeModel
})

watch(previewModel, (model) => emit('preview-model-changed', model))

async function generatePlan(): Promise<void> {
  if (!instruction.value.trim()) {
    return
  }
  requesting.value = true
  requestError.value = null
  applyError.value = null
  staleBase.value = false
  try {
    showingAfter.value = true
    plan.value = await requestEditPlan(props.mobId, instruction.value.trim())
  } catch (error) {
    requestError.value = error instanceof ApiError ? error.message : 'No se pudo generar el plan de cambio.'
  } finally {
    requesting.value = false
  }
}

function cancelPlan(): void {
  plan.value = null
  applyError.value = null
  staleBase.value = false
}

async function confirmApply(): Promise<void> {
  if (!plan.value) {
    return
  }
  applying.value = true
  applyError.value = null
  staleBase.value = false
  try {
    const afterModel = plan.value.afterModel
    await applyEdit(plan.value.jobId)
    plan.value = null
    instruction.value = ''
    emit('applied', afterModel)
  } catch (error) {
    if (error instanceof ApiError && error.code === 'STALE_EDIT_BASE') {
      staleBase.value = true
    }
    applyError.value = error instanceof ApiError ? error.message : 'No se pudo aplicar este cambio.'
  } finally {
    applying.value = false
  }
}

async function regenerate(): Promise<void> {
  plan.value = null
  await generatePlan()
}
</script>

<template>
  <div class="ai-edit-panel">
    <template v-if="!plan">
      <textarea
        v-model="instruction"
        class="ai-edit-panel__instruction"
        rows="4"
        placeholder="Haz las manos más grandes y los hombros más irregulares."
        aria-label="Instrucción para la IA"
        :disabled="requesting"
      />
      <p v-if="requestError" class="ai-edit-panel__error">{{ requestError }}</p>
      <GButton variant="primary" :disabled="requesting || !instruction.trim()" @click="generatePlan">
        {{ requesting ? 'Generando…' : 'Generar cambios' }}
      </GButton>
    </template>

    <template v-else>
      <p class="ai-edit-panel__summary">{{ plan.summary }}</p>

      <div class="ai-edit-panel__toggle" role="tablist" aria-label="Antes o después del cambio, en el canvas principal">
        <button
          type="button"
          role="tab"
          :aria-selected="!showingAfter"
          class="ai-edit-panel__toggle-btn"
          :class="{ 'ai-edit-panel__toggle-btn--active': !showingAfter }"
          @click="showingAfter = false"
        >
          Antes
        </button>
        <button
          type="button"
          role="tab"
          :aria-selected="showingAfter"
          class="ai-edit-panel__toggle-btn"
          :class="{ 'ai-edit-panel__toggle-btn--active': showingAfter }"
          @click="showingAfter = true"
        >
          Después
        </button>
      </div>

      <div class="ai-edit-panel__suggestion">
        <p class="ai-edit-panel__suggestion-title">La IA modificará:</p>
        <ul class="ai-edit-panel__changed-list">
          <li v-for="element in plan.changedElements" :key="`${element.type}-${element.id}`">
            <span :class="`ai-edit-panel__change-kind ai-edit-panel__change-kind--${element.changeKind}`" aria-hidden="true">
              {{ element.changeKind === 'added' ? '+' : element.changeKind === 'removed' ? '−' : '~' }}
            </span>
            {{ element.name }} ({{ element.changeKind === 'added' ? 'nuevo' : element.changeKind === 'removed' ? 'eliminado' : 'modificado' }})
          </li>
        </ul>
        <p class="ai-edit-panel__changed-count">{{ plan.changedElements.length }} elementos modificados</p>
      </div>

      <p v-if="applyError" class="ai-edit-panel__error">{{ applyError }}</p>
      <GButton v-if="staleBase" variant="secondary" :disabled="requesting" @click="regenerate">Regenerar contra el estado actual</GButton>

      <div class="ai-edit-panel__actions">
        <GButton variant="secondary" :disabled="applying" @click="cancelPlan">Cancelar</GButton>
        <GButton variant="primary" :disabled="applying" @click="confirmApply">{{ applying ? 'Aplicando…' : 'Aplicar cambios' }}</GButton>
      </div>
    </template>
  </div>
</template>

<style scoped>
/* Ticket 069: el padding/scroll del contenedor pasa a ser responsabilidad
   de `.g-drawer__body` (GDrawer.vue) -- este componente ya no gestiona su
   propio scroll ni layout de página completa. */
.ai-edit-panel {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.ai-edit-panel__title {
  margin: 0;
}

.ai-edit-panel__instruction {
  width: 100%;
  box-sizing: border-box;
  padding: var(--space-2);
  background: var(--surface);
  border: var(--border-width) solid var(--border);
  border-radius: var(--radius-md);
  color: var(--text);
  font: inherit;
  resize: vertical;
}

.ai-edit-panel__error {
  margin: 0;
  color: var(--danger);
  font-size: var(--text-sm);
}

.ai-edit-panel__summary {
  margin: 0;
  font-weight: 600;
}

.ai-edit-panel__toggle {
  display: flex;
  gap: var(--space-1);
}

.ai-edit-panel__toggle-btn {
  flex: 1;
  min-height: var(--hit-target-min);
  padding: 0 var(--space-3);
  background: var(--surface);
  border: var(--border-width) solid var(--border);
  border-radius: var(--radius-md);
  color: var(--muted);
  font: inherit;
  cursor: pointer;
}

.ai-edit-panel__toggle-btn--active {
  background: var(--surface-2);
  border-color: var(--accent);
  color: var(--text);
  font-weight: 600;
}

.ai-edit-panel__suggestion {
  padding: var(--space-2);
  background: var(--surface-2);
  border: var(--border-width) solid var(--border);
  border-radius: var(--radius-md);
}

.ai-edit-panel__suggestion-title {
  margin: 0 0 var(--space-1);
  font-size: var(--text-xs);
  color: var(--muted);
}

.ai-edit-panel__changed-list {
  margin: 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
  font-size: var(--text-sm);
}

.ai-edit-panel__change-kind {
  font-weight: 700;
}

.ai-edit-panel__change-kind--added {
  color: var(--accent);
}

.ai-edit-panel__change-kind--removed {
  color: var(--danger);
}

.ai-edit-panel__changed-count {
  margin: var(--space-1) 0 0;
  font-size: var(--text-xs);
  color: var(--muted);
}

.ai-edit-panel__actions {
  display: flex;
  gap: var(--space-2);
}
</style>
