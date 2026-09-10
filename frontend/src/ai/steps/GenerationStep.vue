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
 * Ticket 038 (bugfix del progreso IA): 6 etapas reales -- las 4
 * originales más `preparando_resultado`/`validando_geometria`, que el
 * backend ahora emite de verdad ANTES de `completado` (ver
 * `MobGenerationService.runPipeline`). Las otras 2 del mockup
 * ("Preparando UV"/"Generando textura") siguen sin existir a propósito:
 * UV es determinista y la textura pintada es Fase 3, fuera de alcance.
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
 *
 * Ticket 038 -- recuperación tras refresh de página: el `jobId` de una
 * generación en curso se persiste en `sessionStorage` (clave por mob) en
 * cuanto se conoce. Si este componente se vuelve a montar (refresh real
 * del browser, `AiMobWizard.vue` restaura `step='generation'` con el
 * mismo mob) y encuentra un `jobId` pendiente para este `mobId`, NO
 * dispara un `POST /generate` nuevo (evita duplicar el job) -- reconecta
 * directo al stream existente. El backend siempre re-envía el backlog
 * completo desde `seq=1` a una conexión SIN `Last-Event-ID` (`GenerationJobController`),
 * así que reconectar reconstruye el estado real completo (etapa/%/preview)
 * reproduciendo los mismos eventos por el mismo `handleProgressEvent` de
 * siempre -- nunca arranca visualmente desde 0% si el job ya había
 * avanzado de verdad.
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
import { STAGE_ORDER, outcomeForStage, findStageIndex, stageStatusFor, type GenerationOutcome } from '../generationStages'

const props = defineProps<{ mobId: string; projectId: string; mobName: string; baseType: BaseType }>()
const emit = defineEmits<{ 'back-to-project': []; completed: [jobId: string, finalModel: MobProjectModel] }>()

type Outcome = GenerationOutcome

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
/** Ticket 038 -- true mientras el `EventSource` reporta un error de conexión y el navegador todavía no reestableció el stream (reintenta solo, `Last-Event-ID`). */
const reconnecting = ref(false)

let eventSource: EventSource | null = null
const seenSeqs = new Set<number>()

/** Ticket 038 -- una entrada por mob alcanza (un wizard activo por pestaña). */
function jobStorageKey(mobId: string): string {
  return `galgoth:ai-job:${mobId}`
}

function persistJobId(id: string): void {
  try {
    sessionStorage.setItem(jobStorageKey(props.mobId), id)
  } catch {
    // Modo privado/cuota agotada -- nunca bloquea la generación, solo se pierde la recuperación tras refresh.
  }
}

function readPersistedJobId(): string | null {
  try {
    return sessionStorage.getItem(jobStorageKey(props.mobId))
  } catch {
    return null
  }
}

function clearPersistedJobId(): void {
  try {
    sessionStorage.removeItem(jobStorageKey(props.mobId))
  } catch {
    // Ver persistJobId.
  }
}

/** Logging de debug estructurado, SOLO en dev (ticket 038) -- `import.meta.env.DEV` es `false` en cualquier build de producción de Vite, nunca llega a un usuario real. */
function debugLog(message: string): void {
  if (import.meta.env.DEV) {
    console.log(message)
  }
}

const currentStageIndex = computed(() => findStageIndex(currentPipelineStage.value))
const hasGeometry = computed(() => previewModel.value.cuboids.length > 0)
const currentStageHint = computed(() => STAGE_ORDER[currentStageIndex.value]?.hint ?? null)

function stageStatus(index: number): 'done' | 'current' | 'pending' {
  return stageStatusFor(index, currentStageIndex.value, outcome.value)
}

function handleProgressEvent(raw: MessageEvent): void {
  const event = JSON.parse(raw.data) as GenerationEvent
  debugLog(`[AI JOB EVENT] seq=${event.seq} stage=${event.stage} progress=${event.progressPct}`)
  reconnecting.value = false // cualquier evento real (backlog o en vivo) confirma que el stream está sano de nuevo.
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
    clearPersistedJobId() // el job llegó a un estado terminal -- ya no hay nada que resumir tras un refresh.
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

function openEventStream(id: string): void {
  eventSource = new EventSource(eventsUrl(id))
  eventSource.addEventListener('progress', handleProgressEvent)
  eventSource.onerror = () => {
    // El navegador reintenta solo (Last-Event-ID, ver GenerationJobController)
    // -- un error de red transitorio no es un fallo del job. Mientras el
    // job sigue "running" y el stream no reestableció, se lo comunica al
    // usuario en vez de dejar la pantalla congelada en silencio sin
    // ninguna señal (el propio bug original de este ticket).
    if (outcome.value === 'running') {
      reconnecting.value = true
    }
  }
}

async function beginGeneration(): Promise<void> {
  startError.value = null
  const persistedJobId = readPersistedJobId()
  if (persistedJobId) {
    // Ticket 038 -- refresh de página con un job ya en curso para este mob:
    // reconectar en vez de arrancar un `POST /generate` nuevo (evitaría
    // duplicar el job real). El backlog completo llega igual por la
    // reconexión (ver el comentario de cabecera de este archivo).
    debugLog(`[AI JOB] connected jobId=${persistedJobId} (resumido tras refresh)`)
    jobId.value = persistedJobId
    openEventStream(persistedJobId)
    return
  }
  try {
    const response = await startGeneration(props.mobId)
    jobId.value = response.jobId
    persistJobId(response.jobId)
    debugLog(`[AI JOB] connected jobId=${response.jobId}`)
    openEventStream(response.jobId)
  } catch (error) {
    startError.value = error instanceof ApiError ? error.message : 'No se pudo iniciar la generación.'
  }
}

/** Ticket 038 -- estado de error explícito (AC del bugfix): reintentar dispara una generación COMPLETAMENTE nueva (el job fallido nunca se reutiliza), reseteando el estado local completo primero. */
async function retryGeneration(): Promise<void> {
  closeStream()
  clearPersistedJobId()
  seenSeqs.clear()
  jobId.value = null
  currentPipelineStage.value = STAGE_ORDER[0].key
  currentMessage.value = null
  progressPct.value = 0
  outcome.value = 'running'
  failureMessage.value = null
  previewModel.value = emptyPreviewModel(props.mobId, props.projectId, props.mobName, props.baseType)
  await beginGeneration()
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
          <p v-if="outcome === 'running' && currentStageHint" class="generation-step__hint">{{ currentStageHint }}</p>
        </div>

        <p v-if="outcome === 'running' && reconnecting" class="generation-step__notice">
          <span class="generation-step__spinner" /> Reconectando al proceso…
        </p>
        <p v-if="outcome === 'completed'" class="generation-step__notice generation-step__notice--ok">
          <IconCheck :size="16" /> Generación completada.
        </p>
        <div v-else-if="outcome === 'failed'" class="generation-step__failed">
          <p class="generation-step__notice generation-step__notice--error">
            <IconWarning :size="16" /> La generación falló: {{ failureMessage }}
          </p>
          <div class="generation-step__failed-actions">
            <GButton variant="primary" @click="retryGeneration">Reintentar</GButton>
            <GButton variant="secondary" @click="$emit('back-to-project')">Volver a configuración</GButton>
          </div>
        </div>
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

        <GButton v-if="outcome === 'cancelled'" variant="secondary" @click="$emit('back-to-project')"> Ir al proyecto </GButton>
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

.generation-step__hint {
  margin: 0;
  color: var(--muted);
  font-size: var(--text-xs);
  font-style: italic;
}

.generation-step__failed {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.generation-step__failed-actions {
  display: flex;
  gap: var(--space-2);
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
