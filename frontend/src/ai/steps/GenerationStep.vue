<script setup lang="ts">
/**
 * Paso 3 "Generación" del wizard (ticket 027 shell → ticket 029 real,
 * HU-11, mockup 03) -- dispara `POST /api/mobs/{mobId}/generate` y
 * escucha `GET /api/jobs/{jobId}/events` (SSE) en vivo, renderizando el
 * modelo emergente en {@link GenerationPreviewViewport} a medida que
 * llegan eventos `preview_operations`. El modelo de preview vive
 * SOLO en memoria de este componente -- se descarta al desmontar, nunca
 * toca `useDraftModelStore`/`mob_drafts`/`mob_revisions` (AC #2).
 *
 * Solo 4 etapas reales (a diferencia de las 6 del mockup -- ver la nota
 * en `GenerationStage.java` del backend: "Preparando UV"/"Generando
 * textura" no existen este ciclo, UV es determinista y la textura
 * pintada es Fase 3).
 *
 * Al completar (ticket 030), emite `completed` con el `jobId` real --
 * `AiMobWizard.vue` es quien busca el resultado (`GET /api/jobs/{jobId}/result`)
 * y avanza al paso "Resultado"; este componente nunca hace esa llamada
 * ni navega por su cuenta.
 *
 * Ticket 037 (corrección de UX del flujo IA): `completed` también manda
 * el `previewModel` final (el mismo que ya se construyó en memoria vía
 * SSE, nunca un dato nuevo del backend) para que "Resultado" pueda
 * mostrar el modelo real en vez de solo números -- puramente frontend,
 * sin tocar ningún contrato/endpoint.
 */
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import type { BaseType } from '../../projects/mobsApi'
import type { MobProjectModel } from '../../domain/MobProjectModel'
import { cancelGeneration, eventsUrl, startGeneration } from '../../api/generationApi'
import { emptyPreviewModel, applyPreviewDelta, type GenerationEvent } from '../generationEvents'
import { ApiError } from '../../api/ApiError'
import GButton from '../../design-system/components/GButton.vue'
import IconCheck from '../../design-system/icons/IconCheck.vue'
import IconWarning from '../../design-system/icons/IconWarning.vue'
import GenerationPreviewViewport from '../GenerationPreviewViewport.vue'

const props = defineProps<{ mobId: string; projectId: string; mobName: string; baseType: BaseType }>()
const emit = defineEmits<{ 'back-to-project': []; completed: [jobId: string, finalModel: MobProjectModel] }>()

const STAGE_ORDER = [
  { key: 'analizando_referencia', label: 'Analizando referencia…' },
  { key: 'detectando_silueta', label: 'Detectando silueta…' },
  { key: 'creando_rig', label: 'Creando rig…' },
  { key: 'generando_cuboides', label: 'Generando cuboides…' },
] as const

type Outcome = 'running' | 'completed' | 'failed' | 'cancelled'

const jobId = ref<string | null>(null)
const currentPipelineStage = ref<string>(STAGE_ORDER[0].key)
const currentMessage = ref<string | null>(null)
const progressPct = ref(0)
const outcome = ref<Outcome>('running')
const failureMessage = ref<string | null>(null)
const startError = ref<string | null>(null)
const previewModel = ref<MobProjectModel>(emptyPreviewModel(props.mobId, props.projectId, props.mobName, props.baseType))
const cancelRequested = ref(false)
const cancelling = ref(false)

let eventSource: EventSource | null = null
const seenSeqs = new Set<number>()

const currentStageIndex = computed(() => STAGE_ORDER.findIndex((s) => s.key === currentPipelineStage.value))
const hasGeometry = computed(() => previewModel.value.cuboids.length > 0)

function stageStatus(index: number): 'done' | 'current' | 'pending' {
  if (outcome.value !== 'running') {
    return index <= currentStageIndex.value ? 'done' : 'pending'
  }
  if (index < currentStageIndex.value) {
    return 'done'
  }
  return index === currentStageIndex.value ? 'current' : 'pending'
}

/** `null` mientras la etapa no es una de las 3 terminales -- evita un ternario anidado (Sonar S3358) al traducir `stage` a `Outcome`. */
function outcomeForStage(stage: string): Outcome | null {
  if (stage === 'completado') return 'completed'
  if (stage === 'fallido') return 'failed'
  if (stage === 'cancelado') return 'cancelled'
  return null
}

function handleProgressEvent(raw: MessageEvent): void {
  const event = JSON.parse(raw.data) as GenerationEvent
  if (seenSeqs.has(event.seq)) {
    return // reconexión con solape de backlog/en-vivo, ver GenerationJobController -- idempotente
  }
  seenSeqs.add(event.seq)

  currentMessage.value = event.message
  if (event.progressPct !== null) {
    progressPct.value = event.progressPct
  }

  if (event.payload?.type === 'preview_operations') {
    previewModel.value = applyPreviewDelta(previewModel.value, event.payload)
  } else if (event.payload?.type === 'preview_snapshot') {
    previewModel.value = event.payload.model
  }

  const terminalOutcome = outcomeForStage(event.stage)
  if (terminalOutcome) {
    outcome.value = terminalOutcome
    if (terminalOutcome === 'failed') {
      failureMessage.value = event.message
    }
    closeStream()
    if (terminalOutcome === 'completed' && jobId.value) {
      emit('completed', jobId.value, previewModel.value)
    }
    return
  }
  currentPipelineStage.value = event.stage
}

function closeStream(): void {
  eventSource?.close()
  eventSource = null
}

async function beginGeneration(): Promise<void> {
  try {
    const response = await startGeneration(props.mobId)
    jobId.value = response.jobId
    eventSource = new EventSource(eventsUrl(response.jobId))
    eventSource.addEventListener('progress', handleProgressEvent)
    eventSource.onerror = () => {
      // El navegador reintenta solo (Last-Event-ID) -- un error de red
      // transitorio no es un fallo del job, solo se refleja si el propio
      // stream nunca vuelve a avanzar (sin timeout artificial acá).
    }
  } catch (error) {
    startError.value = error instanceof ApiError ? error.message : 'No se pudo iniciar la generación.'
  }
}

function requestCancel(): void {
  cancelRequested.value = true
}

function abortCancel(): void {
  cancelRequested.value = false
}

async function confirmCancel(): Promise<void> {
  if (!jobId.value) {
    return
  }
  cancelling.value = true
  try {
    await cancelGeneration(jobId.value)
  } catch {
    // El evento `cancelado` (o el estado real del job) es la fuente de
    // verdad -- un fallo de red en ESTA llamada no bloquea la UI, el
    // usuario puede reintentar el botón.
  } finally {
    cancelling.value = false
    cancelRequested.value = false
  }
}

onMounted(beginGeneration)
onBeforeUnmount(closeStream)
</script>

<template>
  <div class="generation-step">
    <div class="generation-step__header">
      <h2 class="generation-step__title">Generando {{ mobName }}</h2>
      <p class="generation-step__subtitle">La IA está construyendo tu modelo paso a paso.</p>
    </div>

    <div v-if="startError" class="generation-step__start-error">
      <p class="generation-step__error"><IconWarning :size="16" /> {{ startError }}</p>
      <GButton variant="secondary" @click="$emit('back-to-project')">Ir al proyecto</GButton>
    </div>

    <div v-else class="generation-step__body">
      <div class="generation-step__panel generation-step__panel--stages">
        <ul class="generation-step__stages">
          <li
            v-for="(item, index) in STAGE_ORDER"
            :key="item.key"
            class="generation-step__stage"
            :class="`generation-step__stage--${stageStatus(index)}`"
          >
            <span class="generation-step__stage-marker" aria-hidden="true">
              <IconCheck v-if="stageStatus(index) === 'done'" :size="14" />
              <span v-else-if="stageStatus(index) === 'current'" class="generation-step__spinner" />
            </span>
            {{ item.label }}
          </li>
        </ul>

        <div class="generation-step__progress-block">
          <div class="generation-step__progress-row">
            <span>Progreso</span>
            <span class="generation-step__progress-pct">{{ progressPct }}%</span>
          </div>
          <progress class="generation-step__progress" :value="progressPct" max="100">{{ progressPct }}%</progress>
          <p v-if="currentMessage" class="generation-step__message">{{ currentMessage }}</p>
        </div>

        <p v-if="outcome === 'completed'" class="generation-step__notice generation-step__notice--ok">
          <IconCheck :size="16" /> Generación completada.
        </p>
        <p v-else-if="outcome === 'failed'" class="generation-step__notice generation-step__notice--error">
          <IconWarning :size="16" /> La generación falló: {{ failureMessage }}
        </p>
        <p v-else-if="outcome === 'cancelled'" class="generation-step__notice">Generación cancelada -- no se guardó ningún resultado.</p>

        <div v-if="outcome === 'running' && !cancelRequested" class="generation-step__actions">
          <GButton variant="danger" @click="requestCancel">Cancelar</GButton>
        </div>
        <div v-else-if="cancelRequested" class="generation-step__cancel-confirm">
          <p>¿Cancelar la generación en curso? El progreso hecho hasta ahora se descarta.</p>
          <div class="generation-step__cancel-confirm-actions">
            <GButton variant="danger" :disabled="cancelling" @click="confirmCancel">Sí, cancelar</GButton>
            <GButton variant="ghost" :disabled="cancelling" @click="abortCancel">Seguir esperando</GButton>
          </div>
        </div>

        <GButton
          v-if="outcome === 'failed' || outcome === 'cancelled'"
          variant="secondary"
          @click="$emit('back-to-project')"
        >
          Ir al proyecto
        </GButton>
      </div>

      <div class="generation-step__panel generation-step__panel--preview">
        <GenerationPreviewViewport :model="previewModel" class="generation-step__viewport" />
        <div v-if="!hasGeometry && outcome === 'running'" class="generation-step__preview-overlay">
          <span class="generation-step__spinner generation-step__spinner--lg" />
          <p>Esperando la primera geometría…</p>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.generation-step {
  display: flex;
  flex-direction: column;
  gap: var(--space-5);
  flex: 1;
  min-height: 0;
}

.generation-step__header {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
}

.generation-step__title {
  margin: 0;
}

.generation-step__subtitle {
  margin: 0;
  color: var(--muted);
}

.generation-step__start-error {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: var(--space-3);
}

.generation-step__error {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  margin: 0;
  color: var(--danger);
}

.generation-step__body {
  display: flex;
  gap: var(--space-4);
  flex: 1;
  min-height: 0;
  align-items: stretch;
}

.generation-step__panel {
  background: var(--panel);
  border: var(--border-width) solid var(--border);
  border-radius: var(--radius-lg);
  padding: var(--space-5);
}

.generation-step__panel--stages {
  width: 320px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.generation-step__panel--preview {
  position: relative;
  flex: 1;
  min-width: 0;
  padding: 0;
  overflow: hidden;
}

.generation-step__viewport {
  width: 100%;
  height: 100%;
  min-height: 420px;
  border: none;
  border-radius: 0;
}

.generation-step__preview-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--space-3);
  color: var(--muted);
  font-size: var(--text-sm);
  pointer-events: none;
}

.generation-step__stages {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
}

.generation-step__stage {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  min-height: 32px;
  color: var(--muted);
  font-size: var(--text-sm);
}

.generation-step__stage--current {
  color: var(--text);
  font-weight: 600;
}

.generation-step__stage--done {
  color: var(--accent);
}

.generation-step__stage-marker {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  flex-shrink: 0;
  border-radius: 999px;
  border: var(--border-width) solid var(--border);
}

.generation-step__stage--done .generation-step__stage-marker {
  border-color: var(--accent);
  background: var(--accent-soft);
  color: var(--accent);
}

.generation-step__stage--current .generation-step__stage-marker {
  border-color: var(--accent);
}

/* Spinner puramente CSS -- un anillo con un segmento hueco que gira, sin
   ninguna librería. `prefers-reduced-motion` ya lo neutraliza vía la
   regla global de reset.css (duración de animación forzada a 0.01ms). */
.generation-step__spinner {
  width: 12px;
  height: 12px;
  border-radius: 999px;
  border: 2px solid var(--accent-soft);
  border-top-color: var(--accent);
  animation: generation-step-spin 0.8s linear infinite;
}

.generation-step__spinner--lg {
  width: 28px;
  height: 28px;
  border-width: 3px;
}

@keyframes generation-step-spin {
  to {
    transform: rotate(360deg);
  }
}

.generation-step__progress-block {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.generation-step__progress-row {
  display: flex;
  justify-content: space-between;
  font-size: var(--text-sm);
  color: var(--muted);
}

.generation-step__progress-pct {
  font-variant-numeric: tabular-nums;
  color: var(--text);
  font-weight: 600;
}

/* `<progress>` nativo (Sonar S6819 -- accesible en todos los dispositivos sin reimplementar la semántica a mano con role="progressbar"). Estilos por pseudo-elemento porque el navegador no expone su barra de relleno como CSS normal. */
.generation-step__progress {
  appearance: none;
  width: 100%;
  height: 8px;
  border: none;
  border-radius: var(--radius-md);
  overflow: hidden;
}

.generation-step__progress::-webkit-progress-bar {
  background: var(--surface-2);
  border-radius: var(--radius-md);
}

.generation-step__progress::-webkit-progress-value {
  background: var(--accent);
  border-radius: var(--radius-md);
  transition: width 0.2s ease;
}

.generation-step__progress::-moz-progress-bar {
  background: var(--accent);
  border-radius: var(--radius-md);
  transition: width 0.2s ease;
}

.generation-step__message {
  margin: 0;
  color: var(--muted);
  font-size: var(--text-sm);
}

.generation-step__notice {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  margin: 0;
  color: var(--muted);
  font-size: var(--text-sm);
  background: var(--surface-2);
  border: var(--border-width) solid var(--border);
  border-radius: var(--radius-md);
  padding: var(--space-3);
}

.generation-step__notice--ok {
  color: var(--accent);
  background: var(--accent-soft);
  border-color: transparent;
}

.generation-step__notice--error {
  color: var(--danger);
  background: var(--danger-soft);
  border-color: transparent;
}

.generation-step__actions,
.generation-step__cancel-confirm {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: var(--space-2);
  margin-top: auto;
  padding-top: var(--space-3);
}

.generation-step__cancel-confirm p {
  margin: 0;
  font-size: var(--text-sm);
  color: var(--muted);
}

.generation-step__cancel-confirm-actions {
  display: flex;
  gap: var(--space-2);
}
</style>
