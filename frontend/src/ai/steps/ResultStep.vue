<script setup lang="ts">
/**
 * Paso 4 "Resultado" del wizard (ticket 027 shell → ticket 030 real,
 * HU-12, mockup 04) -- muestra la propuesta YA completada y sus 3
 * acciones reales. Deliberadamente NO hace ningún fetch/llamada HTTP acá
 * (mismo criterio que `ConfigurationStep.vue`, 027): recibe todo como
 * props y solo emite intención (`discard`/`regenerate`/`apply`) -- el
 * orquestador (`AiMobWizard.vue`) es quien centraliza los efectos
 * secundarios reales de todo el wizard.
 *
 * `jobId` es la única prop verdaderamente opcional (default `null`): sin
 * ella, el componente asume que lo está montando `/dev/wizard-result-harness`
 * (ticket 027) con datos de ejemplo, y lo dice explícitamente en pantalla
 * -- nunca deja que un dato inventado se confunda con uno real.
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

const props = withDefaults(
  defineProps<{
    jobId?: string | null
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
  apply: '¿Usar este modelo? Se crea la primera revisión guardada del mob a partir de esta propuesta.',
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
    <h2 class="result-step__ready">¡Tu modelo está listo!</h2>
    <h3 class="result-step__name">{{ props.mobName }}</h3>

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
        <dd :class="{ 'result-step__stat-value--ok': props.fmmCompatible, 'result-step__stat-value--error': !props.fmmCompatible }">
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
      <p class="result-step__confirm-copy">{{ CONFIRM_COPY[pendingAction] }}</p>
      <div class="result-step__confirm-actions">
        <button type="button" class="result-step__action" :disabled="props.busy" @click="confirmPendingAction">Confirmar</button>
        <button type="button" class="result-step__action" :disabled="props.busy" @click="cancelPendingAction">Cancelar</button>
      </div>
    </template>
    <div v-else class="result-step__actions">
      <button
        type="button"
        class="result-step__action result-step__action--danger"
        :disabled="props.busy"
        @click="requestAction('discard')"
      >
        Descartar
      </button>
      <button type="button" class="result-step__action" :disabled="props.busy" @click="requestAction('regenerate')">Regenerar</button>
      <button
        type="button"
        class="result-step__action result-step__action--primary"
        :disabled="props.busy"
        @click="requestAction('apply')"
      >
        Usar este modelo
      </button>
    </div>
  </div>
</template>

<style scoped>
.result-step {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  max-width: 360px;
}

.result-step__example-notice {
  margin: 0;
  font-size: var(--text-xs);
  color: var(--muted);
  background: var(--surface-2);
  border: var(--border-width) solid var(--border);
  border-radius: var(--radius-md);
  padding: var(--space-2);
}

.result-step__ready {
  margin: 0;
  color: var(--accent);
}

.result-step__name {
  margin: 0;
}

.result-step__stats {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--space-3);
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
  margin: 0;
  font-weight: 600;
}

.result-step__stat-value--ok {
  color: var(--accent);
}

.result-step__stat-value--error {
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

.result-step__confirm-copy {
  margin: 0;
  font-size: var(--text-sm);
}

.result-step__actions,
.result-step__confirm-actions {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.result-step__action {
  min-height: var(--hit-target-min);
  padding: 0 var(--space-4);
  border-radius: var(--radius-md);
  border: var(--border-width) solid var(--border);
  background: var(--surface);
  color: var(--text);
  font-weight: 600;
}

.result-step__action--primary {
  background: var(--accent);
  border-color: var(--accent);
  color: var(--accent-ink);
}

.result-step__action--danger {
  border-color: var(--danger);
  color: var(--danger);
  background: transparent;
}

.result-step__action:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}
</style>
