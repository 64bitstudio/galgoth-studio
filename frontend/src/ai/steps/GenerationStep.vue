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
 * Al completar, esta pantalla NO navega sola al paso "Resultado" --
 * conectar esa transición con la propuesta real es alcance del ticket
 * 030 (mismo límite honesto ya establecido en 027/028: `ResultStep.vue`
 * sigue siendo un shell hasta ese ticket).
 */
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import type { BaseType } from '../../projects/mobsApi'
import type { MobProjectModel } from '../../domain/MobProjectModel'
import { cancelGeneration, eventsUrl, startGeneration } from '../../api/generationApi'
import { emptyPreviewModel, applyPreviewDelta, type GenerationEvent } from '../generationEvents'
import { ApiError } from '../../api/ApiError'
import GButton from '../../design-system/components/GButton.vue'
import GenerationPreviewViewport from '../GenerationPreviewViewport.vue'

const props = defineProps<{ mobId: string; projectId: string; mobName: string; baseType: BaseType }>()
defineEmits<{ 'back-to-project': [] }>()

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
    <h2 class="generation-step__title">Generación</h2>

    <p v-if="startError" class="generation-step__error">{{ startError }}</p>

    <template v-else>
      <ul class="generation-step__stages">
        <li
          v-for="(item, index) in STAGE_ORDER"
          :key="item.key"
          class="generation-step__stage"
          :class="`generation-step__stage--${stageStatus(index)}`"
        >
          <span class="generation-step__stage-marker" aria-hidden="true">{{ stageStatus(index) === 'done' ? '●' : '○' }}</span>
          {{ item.label }}
        </li>
      </ul>

      <p v-if="currentMessage" class="generation-step__message">{{ currentMessage }}</p>
      <progress class="generation-step__progress" :value="progressPct" max="100">{{ progressPct }}%</progress>

      <GenerationPreviewViewport :model="previewModel" />

      <p v-if="outcome === 'completed'" class="generation-step__notice generation-step__notice--ok">
        Generación completada -- la pantalla de Resultado con esta propuesta real llega en el ticket 030.
      </p>
      <p v-else-if="outcome === 'failed'" class="generation-step__notice generation-step__notice--error">
        La generación falló: {{ failureMessage }}
      </p>
      <p v-else-if="outcome === 'cancelled'" class="generation-step__notice">Generación cancelada -- no se guardó ningún resultado.</p>

      <div v-if="outcome === 'running' && !cancelRequested" class="generation-step__actions">
        <GButton variant="danger" @click="requestCancel">Cancelar</GButton>
      </div>
      <div v-else-if="cancelRequested" class="generation-step__cancel-confirm">
        <p>¿Cancelar la generación en curso? El progreso hecho hasta ahora se descarta.</p>
        <GButton variant="danger" :disabled="cancelling" @click="confirmCancel">Sí, cancelar</GButton>
        <GButton variant="ghost" :disabled="cancelling" @click="abortCancel">Seguir esperando</GButton>
      </div>
    </template>

    <button
      v-if="outcome !== 'running' || startError"
      type="button"
      class="generation-step__back"
      @click="$emit('back-to-project')"
    >
      Ir al proyecto
    </button>
  </div>
</template>

<style scoped>
.generation-step {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
  max-width: 480px;
}

.generation-step__title {
  margin: 0;
}

.generation-step__error {
  color: var(--danger);
}

.generation-step__stages {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.generation-step__stage {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  color: var(--muted);
}

.generation-step__stage--current {
  color: var(--text);
  font-weight: 600;
}

.generation-step__stage--done {
  color: var(--accent);
}

.generation-step__stage-marker {
  font-size: var(--text-base);
}

.generation-step__message {
  margin: 0;
  color: var(--muted);
  font-size: var(--text-sm);
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

.generation-step__notice {
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
}

.generation-step__notice--error {
  color: var(--danger);
}

.generation-step__actions,
.generation-step__cancel-confirm {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: var(--space-2);
}

.generation-step__back {
  align-self: flex-start;
  min-height: var(--hit-target-min);
  padding: 0 var(--space-4);
  background: var(--accent);
  color: var(--accent-ink);
  border: none;
  border-radius: var(--radius-md);
  font-weight: 600;
  cursor: pointer;
}
</style>
