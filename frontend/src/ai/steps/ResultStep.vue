<script setup lang="ts">
/**
 * Paso 4 "Resultado" del wizard (ticket 027 shell → ticket 030 real,
 * HU-12, mockup 04) -- muestra la propuesta YA completada y sus 3
 * acciones reales. Deliberadamente NO hace ningún fetch/llamada HTTP acá
 * (mismo criterio que `ConfigurationStep.vue`, 027): recibe cada dato
 * como prop y solo emite intención (`discard`/`regenerate`/`apply`) -- el
 * orquestador (`AiMobWizard.vue`) es quien centraliza los efectos
 * secundarios reales del wizard completo.
 *
 * `jobId` es la única prop verdaderamente opcional (default `null`): sin
 * ella, el componente asume que lo está montando `/dev/wizard-result-harness`
 * (ticket 027) con datos de ejemplo, y lo dice explícitamente en pantalla
 * -- nunca deja que un dato inventado se confunda con uno real.
 *
 * Ticket 037 (corrección de UX/fidelidad visual, mockup 04): `previewModel`
 * (nuevo, opcional) es el modelo final que `GenerationStep` ya construyó
 * en memoria vía SSE -- lo reutiliza `GenerationPreviewViewport` (029) de
 * solo lectura para que "Resultado" muestre el modelo real, no solo
 * números. Sin esta prop (harness de desarrollo), se omite sin más.
 *
 * **Gap conocido, documentado a propósito (VoBo del PO en este ticket)**:
 * al confirmar "Usar este modelo" NO existe todavía una ruta real de
 * "Editar modelo" (el editor manual, 016-018, solo se ejerció vía el
 * harness de desarrollo `/dev/viewport-harness` hasta ahora) -- el
 * orquestador navega de vuelta a `/projects/:projectId` en vez de abrir
 * un editor real. Cerrar esa ruta es alcance de un ticket futuro.
 */
import { ref } from 'vue'
import type { FmmIssue } from '../../api/generationResultApi'
import type { MobProjectModel } from '../../domain/MobProjectModel'
import GButton from '../../design-system/components/GButton.vue'
import IconCheck from '../../design-system/icons/IconCheck.vue'
import IconWarning from '../../design-system/icons/IconWarning.vue'
import GenerationPreviewViewport from '../GenerationPreviewViewport.vue'

const props = withDefaults(
  defineProps<{
    jobId?: string | null
    previewModel?: MobProjectModel | null
    mobName?: string
    cuboidCount?: number
    boneCount?: number
    textureWidth?: number
    textureHeight?: number
    fmmCompatible?: boolean
    fmmIssues?: FmmIssue[]
    busy?: boolean
    actionError?: string | null
  }>(),
  {
    jobId: null,
    previewModel: null,
    mobName: 'Carcomido',
    cuboidCount: 27,
    boneCount: 7,
    textureWidth: 128,
    textureHeight: 128,
    fmmCompatible: true,
    fmmIssues: () => [],
    busy: false,
    actionError: null,
  },
)

const emit = defineEmits<{ discard: []; regenerate: []; apply: [] }>()

type PendingAction = 'discard' | 'regenerate' | 'apply' | null
const pendingAction = ref<PendingAction>(null)

const CONFIRM_COPY: Record<Exclude<PendingAction, null>, string> = {
  discard: '¿Descartar esta propuesta? No se guarda ningún draft ni revisión.',
  regenerate: '¿Regenerar? Se descarta esta propuesta y se inicia un nuevo intento con la misma imagen de referencia.',
  apply: '¿Usar este modelo? Se creará la primera revisión guardada del mob a partir de esta propuesta -- pasará a ser el modelo base real.',
}

function requestAction(action: Exclude<PendingAction, null>): void {
  pendingAction.value = action
}

function cancelPendingAction(): void {
  pendingAction.value = null
}

function confirmPendingAction(): void {
  switch (pendingAction.value) {
    case 'discard':
      emit('discard')
      break
    case 'regenerate':
      emit('regenerate')
      break
    case 'apply':
      emit('apply')
      break
  }
}
</script>

<template>
  <div class="result-step">
    <p v-if="!props.jobId" class="result-step__example-notice">Vista previa con datos de ejemplo -- así se ve esta pantalla con una propuesta real.</p>

    <div class="result-step__header">
      <span class="result-step__badge"><IconCheck :size="18" /></span>
      <div>
        <h2 class="result-step__ready">¡Tu modelo está listo!</h2>
        <h3 class="result-step__name">{{ props.mobName }}</h3>
      </div>
    </div>

    <div class="result-step__body">
      <div class="result-step__panel result-step__panel--preview">
        <GenerationPreviewViewport v-if="props.previewModel" :model="props.previewModel" class="result-step__viewport" />
        <div v-else class="result-step__viewport-placeholder">{{ props.mobName.charAt(0).toUpperCase() }}</div>
      </div>

      <div class="result-step__panel result-step__panel--summary">
        <dl class="result-step__stats">
          <div class="result-step__stat">
            <dt>Cuboides</dt>
            <dd>{{ props.cuboidCount }}</dd>
          </div>
          <div class="result-step__stat">
            <dt>Bones</dt>
            <dd>{{ props.boneCount }}</dd>
          </div>
          <div class="result-step__stat">
            <dt>Textura</dt>
            <dd>{{ props.textureWidth }}×{{ props.textureHeight }}</dd>
          </div>
          <div class="result-step__stat">
            <dt>Compatibilidad FMM</dt>
            <dd class="result-step__fmm" :class="{ 'result-step__fmm--ok': props.fmmCompatible, 'result-step__fmm--error': !props.fmmCompatible }">
              <IconCheck v-if="props.fmmCompatible" :size="14" />
              <IconWarning v-else :size="14" />
              {{ props.fmmCompatible ? 'Compatible' : 'Con problemas' }}
            </dd>
          </div>
        </dl>

        <ul v-if="props.fmmIssues.length > 0" class="result-step__fmm-issues">
          <li v-for="(issue, index) in props.fmmIssues" :key="index" class="result-step__fmm-issue">
            <strong>{{ issue.severity === 'ERROR' ? 'Error' : 'Aviso' }}</strong> ({{ issue.element }}): {{ issue.message }}
          </li>
        </ul>

        <p v-if="props.actionError" class="result-step__action-error">{{ props.actionError }}</p>

        <template v-if="pendingAction">
          <div class="result-step__confirm">
            <p class="result-step__confirm-copy">{{ CONFIRM_COPY[pendingAction] }}</p>
            <div class="result-step__confirm-actions">
              <GButton variant="ghost" :disabled="props.busy" @click="cancelPendingAction">Cancelar</GButton>
              <GButton :variant="pendingAction === 'discard' ? 'danger' : 'primary'" :disabled="props.busy" @click="confirmPendingAction">
                {{ props.busy ? 'Procesando…' : 'Confirmar' }}
              </GButton>
            </div>
          </div>
        </template>
        <div v-else class="result-step__actions">
          <GButton variant="danger" :disabled="props.busy" @click="requestAction('discard')">Descartar</GButton>
          <GButton variant="secondary" :disabled="props.busy" @click="requestAction('regenerate')">Regenerar</GButton>
          <GButton variant="primary" :disabled="props.busy" @click="requestAction('apply')">Usar este modelo</GButton>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.result-step {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
  flex: 1;
  min-height: 0;
}

.result-step__example-notice {
  margin: 0;
  font-size: var(--text-xs);
  color: var(--muted);
  background: var(--surface-2);
  border: var(--border-width) solid var(--border);
  border-radius: var(--radius-md);
  padding: var(--space-2);
  align-self: flex-start;
}

.result-step__header {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.result-step__badge {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  flex-shrink: 0;
  border-radius: 999px;
  background: var(--accent-soft);
  color: var(--accent);
}

.result-step__ready {
  margin: 0;
  color: var(--accent);
  font-size: var(--text-lg);
}

.result-step__name {
  margin: var(--space-1) 0 0;
}

.result-step__body {
  display: flex;
  gap: var(--space-4);
  flex: 1;
  min-height: 0;
  align-items: stretch;
}

.result-step__panel {
  background: var(--panel);
  border: var(--border-width) solid var(--border);
  border-radius: var(--radius-lg);
}

.result-step__panel--preview {
  flex: 1.2;
  min-width: 0;
  min-height: 360px;
  padding: 0;
  overflow: hidden;
  display: flex;
}

.result-step__viewport {
  width: 100%;
  min-height: 360px;
  border: none;
  border-radius: 0;
}

.result-step__viewport-placeholder {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 64px;
  font-weight: 700;
  color: var(--muted);
  background: var(--surface-2);
}

.result-step__panel--summary {
  width: 340px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
  padding: var(--space-5);
}

.result-step__stats {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--space-4);
  margin: 0;
  padding: var(--space-4);
  background: var(--surface);
  border: var(--border-width) solid var(--border);
  border-radius: var(--radius-lg);
}

.result-step__stat dt {
  font-size: var(--text-xs);
  color: var(--muted);
}

.result-step__stat dd {
  margin: var(--space-1) 0 0;
  font-weight: 600;
  font-size: var(--text-md);
}

.result-step__fmm {
  display: flex;
  align-items: center;
  gap: var(--space-1);
  font-size: var(--text-base) !important;
}

.result-step__fmm--ok {
  color: var(--accent);
}

.result-step__fmm--error {
  color: var(--danger);
}

.result-step__fmm-issues {
  margin: 0;
  padding: var(--space-3);
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
  background: var(--surface-2);
  border: var(--border-width) solid var(--border);
  border-radius: var(--radius-md);
  font-size: var(--text-sm);
}

.result-step__action-error {
  margin: 0;
  color: var(--danger);
  font-size: var(--text-sm);
}

.result-step__confirm {
  margin-top: auto;
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  padding: var(--space-3);
  background: var(--accent-soft);
  border: var(--border-width) solid var(--accent);
  border-radius: var(--radius-md);
}

.result-step__confirm-copy {
  margin: 0;
  font-size: var(--text-sm);
}

.result-step__confirm-actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--space-2);
}

.result-step__actions {
  margin-top: auto;
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}
</style>
