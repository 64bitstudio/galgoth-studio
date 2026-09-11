<script setup lang="ts">
/**
 * "Generador de textura (IA)" (ticket 055, HU-36/HU-37/HU-38/HU-42,
 * mockup 08) -- `/projects/:projectId/mobs/:mobId/texture/generate-ai`.
 * Ensambla el pipeline de 054 (`TextureGenerationController`) en una
 * pantalla propia, siguiendo el MISMO patrón ya probado del wizard de
 * generación de geometría (`GenerationStep.vue`/`AiEditPanel.vue`,
 * 029/031/038): configurar -> progreso SSE con preview incremental ->
 * diff Antes/Después con Apply/Reject.
 *
 * **Integración con el tab "Textura" -- gap documentado a propósito**:
 * este ticket depende solo de 054 (backend), NO de 050 (que todavía está
 * pending -- "hace funcional" el tab Textura completo, mockup 07,
 * ensamblando 047/048/049 dentro de `MobEditor.vue`/`EditorHeader.vue`).
 * Cablear el botón real "Generar con IA" DENTRO de ese tab es
 * responsabilidad de 050 cuando aterrice -- acá la pantalla ya es 100%
 * funcional y navegable de forma independiente en su propia ruta (mismo
 * criterio que `AiMobWizard.vue` es una ruta propia en vez de vivir
 * dentro de `MobEditor.vue`). El botón "×" de cierre vuelve al editor
 * del mob (`/edit`), mismo mob, listo para que 050 agregue un link real
 * hacia esta ruta desde el tab Textura sin tocar nada de este archivo.
 *
 * Route-level component, dueño de su propio fetch -- mismo patrón que
 * `MobEditor.vue`/`ExportScreen.vue` (034/032): sin orquestador externo.
 *
 * El panel "resultado/preview" del mockup 08 es el ATLAS 2D (no el
 * viewport 3D) -- coherente con que el pipeline de 054 opera sobre
 * píxeles del atlas, no sobre geometría; `preview_texture_patch` (Diseño
 * técnico §13) llega como parches de un `rect` del atlas, compuestos
 * incrementalmente sobre un `<canvas>` del tamaño real de la textura
 * (`TextureDocument.width/height` del draft ya cargado) -- nunca se
 * escala/interpola (pixel-perfect, mismo invariante que el editor manual,
 * 047). Al completar, el diff Antes/Después (HU-38) reemplaza el canvas
 * por las dos imágenes completas que ya devuelve
 * `GET /api/jobs/{jobId}/texture-result` (`beforeAtlasPngBase64`/
 * `afterAtlasPngBase64`) -- no hace falta reconstruir nada a mano.
 */
import { computed, nextTick, onBeforeUnmount, onMounted, ref, type Component } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import GSidebar, { type GSidebarKey } from '../../design-system/components/GSidebar.vue'
import GButton from '../../design-system/components/GButton.vue'
import GSelect, { type GSelectOption } from '../../design-system/components/GSelect.vue'
import IconButton from '../../design-system/components/IconButton.vue'
import IconCheck from '../../design-system/icons/IconCheck.vue'
import IconCuboid from '../../design-system/icons/IconCuboid.vue'
import IconEye from '../../design-system/icons/IconEye.vue'
import IconMosaic from '../../design-system/icons/IconMosaic.vue'
import IconTarget from '../../design-system/icons/IconTarget.vue'
import IconUpload from '../../design-system/icons/IconUpload.vue'
import IconWarning from '../../design-system/icons/IconWarning.vue'
import IconSparkle from '../../design-system/icons/IconSparkle.vue'
import { getMob } from '../../projects/mobsApi'
import { getDraft } from '../../editor/draftPersistenceApi'
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
import type { TextureGenerationEvent } from './textureGenerationEvents'
import { decodeTexturePreviewPatch } from './texturePatchDecode'
import {
  TEXTURE_STAGE_ORDER,
  findTextureStageIndex,
  outcomeForTextureStage,
  textureStageStatusFor,
  type TextureGenerationOutcome,
} from './textureGenerationStages'

const route = useRoute()
const router = useRouter()
const projectId = route.params.projectId as string
const mobId = route.params.mobId as string

/** Ticket 067 -- rediseño con VoBo del PO sobre preview interactivo (https://claude.ai/code/artifact/9efe4c8d-2776-4f94-be58-6caa4fb78ebc): radio buttons personalizados con ícono, nunca `<input type="radio">` nativo. */
const STYLE_OPTIONS: Array<{ value: TextureStyleValue; label: string; icon: Component }> = [
  { value: 'faithful', label: 'Fiel a la referencia', icon: IconTarget },
  { value: 'minecraft_vanilla', label: 'Minecraft Vanilla', icon: IconCuboid },
  { value: 'pixel_art', label: 'Pixel Art', icon: IconMosaic },
  { value: 'realistic', label: 'Realista', icon: IconEye },
]

const DETAIL_LEVELS: readonly TextureDetailLevelValue[] = ['low', 'medium', 'high']
/** Ticket 067 -- el slider nativo (`<input type="range">`) pasa a un control segmentado de 3 opciones: un slider ARIA personalizado es un widget de alto riesgo de accesibilidad para solo 3 valores discretos: un `role="radiogroup"` cubre lo mismo con el mismo patrón ya usado en `STYLE_OPTIONS`, sin reinventar un slider a mano. */
const DETAIL_LABELS: readonly string[] = ['Bajo', 'Medio', 'Alto']

/** Ticket 067 -- valor centinela para "Modelo completo" en el `GSelect` de "Parte a generar" (su `modelValue` es siempre `string`, nunca `null`) -- se traduce a/desde `targetBoneId` (`string | null`) en `selectedBoneValue`. */
const WHOLE_MODEL_VALUE = ''

interface BoneOption {
  id: string
  name: string
}

type Phase = 'form' | 'generating' | 'result'

// ---- carga inicial ----
const loading = ref(true)
const loadError = ref<string | null>(null)
const notFound = ref(false)
const referenceUrl = ref<string | null>(null)
const hasReferenceImage = ref(false)
const textureWidth = ref(0)
const textureHeight = ref(0)
const boneOptions = ref<BoneOption[]>([])

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

/** Mismos límites/mensajes que `ReferenceStep.vue` (027) -- valida client-side antes de subir, mismo criterio que el resto del proyecto. */
async function handleReferenceFileChange(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0] ?? null
  input.value = '' // permite re-elegir el mismo archivo dos veces seguidas (mismo criterio que TextureImportPanel/pngImportDecode)
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
    const summary = await uploadReferenceImage(mobId, file)
    // `ReferenceImageService` es append-only (024) -- subir una nueva imagen
    // la vuelve automáticamente "la" referencia (`mostRecentReference` en el
    // backend siempre toma la última) sin necesitar ningún endpoint nuevo.
    referenceUrl.value = referenceImageUrl(summary.url)
    hasReferenceImage.value = true
  } catch (error) {
    replaceReferenceError.value = error instanceof ApiError ? error.message : 'No se pudo subir la nueva imagen de referencia.'
  } finally {
    replacingReference.value = false
  }
}

// ---- radiogroups personalizados (Estilo/Detalle, ticket 067) ----
// Patrón WAI-ARIA "Radio Group" con roving tabindex: solo la opción
// seleccionada (o la primera, si ninguna) es alcanzable con Tab -- las
// flechas mueven foco+selección DENTRO del grupo (con wrap-around, mismo
// comportamiento validado en el preview interactivo). Un solo helper
// genérico para los 2 grupos de esta pantalla (Estilo de 4 opciones,
// Detalle de 3) en vez de duplicar la lógica de teclado.
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

const canSubmit = computed(() => !loading.value && hasReferenceImage.value && !notFound.value && !loadError.value)

onMounted(load)
onBeforeUnmount(closeStream)

async function load(): Promise<void> {
  loading.value = true
  loadError.value = null
  try {
    // `getMob` no se usa por su resultado -- solo para que un mob inexistente
    // falle acá con `MOB_NOT_FOUND` de la misma forma explícita que el resto
    // de las pantallas route-level (`MobEditor.vue`/`ExportScreen.vue`, 034/032).
    const [, draftView, references] = await Promise.all([getMob(mobId), getDraft(mobId), listReferenceImages(mobId)])
    textureWidth.value = draftView.model.texture.width
    textureHeight.value = draftView.model.texture.height
    boneOptions.value = draftView.model.bones
      .filter((bone) => draftView.model.cuboids.some((cuboid) => cuboid.boneId === bone.id))
      .map((bone) => ({ id: bone.id, name: bone.name }))

    if (references.length > 0) {
      hasReferenceImage.value = true
      referenceUrl.value = referenceImageUrl(references[references.length - 1]!.url)
    }
  } catch (error) {
    if (error instanceof ApiError && error.code === 'MOB_NOT_FOUND') {
      notFound.value = true
      return
    }
    loadError.value = error instanceof ApiError ? error.message : 'No se pudo cargar el generador de textura de este mob.'
  } finally {
    loading.value = false
  }
}

function resetProgressState(): void {
  seenSeqs.clear()
  currentStage.value = TEXTURE_STAGE_ORDER[0]!.key
  currentMessage.value = null
  progressPct.value = 0
  outcome.value = 'running'
  failureMessage.value = null
}

async function submitGeneration(): Promise<void> {
  startError.value = null
  staleBase.value = false
  resetProgressState()
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
    const response = await startTextureGeneration(mobId, { style: style.value, detailLevel: detailLevel.value, boneId: targetBoneId.value })
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
    return // reconexión con solape de backlog/en-vivo, mismo criterio que GenerationStep.vue (029/038) -- idempotente.
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
    // flujo de progreso -- es solo una ayuda visual incremental (Diseño
    // técnico §13); el resultado real y autoritativo es el diff Antes/
    // Después (HU-38) que llega al completar el job.
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

/** HU-38: "Reject no modifica ni el draft ni ninguna revisión" -- no existe un endpoint de reject (ver docs/API.md), simplemente nunca se llama a applyTexture. */
function reject(): void {
  phase.value = 'form'
  jobId.value = null
  result.value = null
  applyError.value = null
  staleBase.value = false
}

async function apply(): Promise<void> {
  if (!jobId.value) {
    return
  }
  applying.value = true
  applyError.value = null
  try {
    await applyTexture(jobId.value)
    router.push(`/projects/${projectId}/mobs/${mobId}/edit`)
  } catch (error) {
    if (error instanceof ApiError && error.code === 'STALE_TEXTURE_BASE') {
      staleBase.value = true
    }
    applyError.value = error instanceof ApiError ? error.message : 'No se pudo aplicar esta textura.'
  } finally {
    applying.value = false
  }
}

function backToEditor(): void {
  router.push(`/projects/${projectId}/mobs/${mobId}/edit`)
}

function handleSidebarSelect(key: GSidebarKey): void {
  if (key === 'home' || key === 'projects') {
    router.push('/projects')
  }
}
</script>

<template>
  <div class="texture-ai-generator-shell">
    <GSidebar active="projects" @select="handleSidebarSelect" />
    <main class="texture-ai-generator app-scroll">
      <p v-if="notFound" class="texture-ai-generator__error">
        Este mob no existe. <button type="button" class="texture-ai-generator__link-button" @click="router.push(`/projects/${projectId}`)">Volver al proyecto</button>
      </p>
      <p v-else-if="loadError" class="texture-ai-generator__error">{{ loadError }}</p>
      <p v-else-if="loading" class="texture-ai-generator__loading">Cargando…</p>

      <div v-else class="texture-ai-generator__panel">
        <div class="texture-ai-generator__header">
          <h2 class="texture-ai-generator__title"><IconSparkle :size="18" /> Generador de textura (IA)</h2>
          <IconButton label="Cerrar" @click="backToEditor">×</IconButton>
        </div>

        <p v-if="!hasReferenceImage" class="texture-ai-generator__notice">
          Este mob todavía no tiene ninguna imagen de referencia subida -- no se puede generar textura por IA.
        </p>

        <div class="texture-ai-generator__body">
          <div class="texture-ai-generator__column">
            <div class="texture-ai-generator__reference">
              <img v-if="referenceUrl" class="texture-ai-generator__reference-img" :src="referenceUrl" alt="Referencia del mob" />
              <div v-else class="texture-ai-generator__reference-placeholder">Sin referencia</div>
              <div v-if="phase === 'form'" class="texture-ai-generator__reference-replace">
                <GButton type="button" variant="secondary" :disabled="replacingReference" @click="openReferenceFilePicker">
                  <template #icon><IconUpload :size="16" /></template>
                  {{ replacingReference ? 'Subiendo…' : 'Cambiar imagen' }}
                </GButton>
                <input ref="referenceFileInputEl" aria-label="Elegir nueva imagen de referencia" type="file" accept="image/png,image/jpeg" class="texture-ai-generator__sr-only" @change="handleReferenceFileChange" />
              </div>
            </div>
            <p v-if="replaceReferenceError" class="texture-ai-generator__notice texture-ai-generator__notice--error"><IconWarning :size="14" /> {{ replaceReferenceError }}</p>

            <div>
              <p id="texture-style-label" class="texture-ai-generator__section-label">Estilo</p>
              <div class="texture-ai-generator__style-grid" role="radiogroup" aria-labelledby="texture-style-label">
                <button v-for="(option, index) in STYLE_OPTIONS" :key="option.value" :ref="(el) => setStyleButtonRef(el as Element | null, index)" type="button" role="radio" class="texture-ai-generator__style-card" :class="{ 'texture-ai-generator__style-card--active': style === option.value }" :aria-checked="style === option.value" :tabindex="style === option.value ? 0 : -1" :disabled="phase !== 'form'" @click="style = option.value" @keydown="handleStyleKeydown($event, index)"><component :is="option.icon" :size="20" aria-hidden="true" />{{ option.label }}<IconCheck class="texture-ai-generator__style-card-check" :size="16" aria-hidden="true" /></button>
              </div>
            </div>

            <p v-if="startError" class="texture-ai-generator__notice texture-ai-generator__notice--error">{{ startError }}</p>
            <GButton v-if="phase === 'form'" type="button" variant="primary" class="texture-ai-generator__generate-btn" :disabled="!canSubmit" @click="submitGeneration">
              <template #icon><IconSparkle :size="16" /></template>
              Generar con IA
            </GButton>
          </div>

          <div class="texture-ai-generator__column">
            <div class="texture-ai-generator__preview">
              <p v-if="phase === 'form'" class="texture-ai-generator__preview-placeholder">El resultado aparecerá aquí.</p>

              <template v-else-if="phase === 'generating'">
                <!-- S6819/S6843: el canvas es un elemento potencialmente interactivo del navegador -- no lleva rol de imagen, el aria-label ya describe el contenido para lectores de pantalla. -->
                <canvas ref="canvasEl" class="texture-ai-generator__canvas" aria-label="Preview incremental del atlas de textura generándose" />
                <div class="texture-ai-generator__progress-block">
                  <ul class="texture-ai-generator__stages">
                    <li
                      v-for="(item, index) in TEXTURE_STAGE_ORDER"
                      :key="item.key"
                      class="texture-ai-generator__stage"
                      :class="`texture-ai-generator__stage--${stageStatus(index)}`"
                    >
                      <IconCheck v-if="stageStatus(index) === 'done'" :size="12" />
                      <span v-else-if="stageStatus(index) === 'current'" class="texture-ai-generator__spinner" />
                      {{ item.label }}
                    </li>
                  </ul>
                  <progress class="texture-ai-generator__progress" :value="progressPct" max="100">{{ progressPct }}%</progress>
                  <p v-if="currentMessage && outcome === 'running'" class="texture-ai-generator__message">{{ currentMessage }}</p>
                  <div v-if="outcome === 'failed'" class="texture-ai-generator__failed">
                    <p class="texture-ai-generator__notice texture-ai-generator__notice--error"><IconWarning :size="14" /> {{ failureMessage }}</p>
                    <GButton variant="primary" @click="retryGeneration">Reintentar</GButton>
                  </div>
                </div>
              </template>

              <template v-else-if="phase === 'result' && result">
                <div class="texture-ai-generator__toggle" role="tablist" aria-label="Antes o después de la generación">
                  <button type="button" role="tab" :aria-selected="!showingAfter" :class="{ 'texture-ai-generator__toggle-btn--active': !showingAfter }" class="texture-ai-generator__toggle-btn" @click="showingAfter = false">Antes</button>
                  <button type="button" role="tab" :aria-selected="showingAfter" :class="{ 'texture-ai-generator__toggle-btn--active': showingAfter }" class="texture-ai-generator__toggle-btn" @click="showingAfter = true">Después</button>
                </div>
                <img class="texture-ai-generator__result-image" :src="resultImageSrc" :alt="resultAltText" />
              </template>

              <p v-else-if="resultLoading" class="texture-ai-generator__preview-placeholder">Cargando resultado…</p>
              <div v-else-if="resultFetchError" class="texture-ai-generator__failed">
                <p class="texture-ai-generator__notice texture-ai-generator__notice--error">{{ resultFetchError }}</p>
                <GButton variant="secondary" @click="retryFetchResult">Reintentar</GButton>
              </div>
            </div>

            <div class="texture-ai-generator__detail">
              <p id="texture-detail-label" class="texture-ai-generator__section-label">Detalle</p>
              <div class="texture-ai-generator__segmented" role="radiogroup" aria-labelledby="texture-detail-label">
                <button v-for="(detailLabel, index) in DETAIL_LABELS" :key="detailLabel" :ref="(el) => setDetailButtonRef(el as Element | null, index)" type="button" role="radio" class="texture-ai-generator__segmented-opt" :class="{ 'texture-ai-generator__segmented-opt--active': detailIndex === index }" :aria-checked="detailIndex === index" :tabindex="detailIndex === index ? 0 : -1" :disabled="phase !== 'form'" @click="detailIndex = index" @keydown="handleDetailKeydown($event, index)">{{ detailLabel }}</button>
              </div>
            </div>

            <div class="texture-ai-generator__part">
              <p id="texture-part-label" class="texture-ai-generator__section-label">Parte a generar</p>
              <GSelect v-model="selectedBoneValue" :options="boneSelectOptions" label="Parte a generar" :disabled="phase !== 'form'" />
            </div>
          </div>
        </div>

        <div v-if="phase === 'result'" class="texture-ai-generator__footer">
          <p v-if="result?.hasHandPaintedOverwrite" class="texture-ai-generator__notice texture-ai-generator__notice--warning">
            <IconWarning :size="14" /> Esta propuesta sobrescribirá contenido pintado A MANO en algunas caras -- no solo generado por IA.
          </p>
          <p v-if="applyError" class="texture-ai-generator__notice texture-ai-generator__notice--error">{{ applyError }}</p>
          <GButton v-if="staleBase" variant="secondary" :disabled="applying" @click="retryGeneration">Regenerar contra el estado actual</GButton>
          <div class="texture-ai-generator__actions">
            <GButton variant="secondary" :disabled="applying" @click="reject">Rechazar</GButton>
            <GButton variant="primary" :disabled="applying" @click="apply">{{ applying ? 'Aplicando…' : 'Aplicar' }}</GButton>
          </div>
        </div>
      </div>
    </main>
  </div>
</template>

<style scoped>
.texture-ai-generator-shell {
  display: flex;
  height: 100vh;
}

.texture-ai-generator {
  flex: 1;
  padding: var(--space-6);
  overflow: auto;
  display: flex;
  flex-direction: column;
}

.texture-ai-generator__loading,
.texture-ai-generator__error {
  color: var(--muted);
}

.texture-ai-generator__link-button {
  background: none;
  border: none;
  color: var(--accent);
  text-decoration: underline;
  cursor: pointer;
  padding: 0;
  font: inherit;
}

.texture-ai-generator__panel {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
  background: var(--panel);
  border: var(--border-width) solid var(--border);
  border-radius: var(--radius-lg);
  padding: var(--space-5);
  max-width: 900px;
}

.texture-ai-generator__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.texture-ai-generator__title {
  margin: 0;
  display: flex;
  align-items: center;
  gap: var(--space-2);
  color: var(--accent);
}

.texture-ai-generator__body {
  display: flex;
  gap: var(--space-5);
  flex-wrap: wrap;
}

.texture-ai-generator__column {
  flex: 1;
  min-width: 260px;
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.texture-ai-generator__reference,
.texture-ai-generator__preview {
  aspect-ratio: 1;
  background: var(--surface);
  border: var(--border-width) solid var(--border);
  border-radius: var(--radius-md);
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
}

.texture-ai-generator__reference-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.texture-ai-generator__reference-placeholder,
.texture-ai-generator__preview-placeholder {
  color: var(--muted);
  font-size: var(--text-sm);
  padding: var(--space-3);
  text-align: center;
}

.texture-ai-generator__canvas {
  width: 100%;
  height: 100%;
  object-fit: contain;
  image-rendering: pixelated;
  background: var(--surface);
}

.texture-ai-generator__result-image {
  width: 100%;
  height: 100%;
  object-fit: contain;
  image-rendering: pixelated;
}

.texture-ai-generator__section-label {
  margin: 0 0 var(--space-2);
  color: var(--muted);
  font-size: var(--text-sm);
}

/* Ticket 067 -- radiogroup de "Estilo" con VoBo del PO sobre el preview
   interactivo (https://claude.ai/code/artifact/9efe4c8d-2776-4f94-be58-6caa4fb78ebc):
   tarjetas con ícono, nunca `<input type="radio">` nativo -- mismo criterio
   ya validado por `GSelect.vue` (058) para "sin componentes nativos". */
.texture-ai-generator__style-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: var(--space-2);
}

.texture-ai-generator__style-card {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--space-2);
  min-height: 88px;
  padding: var(--space-3);
  background: var(--surface);
  border: var(--border-width) solid var(--border);
  border-radius: var(--radius-md);
  color: var(--muted);
  font-size: var(--text-sm);
  text-align: center;
  cursor: pointer;
  transition: var(--transition-fast);
}

.texture-ai-generator__style-card:hover:not(:disabled) {
  border-color: #3a4956;
  background: var(--surface-2);
}

.texture-ai-generator__style-card:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

.texture-ai-generator__style-card--active {
  border-color: var(--accent);
  background: var(--accent-soft);
  color: var(--text);
  font-weight: 600;
}

.texture-ai-generator__style-card-check {
  position: absolute;
  top: var(--space-2);
  right: var(--space-2);
  color: var(--accent);
  visibility: hidden;
}

.texture-ai-generator__style-card--active .texture-ai-generator__style-card-check {
  visibility: visible;
}

.texture-ai-generator__generate-btn {
  width: 100%;
}

/* Ticket 067 -- reemplazo de la imagen de referencia (VoBo del PO):
   overlay "Cambiar imagen" siempre visible bajo la referencia (no solo al
   hover) -- mismo criterio de accesibilidad que el resto de la pantalla
   (nada exclusivo de hover/mouse). */
.texture-ai-generator__reference-replace {
  display: flex;
  justify-content: center;
}

.texture-ai-generator__sr-only {
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

.texture-ai-generator__detail,
.texture-ai-generator__part {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

/* Ticket 067 -- el slider nativo de Detalle pasa a un control segmentado de
   3 opciones (ver nota en `DETAIL_LABELS`, script). */
.texture-ai-generator__segmented {
  display: flex;
  border: var(--border-width) solid var(--border);
  border-radius: var(--radius-md);
  overflow: hidden;
}

.texture-ai-generator__segmented-opt {
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

.texture-ai-generator__segmented-opt:last-child {
  border-right: none;
}

.texture-ai-generator__segmented-opt:hover:not(:disabled) {
  background: var(--surface-2);
}

.texture-ai-generator__segmented-opt:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

.texture-ai-generator__segmented-opt--active {
  background: var(--accent-soft);
  color: var(--text);
  font-weight: 600;
}

.texture-ai-generator__progress-block {
  position: absolute;
  inset: auto 0 0 0;
  background: color-mix(in srgb, var(--surface) 85%, transparent);
  padding: var(--space-2) var(--space-3);
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.texture-ai-generator__stages {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2) var(--space-3);
  font-size: var(--text-xs);
}

.texture-ai-generator__stage {
  display: flex;
  align-items: center;
  gap: var(--space-1);
  color: var(--muted);
}

.texture-ai-generator__stage--current {
  color: var(--text);
  font-weight: 600;
}

.texture-ai-generator__stage--done {
  color: var(--accent);
}

.texture-ai-generator__spinner {
  width: 10px;
  height: 10px;
  border-radius: 999px;
  border: 2px solid var(--accent-soft);
  border-top-color: var(--accent);
  animation: texture-ai-generator-spin 0.8s linear infinite;
}

@keyframes texture-ai-generator-spin {
  to {
    transform: rotate(360deg);
  }
}

.texture-ai-generator__progress {
  appearance: none;
  width: 100%;
  height: 6px;
  border: none;
  border-radius: var(--radius-md);
  overflow: hidden;
}

.texture-ai-generator__progress::-webkit-progress-bar {
  background: var(--surface-2);
}

.texture-ai-generator__progress::-webkit-progress-value {
  background: var(--accent);
}

.texture-ai-generator__progress::-moz-progress-bar {
  background: var(--accent);
}

.texture-ai-generator__message {
  margin: 0;
  font-size: var(--text-xs);
  color: var(--muted);
}

.texture-ai-generator__toggle {
  display: flex;
  gap: var(--space-1);
  position: absolute;
  top: var(--space-2);
  left: var(--space-2);
  z-index: 1;
}

.texture-ai-generator__toggle-btn {
  min-height: 28px;
  padding: 0 var(--space-2);
  background: var(--surface);
  border: var(--border-width) solid var(--border);
  border-radius: var(--radius-sm);
  color: var(--muted);
  font-size: var(--text-xs);
  cursor: pointer;
}

.texture-ai-generator__toggle-btn--active {
  background: var(--surface-2);
  border-color: var(--accent);
  color: var(--text);
  font-weight: 600;
}

.texture-ai-generator__footer {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: var(--space-3);
  padding-top: var(--space-3);
  border-top: var(--border-width) solid var(--border);
}

.texture-ai-generator__actions {
  display: flex;
  gap: var(--space-2);
}

.texture-ai-generator__notice {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  margin: 0;
  font-size: var(--text-sm);
}

.texture-ai-generator__notice--error {
  color: var(--danger);
}

.texture-ai-generator__notice--warning {
  color: var(--danger);
  background: var(--danger-soft);
  border-radius: var(--radius-md);
  padding: var(--space-2) var(--space-3);
}

.texture-ai-generator__failed {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: var(--space-2);
}
</style>
