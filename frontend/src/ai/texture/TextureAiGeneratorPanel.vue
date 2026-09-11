<script setup lang="ts">
/**
 * "Generador de textura (IA)" (ticket 055, HU-36/HU-37/HU-38/HU-42,
 * mockup 08) -- pieza embebible dentro de `GDrawer.vue`, compartida con
 * `AiEditPanel.vue` (Modelo) desde `MobEditor.vue` (ticket 069, VoBo del
 * PO sobre el mockup https://claude.ai/code/artifact/a3514398-4fc4-430f-96b2-40299c0038a4).
 * Reemplaza a `TextureAiGeneratorScreen.vue` (ruta propia
 * `/projects/:projectId/mobs/:mobId/texture/generate-ai`, RETIRADA en
 * este ticket -- el drawer la sustituye por completo, sin dejarla como
 * fallback).
 *
 * Mismo pipeline de 054 (`TextureGenerationController`), MISMA lógica de
 * negocio y streaming SSE que la pantalla original -- lo único que
 * cambió es el CONTENEDOR: de una pantalla de ruta con GSidebar propio y
 * layout de 2 columnas (~900px), a un panel de 1 sola columna embebido
 * en el drawer (420px), reordenado como un formulario: Referencia ->
 * Estilo -> Detalle -> Parte a generar -> Generar. Ya no hace falta
 * `getMob`/`getDraft` (el mob ya está cargado por `MobEditor.vue` --
 * llegan acá como props `mobId`/`model`, sin duplicar el fetch), ni
 * `loading`/`notFound`/`loadError` de pantalla completa (el drawer solo
 * se abre cuando `MobEditor.vue` ya tiene el draft cargado). El único
 * fetch propio que sigue haciendo falta es `listReferenceImages` (no
 * viaja como parte del draft).
 *
 * `apply()` ya NO navega (no hay a dónde -- esto no es una ruta): emite
 * `applied` para que `MobEditor.vue` recargue el atlas ya persistido en
 * `TextureCanvas.vue` (`reloadAtlas`, ver ese componente) y vuelve al
 * formulario (mismo criterio que `AiEditPanel.vue`: el drawer se queda
 * abierto, listo para una nueva generación, en vez de cerrarse solo).
 */
import { computed, nextTick, onBeforeUnmount, ref, type Component } from 'vue'
import GButton from '../../design-system/components/GButton.vue'
import GSelect, { type GSelectOption } from '../../design-system/components/GSelect.vue'
import IconCheck from '../../design-system/icons/IconCheck.vue'
import IconCuboid from '../../design-system/icons/IconCuboid.vue'
import IconEye from '../../design-system/icons/IconEye.vue'
import IconMosaic from '../../design-system/icons/IconMosaic.vue'
import IconTarget from '../../design-system/icons/IconTarget.vue'
import IconUpload from '../../design-system/icons/IconUpload.vue'
import IconWarning from '../../design-system/icons/IconWarning.vue'
import IconSparkle from '../../design-system/icons/IconSparkle.vue'
import {
  listReferenceImages,
  referenceImageUrl,
  uploadReferenceImage,
  MAX_REFERENCE_IMAGE_BYTES,
  SUPPORTED_REFERENCE_IMAGE_TYPES,
} from '../../api/referenceImagesApi'
import {
  applyTexture,
  getTextureResult,
  startTextureGeneration,
  type TextureDetailLevelValue,
  type TextureGenerationResult,
  type TextureStyleValue,
} from '../../api/textureGenerationApi'
import { eventsUrl } from '../../api/generationApi'
import { ApiError } from '../../api/ApiError'
import type { MobProjectModel } from '../../domain/MobProjectModel'
import type { TextureGenerationEvent } from './textureGenerationEvents'
import { decodeTexturePreviewPatch } from './texturePatchDecode'
import {
  TEXTURE_STAGE_ORDER,
  findTextureStageIndex,
  outcomeForTextureStage,
  textureStageStatusFor,
  type TextureGenerationOutcome,
} from './textureGenerationStages'

const props = defineProps<{ mobId: string; model: MobProjectModel }>()
const emit = defineEmits<{ applied: [] }>()

/** Ticket 067 -- VoBo del PO sobre preview interactivo: radio buttons personalizados con ícono, nunca `<input type="radio">` nativo. */
const STYLE_OPTIONS: Array<{ value: TextureStyleValue; label: string; icon: Component }> = [
  { value: 'faithful', label: 'Fiel a la referencia', icon: IconTarget },
  { value: 'minecraft_vanilla', label: 'Minecraft Vanilla', icon: IconCuboid },
  { value: 'pixel_art', label: 'Pixel Art', icon: IconMosaic },
  { value: 'realistic', label: 'Realista', icon: IconEye },
]

const DETAIL_LEVELS: readonly TextureDetailLevelValue[] = ['low', 'medium', 'high']
/** Ticket 067 -- control segmentado de 3 opciones en vez de un slider nativo (mismo `role="radiogroup"` que `STYLE_OPTIONS`). */
const DETAIL_LABELS: readonly string[] = ['Bajo', 'Medio', 'Alto']

/** Ticket 067 -- valor centinela para "Modelo completo" en el `GSelect` de "Parte a generar" (su `modelValue` es siempre `string`, nunca `null`) -- se traduce a/desde `targetBoneId` (`string | null`) en `selectedBoneValue`. */
const WHOLE_MODEL_VALUE = ''

interface BoneOption {
  id: string
  name: string
}

type Phase = 'form' | 'generating' | 'result'

// ---- referencia (único fetch propio que sigue haciendo falta -- el resto llega vía props) ----
const referenceUrl = ref<string | null>(null)
const hasReferenceImage = ref(false)
const referenceLoadError = ref<string | null>(null)

const textureWidth = computed(() => props.model.texture.width)
const textureHeight = computed(() => props.model.texture.height)
const boneOptions = computed<BoneOption[]>(() =>
  props.model.bones
    .filter((bone) => props.model.cuboids.some((cuboid) => cuboid.boneId === bone.id))
    .map((bone) => ({ id: bone.id, name: bone.name })),
)

// ---- formulario ----
const style = ref<TextureStyleValue>('faithful')
const detailIndex = ref(1)
const detailLevel = computed<TextureDetailLevelValue>(() => DETAIL_LEVELS[detailIndex.value]!)
const targetBoneId = ref<string | null>(null)
const startError = ref<string | null>(null)

const boneSelectOptions = computed<GSelectOption[]>(() => [
  { value: WHOLE_MODEL_VALUE, label: 'Modelo completo' },
  ...boneOptions.value.map((bone) => ({ value: bone.id, label: bone.name })),
])
const selectedBoneValue = computed<string>({
  get: () => targetBoneId.value ?? WHOLE_MODEL_VALUE,
  set: (value) => {
    targetBoneId.value = value === WHOLE_MODEL_VALUE ? null : value
  },
})

// ---- reemplazo de la imagen de referencia (ticket 067, VoBo del PO) ----
const replacingReference = ref(false)
const replaceReferenceError = ref<string | null>(null)
const referenceFileInputEl = ref<HTMLInputElement>()

function openReferenceFilePicker(): void {
  referenceFileInputEl.value?.click()
}

/** Mismos límites/mensajes que `ReferenceStep.vue` (027) -- valida client-side antes de subir. */
async function handleReferenceFileChange(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0] ?? null
  input.value = '' // permite re-elegir el mismo archivo dos veces seguidas.
  if (!file) {
    return
  }
  if (!SUPPORTED_REFERENCE_IMAGE_TYPES.includes(file.type)) {
    replaceReferenceError.value = `Formato no soportado: '${file.type || 'desconocido'}' -- solo se aceptan PNG o JPEG.`
    return
  }
  if (file.size > MAX_REFERENCE_IMAGE_BYTES) {
    replaceReferenceError.value = `La imagen pesa ${(file.size / (1024 * 1024)).toFixed(1)}MB -- el máximo soportado es 10MB.`
    return
  }
  replaceReferenceError.value = null
  replacingReference.value = true
  try {
    const summary = await uploadReferenceImage(props.mobId, file)
    referenceUrl.value = referenceImageUrl(summary.url)
    hasReferenceImage.value = true
  } catch (error) {
    replaceReferenceError.value = error instanceof ApiError ? error.message : 'No se pudo subir la nueva imagen de referencia.'
  } finally {
    replacingReference.value = false
  }
}

// ---- radiogroups personalizados (Estilo/Detalle, ticket 067) ----
function handleRadioGroupKeydown(event: KeyboardEvent, currentIndex: number, count: number, selectIndex: (index: number) => void, buttonRefs: HTMLButtonElement[]): void {
  if (!['ArrowRight', 'ArrowDown', 'ArrowLeft', 'ArrowUp'].includes(event.key)) {
    return
  }
  event.preventDefault()
  const delta = event.key === 'ArrowRight' || event.key === 'ArrowDown' ? 1 : -1
  const nextIndex = (currentIndex + delta + count) % count
  selectIndex(nextIndex)
  void nextTick(() => buttonRefs[nextIndex]?.focus())
}

const styleButtonRefs = ref<HTMLButtonElement[]>([])
function setStyleButtonRef(el: Element | null, index: number): void {
  if (el) {
    styleButtonRefs.value[index] = el as HTMLButtonElement
  }
}
function handleStyleKeydown(event: KeyboardEvent, index: number): void {
  handleRadioGroupKeydown(event, index, STYLE_OPTIONS.length, (next) => (style.value = STYLE_OPTIONS[next]!.value), styleButtonRefs.value)
}

const detailButtonRefs = ref<HTMLButtonElement[]>([])
function setDetailButtonRef(el: Element | null, index: number): void {
  if (el) {
    detailButtonRefs.value[index] = el as HTMLButtonElement
  }
}
function handleDetailKeydown(event: KeyboardEvent, index: number): void {
  handleRadioGroupKeydown(event, index, DETAIL_LABELS.length, (next) => (detailIndex.value = next), detailButtonRefs.value)
}

// ---- progreso ----
const phase = ref<Phase>('form')
const jobId = ref<string | null>(null)
const currentStage = ref<string>(TEXTURE_STAGE_ORDER[0]!.key)
const currentMessage = ref<string | null>(null)
const progressPct = ref(0)
const outcome = ref<TextureGenerationOutcome>('running')
const failureMessage = ref<string | null>(null)
const currentStageIndex = computed(() => findTextureStageIndex(currentStage.value))

function stageStatus(index: number): 'done' | 'current' | 'pending' {
  return textureStageStatusFor(index, currentStageIndex.value, outcome.value)
}

let eventSource: EventSource | null = null
const seenSeqs = new Set<number>()

const canvasEl = ref<HTMLCanvasElement | null>(null)
let canvasCtx: CanvasRenderingContext2D | null = null

// ---- resultado (diff Antes/Después, HU-38) ----
const result = ref<TextureGenerationResult | null>(null)
const resultLoading = ref(false)
const resultFetchError = ref<string | null>(null)
const showingAfter = ref(true)
const applying = ref(false)
const applyError = ref<string | null>(null)
const staleBase = ref(false)

const resultImageSrc = computed(() => {
  if (!result.value) {
    return ''
  }
  const base64 = showingAfter.value ? result.value.afterAtlasPngBase64 : result.value.beforeAtlasPngBase64
  return `data:image/png;base64,${base64}`
})

const resultAltText = computed(() =>
  showingAfter.value ? 'Atlas de textura después de la generación por IA' : 'Atlas de textura antes de la generación por IA',
)

const canSubmit = computed(() => hasReferenceImage.value)

void loadReferenceImages()
onBeforeUnmount(closeStream)

async function loadReferenceImages(): Promise<void> {
  referenceLoadError.value = null
  try {
    const references = await listReferenceImages(props.mobId)
    if (references.length > 0) {
      hasReferenceImage.value = true
      referenceUrl.value = referenceImageUrl(references[references.length - 1]!.url)
    }
  } catch (error) {
    referenceLoadError.value = error instanceof ApiError ? error.message : 'No se pudo cargar la imagen de referencia de este mob.'
  }
}

function resetGenerationState(): void {
  phase.value = 'form'
  jobId.value = null
  result.value = null
  applyError.value = null
  staleBase.value = false
}

async function submitGeneration(): Promise<void> {
  startError.value = null
  staleBase.value = false
  seenSeqs.clear()
  currentStage.value = TEXTURE_STAGE_ORDER[0]!.key
  currentMessage.value = null
  progressPct.value = 0
  outcome.value = 'running'
  failureMessage.value = null
  phase.value = 'generating'
  result.value = null

  await nextTick()
  canvasCtx = canvasEl.value?.getContext('2d') ?? null
  if (canvasEl.value) {
    canvasEl.value.width = textureWidth.value
    canvasEl.value.height = textureHeight.value
  }
  canvasCtx?.clearRect(0, 0, textureWidth.value, textureHeight.value)

  try {
    const response = await startTextureGeneration(props.mobId, { style: style.value, detailLevel: detailLevel.value, boneId: targetBoneId.value })
    jobId.value = response.jobId
    openEventStream(response.jobId)
  } catch (error) {
    phase.value = 'form'
    startError.value = error instanceof ApiError ? error.message : 'No se pudo iniciar la generación de textura.'
  }
}

function openEventStream(id: string): void {
  eventSource = new EventSource(eventsUrl(id))
  eventSource.addEventListener('progress', handleProgressEvent)
}

function closeStream(): void {
  eventSource?.close()
  eventSource = null
}

function handleProgressEvent(raw: MessageEvent): void {
  const event = JSON.parse(raw.data) as TextureGenerationEvent
  if (seenSeqs.has(event.seq)) {
    return // reconexión con solape de backlog/en-vivo -- idempotente.
  }
  seenSeqs.add(event.seq)

  currentMessage.value = event.message
  if (event.progressPct !== null) {
    progressPct.value = event.progressPct
  }

  if (event.payload?.type === 'preview_texture_patch') {
    void drawPatch(event.payload)
  }

  const terminalOutcome = outcomeForTextureStage(event.stage)
  if (terminalOutcome) {
    outcome.value = terminalOutcome
    closeStream()
    if (terminalOutcome === 'failed') {
      failureMessage.value = event.message
      return
    }
    if (jobId.value) {
      void fetchResult(jobId.value)
    }
    return
  }
  currentStage.value = event.stage
}

async function drawPatch(payload: TextureGenerationEvent['payload']): Promise<void> {
  if (!payload || !canvasCtx) {
    return
  }
  try {
    const bitmap = await decodeTexturePreviewPatch(payload)
    try {
      canvasCtx.drawImage(bitmap, payload.rect.x, payload.rect.y)
    } finally {
      bitmap.close()
    }
  } catch {
    // Un parche de preview que no se pudo decodificar no debe romper el
    // flujo de progreso -- el resultado real y autoritativo es el diff
    // Antes/Después (HU-38) que llega al completar el job.
  }
}

async function fetchResult(id: string): Promise<void> {
  resultLoading.value = true
  resultFetchError.value = null
  try {
    result.value = await getTextureResult(id)
    showingAfter.value = true
    phase.value = 'result'
  } catch (error) {
    resultFetchError.value = error instanceof ApiError ? error.message : 'No se pudo cargar el resultado de la generación de textura.'
  } finally {
    resultLoading.value = false
  }
}

function retryFetchResult(): void {
  if (jobId.value) {
    void fetchResult(jobId.value)
  }
}

function retryGeneration(): void {
  void submitGeneration()
}

/** HU-38: "Reject no modifica ni el draft ni ninguna revisión" -- no existe un endpoint de reject, simplemente nunca se llama a applyTexture. */
function reject(): void {
  resetGenerationState()
}

async function apply(): Promise<void> {
  if (!jobId.value) {
    return
  }
  applying.value = true
  applyError.value = null
  try {
    await applyTexture(jobId.value)
    emit('applied')
    resetGenerationState() // mismo criterio que `AiEditPanel.vue`: el drawer se queda abierto, listo para una nueva generación.
  } catch (error) {
    if (error instanceof ApiError && error.code === 'STALE_TEXTURE_BASE') {
      staleBase.value = true
    }
    applyError.value = error instanceof ApiError ? error.message : 'No se pudo aplicar esta textura.'
  } finally {
    applying.value = false
  }
}
</script>

<template>
  <div class="texture-ai-generator-panel">
    <p v-if="referenceLoadError" class="texture-ai-generator-panel__notice texture-ai-generator-panel__notice--error">{{ referenceLoadError }}</p>
    <p v-if="!hasReferenceImage" class="texture-ai-generator-panel__notice">
      Este mob todavía no tiene ninguna imagen de referencia subida -- no se puede generar textura por IA.
    </p>

    <template v-if="phase === 'form'">
      <div class="texture-ai-generator-panel__reference">
        <img v-if="referenceUrl" class="texture-ai-generator-panel__reference-img" :src="referenceUrl" alt="Referencia del mob" />
        <div v-else class="texture-ai-generator-panel__reference-placeholder">Sin referencia</div>
        <div class="texture-ai-generator-panel__reference-replace">
          <GButton type="button" variant="secondary" :disabled="replacingReference" @click="openReferenceFilePicker">
            <template #icon><IconUpload :size="16" /></template>
            {{ replacingReference ? 'Subiendo…' : 'Cambiar imagen' }}
          </GButton>
          <input ref="referenceFileInputEl" aria-label="Elegir nueva imagen de referencia" type="file" accept="image/png,image/jpeg" class="texture-ai-generator-panel__sr-only" @change="handleReferenceFileChange" />
        </div>
      </div>
      <p v-if="replaceReferenceError" class="texture-ai-generator-panel__notice texture-ai-generator-panel__notice--error"><IconWarning :size="14" /> {{ replaceReferenceError }}</p>

      <div>
        <p id="texture-style-label" class="texture-ai-generator-panel__section-label">Estilo</p>
        <div class="texture-ai-generator-panel__style-grid" role="radiogroup" aria-labelledby="texture-style-label">
          <button v-for="(option, index) in STYLE_OPTIONS" :key="option.value" :ref="(el) => setStyleButtonRef(el as Element | null, index)" type="button" role="radio" class="texture-ai-generator-panel__style-card" :class="{ 'texture-ai-generator-panel__style-card--active': style === option.value }" :aria-checked="style === option.value" :tabindex="style === option.value ? 0 : -1" @click="style = option.value" @keydown="handleStyleKeydown($event, index)"><component :is="option.icon" :size="18" aria-hidden="true" />{{ option.label }}<IconCheck class="texture-ai-generator-panel__style-card-check" :size="14" aria-hidden="true" /></button>
        </div>
      </div>

      <div>
        <p id="texture-detail-label" class="texture-ai-generator-panel__section-label">Detalle</p>
        <div class="texture-ai-generator-panel__segmented" role="radiogroup" aria-labelledby="texture-detail-label">
          <button v-for="(detailLabel, index) in DETAIL_LABELS" :key="detailLabel" :ref="(el) => setDetailButtonRef(el as Element | null, index)" type="button" role="radio" class="texture-ai-generator-panel__segmented-opt" :class="{ 'texture-ai-generator-panel__segmented-opt--active': detailIndex === index }" :aria-checked="detailIndex === index" :tabindex="detailIndex === index ? 0 : -1" @click="detailIndex = index" @keydown="handleDetailKeydown($event, index)">{{ detailLabel }}</button>
        </div>
      </div>

      <div>
        <p id="texture-part-label" class="texture-ai-generator-panel__section-label">Parte a generar</p>
        <GSelect v-model="selectedBoneValue" :options="boneSelectOptions" label="Parte a generar" />
      </div>

      <p v-if="startError" class="texture-ai-generator-panel__notice texture-ai-generator-panel__notice--error">{{ startError }}</p>
      <GButton type="button" variant="primary" class="texture-ai-generator-panel__generate-btn" :disabled="!canSubmit" @click="submitGeneration">
        <template #icon><IconSparkle :size="16" /></template>
        Generar con IA
      </GButton>
    </template>

    <template v-else-if="phase === 'generating'">
      <div class="texture-ai-generator-panel__preview">
        <!-- S6819/S6843: el canvas es un elemento potencialmente interactivo del navegador -- no lleva rol de imagen, el aria-label ya describe el contenido para lectores de pantalla. -->
        <canvas ref="canvasEl" class="texture-ai-generator-panel__canvas" aria-label="Preview incremental del atlas de textura generándose" />
      </div>
      <ul class="texture-ai-generator-panel__stages">
        <li
          v-for="(item, index) in TEXTURE_STAGE_ORDER"
          :key="item.key"
          class="texture-ai-generator-panel__stage"
          :class="`texture-ai-generator-panel__stage--${stageStatus(index)}`"
        >
          <IconCheck v-if="stageStatus(index) === 'done'" :size="12" />
          <span v-else-if="stageStatus(index) === 'current'" class="texture-ai-generator-panel__spinner" />
          {{ item.label }}
        </li>
      </ul>
      <progress class="texture-ai-generator-panel__progress" :value="progressPct" max="100">{{ progressPct }}%</progress>
      <p v-if="currentMessage && outcome === 'running'" class="texture-ai-generator-panel__message">{{ currentMessage }}</p>
      <div v-if="outcome === 'failed'" class="texture-ai-generator-panel__failed">
        <p class="texture-ai-generator-panel__notice texture-ai-generator-panel__notice--error"><IconWarning :size="14" /> {{ failureMessage }}</p>
        <GButton variant="primary" @click="retryGeneration">Reintentar</GButton>
      </div>
    </template>

    <template v-else-if="phase === 'result' && result">
      <div class="texture-ai-generator-panel__preview">
        <div class="texture-ai-generator-panel__toggle" role="tablist" aria-label="Antes o después de la generación">
          <button type="button" role="tab" :aria-selected="!showingAfter" :class="{ 'texture-ai-generator-panel__toggle-btn--active': !showingAfter }" class="texture-ai-generator-panel__toggle-btn" @click="showingAfter = false">Antes</button>
          <button type="button" role="tab" :aria-selected="showingAfter" :class="{ 'texture-ai-generator-panel__toggle-btn--active': showingAfter }" class="texture-ai-generator-panel__toggle-btn" @click="showingAfter = true">Después</button>
        </div>
        <img class="texture-ai-generator-panel__result-image" :src="resultImageSrc" :alt="resultAltText" />
      </div>
      <p v-if="result.hasHandPaintedOverwrite" class="texture-ai-generator-panel__notice texture-ai-generator-panel__notice--warning">
        <IconWarning :size="14" /> Esta propuesta sobrescribirá contenido pintado A MANO en algunas caras -- no solo generado por IA.
      </p>
      <p v-if="applyError" class="texture-ai-generator-panel__notice texture-ai-generator-panel__notice--error">{{ applyError }}</p>
      <GButton v-if="staleBase" variant="secondary" :disabled="applying" @click="retryGeneration">Regenerar contra el estado actual</GButton>
      <div class="texture-ai-generator-panel__actions">
        <GButton variant="secondary" :disabled="applying" @click="reject">Rechazar</GButton>
        <GButton variant="primary" :disabled="applying" @click="apply">{{ applying ? 'Aplicando…' : 'Aplicar' }}</GButton>
      </div>
    </template>

    <p v-else-if="resultLoading" class="texture-ai-generator-panel__notice">Cargando resultado…</p>
    <div v-else-if="resultFetchError" class="texture-ai-generator-panel__failed">
      <p class="texture-ai-generator-panel__notice texture-ai-generator-panel__notice--error">{{ resultFetchError }}</p>
      <GButton variant="secondary" @click="retryFetchResult">Reintentar</GButton>
    </div>
  </div>
</template>

<style scoped>
.texture-ai-generator-panel {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.texture-ai-generator-panel__reference,
.texture-ai-generator-panel__preview {
  aspect-ratio: 16 / 10;
  background: var(--surface);
  border: var(--border-width) solid var(--border);
  border-radius: var(--radius-md);
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
}

.texture-ai-generator-panel__reference-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.texture-ai-generator-panel__reference-placeholder {
  color: var(--muted);
  font-size: var(--text-sm);
  padding: var(--space-3);
  text-align: center;
}

.texture-ai-generator-panel__canvas {
  width: 100%;
  height: 100%;
  object-fit: contain;
  image-rendering: pixelated;
  background: var(--surface);
}

.texture-ai-generator-panel__result-image {
  width: 100%;
  height: 100%;
  object-fit: contain;
  image-rendering: pixelated;
}

.texture-ai-generator-panel__section-label {
  margin: 0 0 var(--space-2);
  color: var(--muted);
  font-size: var(--text-sm);
}

/* Ticket 067 -- radiogroup con tarjetas + ícono, nunca `<input type="radio">` nativo. Sigue en 2 columnas: 420px de ancho de drawer alcanza igual. */
.texture-ai-generator-panel__style-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: var(--space-2);
}

.texture-ai-generator-panel__style-card {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--space-2);
  min-height: 72px;
  padding: var(--space-2);
  background: var(--surface);
  border: var(--border-width) solid var(--border);
  border-radius: var(--radius-md);
  color: var(--muted);
  font-size: var(--text-xs);
  text-align: center;
  cursor: pointer;
  transition: var(--transition-fast);
}

.texture-ai-generator-panel__style-card:hover {
  border-color: #3a4956;
  background: var(--surface-2);
}

.texture-ai-generator-panel__style-card--active {
  border-color: var(--accent);
  background: var(--accent-soft);
  color: var(--text);
  font-weight: 600;
}

.texture-ai-generator-panel__style-card-check {
  position: absolute;
  top: var(--space-2);
  right: var(--space-2);
  color: var(--accent);
  visibility: hidden;
}

.texture-ai-generator-panel__style-card--active .texture-ai-generator-panel__style-card-check {
  visibility: visible;
}

.texture-ai-generator-panel__generate-btn {
  width: 100%;
}

.texture-ai-generator-panel__reference-replace {
  position: absolute;
  inset: auto 0 0 0;
  display: flex;
  justify-content: center;
  padding: var(--space-2);
  background: linear-gradient(to top, color-mix(in srgb, var(--bg) 85%, transparent), transparent);
}

.texture-ai-generator-panel__sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}

.texture-ai-generator-panel__segmented {
  display: flex;
  border: var(--border-width) solid var(--border);
  border-radius: var(--radius-md);
  overflow: hidden;
}

.texture-ai-generator-panel__segmented-opt {
  flex: 1;
  min-height: var(--hit-target-min);
  padding: 0 var(--space-2);
  background: var(--surface);
  border: none;
  border-right: var(--border-width) solid var(--border);
  color: var(--muted);
  font-size: var(--text-sm);
  cursor: pointer;
  transition: var(--transition-fast);
}

.texture-ai-generator-panel__segmented-opt:last-child {
  border-right: none;
}

.texture-ai-generator-panel__segmented-opt:hover {
  background: var(--surface-2);
}

.texture-ai-generator-panel__segmented-opt--active {
  background: var(--accent-soft);
  color: var(--text);
  font-weight: 600;
}

.texture-ai-generator-panel__stages {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-1) var(--space-2);
  font-size: var(--text-xs);
}

.texture-ai-generator-panel__stage {
  display: flex;
  align-items: center;
  gap: var(--space-1);
  color: var(--muted);
}

.texture-ai-generator-panel__stage--current {
  color: var(--text);
  font-weight: 600;
}

.texture-ai-generator-panel__stage--done {
  color: var(--accent);
}

.texture-ai-generator-panel__spinner {
  width: 10px;
  height: 10px;
  border-radius: 999px;
  border: 2px solid var(--accent-soft);
  border-top-color: var(--accent);
  animation: texture-ai-generator-panel-spin 0.8s linear infinite;
}

@keyframes texture-ai-generator-panel-spin {
  to {
    transform: rotate(360deg);
  }
}

.texture-ai-generator-panel__progress {
  appearance: none;
  width: 100%;
  height: 6px;
  border: none;
  border-radius: var(--radius-md);
  overflow: hidden;
}

.texture-ai-generator-panel__progress::-webkit-progress-bar {
  background: var(--surface-2);
}

.texture-ai-generator-panel__progress::-webkit-progress-value {
  background: var(--accent);
}

.texture-ai-generator-panel__progress::-moz-progress-bar {
  background: var(--accent);
}

.texture-ai-generator-panel__message {
  margin: 0;
  font-size: var(--text-xs);
  color: var(--muted);
}

.texture-ai-generator-panel__toggle {
  display: flex;
  gap: var(--space-1);
  position: absolute;
  top: var(--space-2);
  left: var(--space-2);
  z-index: 1;
}

.texture-ai-generator-panel__toggle-btn {
  min-height: 28px;
  padding: 0 var(--space-2);
  background: var(--surface);
  border: var(--border-width) solid var(--border);
  border-radius: var(--radius-sm);
  color: var(--muted);
  font-size: var(--text-xs);
  cursor: pointer;
}

.texture-ai-generator-panel__toggle-btn--active {
  background: var(--surface-2);
  border-color: var(--accent);
  color: var(--text);
  font-weight: 600;
}

.texture-ai-generator-panel__actions {
  display: flex;
  gap: var(--space-2);
}

.texture-ai-generator-panel__notice {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  margin: 0;
  font-size: var(--text-sm);
}

.texture-ai-generator-panel__notice--error {
  color: var(--danger);
}

.texture-ai-generator-panel__notice--warning {
  color: var(--danger);
  background: var(--danger-soft);
  border-radius: var(--radius-md);
  padding: var(--space-2) var(--space-3);
}

.texture-ai-generator-panel__failed {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: var(--space-2);
}
</style>
