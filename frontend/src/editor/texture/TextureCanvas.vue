<script setup lang="ts">
/**
 * Ticket 047 -- editor de textura/UV manual: el canvas 2D real y sus
 * herramientas de pintado (Diseño técnico Sección 9 de
 * docs/definiciones/galgoth-studio-fase3-textura.md, HU-24/26/27).
 * Ticket 048 -- import de PNG. Ticket 049 -- selección cruzada
 * cuboid-UV. Ver el detalle histórico de cada uno en el `## Hecho` de sus
 * tickets respectivos.
 *
 * Ticket 058 -- REDISEÑO completo del shell visual (mockup 07 v2, VoBo
 * explícito del PO tras 2 rondas de ajustes sobre un Artifact validado,
 * ver `docs/definiciones/mockups/058-texture-editor-redesign-reference.html`).
 * La columna lateral izquierda de herramientas (047/050) se reemplaza por
 * 3 zonas: toolbar horizontal arriba, lienzo dominante al centro, preview
 * 3D secundario a la derecha -- el lienzo pasa a tener zoom/pan reales
 * (antes solo una escala CSS fija, ver el comentario que describía esa
 * limitación en versiones previas de este archivo). Ningún mecanismo de
 * dominio cambia (pixelTools.ts/textureEditorStore.ts/ThreeViewportService
 * siguen intactos) -- este ticket es 100% shell/UX.
 *
 * Arquitectura de dos capas superpuestas dentro del "stage" (sin cambios
 * de fondo respecto a 047, solo ahora con tamaño en px explícito en vez
 * de una escala CSS fija -- ver `displayScale`/`stageWidthPx/HeightPx`):
 * - canvas (bitmap real, canvasRef): resolución intrínseca EXACTA al
 *   atlas (atlas.width/atlas.height), pintado 1:1 sin escalar. El tamaño
 *   VISIBLE (`stageWidthPx/HeightPx`) es controlado por `zoomPercent` --
 *   pixelated garantiza pixel-perfect en cualquier nivel de zoom (AC no
 *   negociable, ya un principio del ticket 047).
 * - svg overlay (guía UV + grid + resaltado de región): capa SEPARADA,
 *   pointer-events: none, nunca toca atlas.pixels.
 *
 * Zoom/pan (ticket 058): `zoomPercent` (25-1600%) controla la escala vía
 * `setZoom()` -- botones +/-/reset, dropdown de presets (`GSelect`),
 * atajos de teclado (+/-/0) y `Ctrl`+rueda CENTRADO en el cursor (mismo
 * algoritmo que el mockup validado: convierte el punto bajo el cursor a
 * coordenadas de atlas ANTES de cambiar la escala, y ajusta el scroll del
 * viewport DESPUÉS para que ese mismo punto de atlas quede bajo el cursor
 * de nuevo). Pan: barra espaciadora + arrastre (mismo criterio que
 * herramientas de diseño estándar) sobre el viewport con scroll nativo
 * (clase `.app-scroll` ya estilizada del proyecto, ticket 039) --
 * `canvasPointToAtlas` NO cambió: sigue siendo un cálculo de RATIO
 * (canvas.width/rect.width), así que es zoom-agnóstico por construcción,
 * sin tocar la lógica de pintado en absoluto.
 *
 * Selectores propios (ticket 058, AC "ningún elemento de select nativo en
 * esta pantalla"): región/tamaño de pincel/presets de zoom migran del
 * elemento nativo de selección/de un input numérico a `GSelect.vue`
 * (design system, generalizado en este mismo ticket). El color activo
 * migra de un control de color nativo VISIBLE a `TextureColorPicker.vue`
 * (trigger + panel con paleta + swatch personalizado -- ese swatch SÍ
 * dispara un control de color nativo oculto, única excepción ya aceptada
 * por el PO). El tamaño de pincel pasa de un rango libre 1-32 a un set de
 * presets (1/2/4/8/16/32px) -- fidelidad al mockup validado, con MÁS
 * presets que los 4 del mockup (1/2/4/8) para no perder el extremo
 * superior (32px) que el rango libre anterior sí permitía.
 *
 * Cuentagotas (ticket 058, AC "confirmación visible de qué color se
 * capturó"): además de activar el color, muestra un toast temporal
 * (`pickedColorToast`) con el hex capturado -- antes era un cambio
 * silencioso (solo el swatch cambiaba, sin ningún otro indicio).
 *
 * Guardado (ticket 058, AC "indicador de 4 estados, reutiliza el flush
 * de 056"): `saveState` (saved/dirty/saving/error) se marca `dirty` en
 * cada mutación real del atlas (trazo/fill/import -- NUNCA en la carga
 * inicial), y `handleSave()` reutiliza EXACTAMENTE el mismo mecanismo que
 * `EditorToolbar.handleSave` (`flushPaintedTexture` + `saveRevision` +
 * `draftModelStore.commitExternalModel` + thumbnail best-effort) -- se
 * duplica la orquestación (no se extrajo a un módulo compartido) porque
 * `EditorToolbar.vue` ya tiene su propio flujo probado (`EditorToolbar.spec.ts`)
 * y unificar ambos hubiese significado tocar un componente ajeno a este
 * ticket sin necesidad real; ambos llaman a los MISMOS módulos de fondo
 * (`textureFlush.ts`/`draftPersistenceApi.ts`/`thumbnailApi.ts`), así que
 * no hay ninguna lógica de negocio duplicada, solo la orquestación local.
 *
 * "Generar con IA" (ticket 058): navega a la ruta ya existente del ticket
 * 055 (generador IA de textura) -- reutiliza el pipeline real de 054/055,
 * este ticket no reimplementa nada de IA.
 *
 * Preview 3D orbitable (ticket 058): `ThreeViewportService` ya expone
 * OrbitControls HABILITADOS por defecto (`this.controls.enableDamping = true`,
 * nunca deshabilitado para este contexto -- la única vez que se
 * deshabilita es durante el arrastre de TransformControls, que no aplica
 * a la tab Textura) -- este ticket NO necesitó ningún cambio en
 * `ThreeViewportService.ts`: el arrastre para orbitar ya funcionaba, la
 * selección cruzada cuboid-UV (`handlePreviewPointerDown`/`handlePreviewClick`,
 * ticket 049) coexiste con OrbitControls exactamente igual que en la tab
 * Modelo (mismo criterio click-vs-drag ya usado por `ThreeViewport.vue`).
 */
import { DataTexture, RGBAFormat, NearestFilter } from 'three'
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import type { FaceName, MobProjectModel } from '../../domain/MobProjectModel'
import { threeViewportService } from '../../viewport/ThreeViewportService'
import type { CuboidFaceRef } from '../../viewport/textureUvMapping'
import IconButton from '../../design-system/components/IconButton.vue'
import GButton from '../../design-system/components/GButton.vue'
import GSelect, { type GSelectOption } from '../../design-system/components/GSelect.vue'
import IconBrush from '../../design-system/icons/IconBrush.vue'
import IconBucket from '../../design-system/icons/IconBucket.vue'
import IconEraser from '../../design-system/icons/IconEraser.vue'
import IconEyedropper from '../../design-system/icons/IconEyedropper.vue'
import IconGrid from '../../design-system/icons/IconGrid.vue'
import IconSave from '../../design-system/icons/IconSave.vue'
import IconSparkle from '../../design-system/icons/IconSparkle.vue'
import IconZoomIn from '../../design-system/icons/IconZoomIn.vue'
import IconZoomOut from '../../design-system/icons/IconZoomOut.vue'
import IconZoomReset from '../../design-system/icons/IconZoomReset.vue'
import { useDraftModelStore } from '../draftModelStore'
import { saveRevision } from '../draftPersistenceApi'
import { uploadThumbnail } from '../thumbnailApi'
import { hexToRgba, rgbaToHex } from './colorHex'
import {
  boundsToRect,
  computeFloodFill,
  createEmptyBounds,
  pickColorAt,
  stampLine,
  stampSquare,
  TRANSPARENT,
  type PixelBounds,
  type RgbaColor,
} from './pixelTools'
import { ALL_REGIONS_VALUE, buildSelectableRegions, regionKey, type SelectableRegion } from './regionLabels'
import type { TextureRect } from './TexturePatchCommand'
import { readRectFrom } from './textureRectBuffer'
import { DEFAULT_COLOR } from './texturePalette'
import { flushPaintedTexture } from './textureFlush'
import { useTextureEditorStore } from './textureEditorStore'
import { useTextureSelectionStore } from './textureSelectionStore'
import TextureColorPicker from './TextureColorPicker.vue'
import TextureImportPanel from './TextureImportPanel.vue'
import TextureSaveStatus, { type TextureSaveState } from './TextureSaveStatus.vue'

type Tool = 'brush' | 'eraser' | 'fill' | 'eyedropper'

const TOOL_LABELS: Record<Tool, string> = { brush: 'Pincel', eraser: 'Borrador', fill: 'Cubeta', eyedropper: 'Cuentagotas' }

// Umbral de movimiento del mouse entre pointerdown y click en el preview
// 3D -- por encima de esto se interpreta como arrastre de órbita
// (OrbitControls), no como un click de selección de cara. Mismo criterio
// que `ThreeViewport.vue` (ticket 017).
const CLICK_DRAG_THRESHOLD_PX = 5

const GRID_STEP_PX = 8
const BRUSH_SIZE_PRESETS = [1, 2, 4, 8, 16, 32]
const ZOOM_PRESETS = [50, 100, 200, 400, 800, 1600]
const MIN_ZOOM = 25
const MAX_ZOOM = 1600
const ZOOM_STEP_FACTOR = 1.5
const ZOOM_WHEEL_FACTOR = 1.12
const BASE_SCALE = 4
const EYEDROPPER_TOAST_MS = 1800

const props = defineProps<{ model: MobProjectModel }>()

const router = useRouter()
const textureEditorStore = useTextureEditorStore()
const textureSelectionStore = useTextureSelectionStore()
const draftModelStore = useDraftModelStore()

const canvasRef = ref<HTMLCanvasElement>()
const canvasViewportRef = ref<HTMLDivElement>()
const previewContainerRef = ref<HTMLDivElement>()

const activeTool = ref<Tool>('brush')
const activeColorHex = ref(DEFAULT_COLOR)
const brushSize = ref(4)
const showGrid = ref(false)
const zoomPercent = ref(100)
const spaceDown = ref(false)
const isPanning = ref(false)
const pickedColorToast = ref<string | null>(null)
const saveState = ref<TextureSaveState>('saved')

/**
 * Puente entre el selector de región (formato `regionKey`, ticket 047) y
 * `textureSelectionStore.selectedFace` (formato `{cuboidId, face}`,
 * ticket 049).
 */
const selectedRegionKey = computed<string>({
  get: () => (textureSelectionStore.selectedFace ? regionKey(textureSelectionStore.selectedFace) : ALL_REGIONS_VALUE),
  set: (value) => {
    if (value === ALL_REGIONS_VALUE) {
      textureSelectionStore.selectFace(null)
      return
    }
    const [cuboidId, face] = value.split(':') as [string, FaceName]
    textureSelectionStore.selectFace({ cuboidId, face })
  },
})

const atlasWidth = computed(() => textureEditorStore.atlas?.width ?? 0)
const atlasHeight = computed(() => textureEditorStore.atlas?.height ?? 0)
const selectableRegions = computed<SelectableRegion[]>(() => buildSelectableRegions(props.model.uv.regions, props.model.cuboids))

const regionOptions = computed<GSelectOption[]>(() => [
  { value: ALL_REGIONS_VALUE, label: 'Todas las caras' },
  ...selectableRegions.value.map((region) => ({ value: regionKey(region), label: region.label })),
])

const brushSizeOptions: GSelectOption[] = BRUSH_SIZE_PRESETS.map((n) => ({ value: String(n), label: `${n}px` }))

const zoomOptions: GSelectOption[] = ZOOM_PRESETS.map((z) => ({ value: String(z), label: `${z}%` }))

const activeRegionLabel = computed<string>(() => selectedRegion.value?.label ?? 'Todas las caras')
const activeToolLabel = computed<string>(() => TOOL_LABELS[activeTool.value])

const gridLinesX = computed(() => {
  const lines: number[] = []
  for (let x = GRID_STEP_PX; x < atlasWidth.value; x += GRID_STEP_PX) {
    lines.push(x)
  }
  return lines
})
const gridLinesY = computed(() => {
  const lines: number[] = []
  for (let y = GRID_STEP_PX; y < atlasHeight.value; y += GRID_STEP_PX) {
    lines.push(y)
  }
  return lines
})

function isRegionSelected(region: SelectableRegion): boolean {
  return selectedRegionKey.value !== ALL_REGIONS_VALUE && regionKey(region) === selectedRegionKey.value
}

function regionRect(region: SelectableRegion): { x: number; y: number; width: number; height: number } {
  const [x0, y0, x1, y1] = region.rect
  return { x: x0, y: y0, width: x1 - x0, height: y1 - y0 }
}

// -- Ticket 048: destino del import de PNG -------------------------------
const selectedRegion = computed<SelectableRegion | null>(() => selectableRegions.value.find((region) => regionKey(region) === selectedRegionKey.value) ?? null)

const importTargetLabel = computed<string>(() => (selectedRegion.value ? `la región "${selectedRegion.value.label}"` : 'el atlas completo'))

function resolveImportTarget(): { rect: TextureRect; label: string } {
  const region = selectedRegion.value
  const rect = region ? regionRect(region) : { x: 0, y: 0, width: atlasWidth.value, height: atlasHeight.value }
  const label = region ? `la región "${region.label}"` : 'el atlas completo'
  return { rect, label }
}

function handleImported(): void {
  syncDataTexture()
  redraw()
  markDirty()
}

// -- Zoom/pan (ticket 058) ------------------------------------------------
const displayScale = computed(() => BASE_SCALE * (zoomPercent.value / 100))
const stageWidthPx = computed(() => Math.round(atlasWidth.value * displayScale.value))
const stageHeightPx = computed(() => Math.round(atlasHeight.value * displayScale.value))

/**
 * Cambia el zoom, opcionalmente centrado en un punto de pantalla
 * (`anchorClientX/Y`, ej. la posición del cursor en `Ctrl`+rueda) -- sin
 * ancla, centra en el punto medio del viewport visible (botones
 * +/-/reset, atajos de teclado). Mismo algoritmo que el mockup validado:
 * convierte el punto bajo el ancla a coordenadas de ATLAS (independientes
 * del zoom) ANTES de cambiar la escala, y ajusta `scrollLeft/scrollTop`
 * DESPUÉS para que ese mismo punto de atlas quede exactamente bajo el
 * ancla de nuevo -- `nextTick` porque el tamaño del stage recién cambia
 * en el DOM después de que Vue aplica el nuevo `zoomPercent`.
 */
function setZoom(nextRaw: number, anchorClientX?: number, anchorClientY?: number): void {
  const next = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, Math.round(nextRaw)))
  if (next === zoomPercent.value) {
    return
  }
  const viewport = canvasViewportRef.value
  if (!viewport) {
    zoomPercent.value = next
    return
  }
  const rect = viewport.getBoundingClientRect()
  const beforeScale = displayScale.value
  const localX = anchorClientX != null ? anchorClientX - rect.left + viewport.scrollLeft : viewport.scrollLeft + viewport.clientWidth / 2
  const localY = anchorClientY != null ? anchorClientY - rect.top + viewport.scrollTop : viewport.scrollTop + viewport.clientHeight / 2
  const atlasX = localX / beforeScale
  const atlasY = localY / beforeScale
  zoomPercent.value = next
  void nextTick(() => {
    const afterScale = displayScale.value
    viewport.scrollLeft = atlasX * afterScale - (anchorClientX != null ? anchorClientX - rect.left : viewport.clientWidth / 2)
    viewport.scrollTop = atlasY * afterScale - (anchorClientY != null ? anchorClientY - rect.top : viewport.clientHeight / 2)
  })
}

function zoomIn(): void {
  setZoom(zoomPercent.value * ZOOM_STEP_FACTOR)
}
function zoomOut(): void {
  setZoom(zoomPercent.value / ZOOM_STEP_FACTOR)
}
function zoomReset(): void {
  setZoom(100)
}
function handleZoomSelect(value: string): void {
  setZoom(Number(value))
}

function isEditableTarget(target: EventTarget | null): boolean {
  return target instanceof HTMLElement && (target.tagName === 'INPUT' || target.tagName === 'TEXTAREA')
}

function handleWindowKeydown(event: KeyboardEvent): void {
  if (isEditableTarget(event.target)) {
    return
  }
  if (event.key === 'Escape') {
    return
  }
  if (event.code === 'Space') {
    spaceDown.value = true
    return
  }
  if (event.key === '+' || event.key === '=') {
    event.preventDefault()
    zoomIn()
  } else if (event.key === '-' || event.key === '_') {
    event.preventDefault()
    zoomOut()
  } else if (event.key === '0') {
    zoomReset()
  }
}

function handleWindowKeyup(event: KeyboardEvent): void {
  if (event.code === 'Space') {
    spaceDown.value = false
  }
}

/** `Ctrl`/`Cmd`+rueda: zoom centrado en el cursor -- una rueda SIN modificador sigue siendo scroll nativo del viewport (pan vertical/horizontal estándar). */
function handleViewportWheel(event: WheelEvent): void {
  if (!event.ctrlKey && !event.metaKey) {
    return
  }
  event.preventDefault()
  const factor = event.deltaY < 0 ? ZOOM_WHEEL_FACTOR : 1 / ZOOM_WHEEL_FACTOR
  setZoom(zoomPercent.value * factor, event.clientX, event.clientY)
}

// -- Pan (barra espaciadora + arrastre) -----------------------------------
let panStart: { x: number; y: number; scrollLeft: number; scrollTop: number } | null = null

function handleViewportPointerDown(event: PointerEvent): void {
  if (!spaceDown.value || event.button !== 0) {
    return
  }
  const viewport = canvasViewportRef.value
  if (!viewport) {
    return
  }
  isPanning.value = true
  panStart = { x: event.clientX, y: event.clientY, scrollLeft: viewport.scrollLeft, scrollTop: viewport.scrollTop }
  viewport.setPointerCapture(event.pointerId)
  event.preventDefault()
}

function handleViewportPointerMove(event: PointerEvent): void {
  if (!isPanning.value || !panStart || !canvasViewportRef.value) {
    return
  }
  canvasViewportRef.value.scrollLeft = panStart.scrollLeft - (event.clientX - panStart.x)
  canvasViewportRef.value.scrollTop = panStart.scrollTop - (event.clientY - panStart.y)
}

function handleViewportPointerUp(): void {
  isPanning.value = false
  panStart = null
}

// -- Textura 3D en vivo (HU-26) -----------------------------------------
let dataTexture: DataTexture | null = null
let dataTexturePixelsRef: Uint8ClampedArray | null = null

function syncDataTexture(): void {
  const atlas = textureEditorStore.atlas
  if (!atlas) {
    return
  }
  const isNewBuffer = dataTexturePixelsRef !== atlas.pixels
  if (isNewBuffer) {
    dataTexture?.dispose()
    const nextTexture = new DataTexture(atlas.pixels, atlas.width, atlas.height, RGBAFormat)
    nextTexture.flipY = false
    nextTexture.magFilter = NearestFilter
    nextTexture.minFilter = NearestFilter
    nextTexture.generateMipmaps = false
    dataTexture = nextTexture
    dataTexturePixelsRef = atlas.pixels
    applyPreviewModel()
  } else if (dataTexture) {
    dataTexture.needsUpdate = true
  }
}

function applyPreviewModel(): void {
  threeViewportService.setModel(props.model, null, dataTexture, textureSelectionStore.selectedFace)
}

// -- Bitmap 2D real (canvas, sin overlay) --------------------------------
function redraw(sourcePixels?: Uint8ClampedArray): void {
  const canvas = canvasRef.value
  const atlas = textureEditorStore.atlas
  if (!canvas || !atlas) {
    return
  }
  const ctx = canvas.getContext('2d')
  if (!ctx) {
    // jsdom (tests) o un navegador sin 2D real -- la verdad de los
    // píxeles vive en atlas.pixels/los buffers de trazo, no acá.
    return
  }
  const pixels = sourcePixels ?? atlas.pixels
  ctx.putImageData(new ImageData(new Uint8ClampedArray(pixels), atlas.width, atlas.height), 0, 0)
}

function loadModelAtlas(model: MobProjectModel): void {
  textureEditorStore.loadAtlas(model.uv.textureWidth, model.uv.textureHeight)
  syncDataTexture()
  redraw()
  saveState.value = 'saved'
}

// -- Selección cruzada cuboid<->UV en el preview 3D (ticket 049, HU-25) --
let previewPointerDownPosition: { x: number; y: number } | null = null

function handlePreviewPointerDown(event: PointerEvent): void {
  previewPointerDownPosition = { x: event.clientX, y: event.clientY }
}

function handlePreviewClick(event: MouseEvent): void {
  if (previewPointerDownPosition) {
    const distance = Math.hypot(event.clientX - previewPointerDownPosition.x, event.clientY - previewPointerDownPosition.y)
    if (distance > CLICK_DRAG_THRESHOLD_PX) {
      return
    }
  }
  const pick: CuboidFaceRef | null = threeViewportService.pickCuboidFaceAt(event.clientX, event.clientY)
  textureSelectionStore.selectFace(pick)
}

onMounted(() => {
  loadModelAtlas(props.model)
  if (previewContainerRef.value) {
    threeViewportService.attachTo(previewContainerRef.value)
    threeViewportService.startRenderLoop()
    previewContainerRef.value.addEventListener('pointerdown', handlePreviewPointerDown)
    previewContainerRef.value.addEventListener('click', handlePreviewClick)
  }
  window.addEventListener('keydown', handleWindowKeydown)
  window.addEventListener('keyup', handleWindowKeyup)
})

watch(
  () => props.model.mobId,
  () => loadModelAtlas(props.model),
)

watch(
  () => textureSelectionStore.selectedFace,
  () => applyPreviewModel(),
)

onBeforeUnmount(() => {
  previewContainerRef.value?.removeEventListener('pointerdown', handlePreviewPointerDown)
  previewContainerRef.value?.removeEventListener('click', handlePreviewClick)
  window.removeEventListener('keydown', handleWindowKeydown)
  window.removeEventListener('keyup', handleWindowKeyup)
  if (toolToastTimer) {
    clearTimeout(toolToastTimer)
  }
  threeViewportService.detach()
  dataTexture?.dispose()
})

// -- Conversión de coordenadas de pantalla a píxeles del ATLAS ----------
// Cálculo de RATIO (canvas.width intrínseco / tamaño CSS mostrado) --
// zoom-agnóstico por construcción: sigue siendo válido en cualquier nivel
// de `zoomPercent` sin ningún cambio (ver docstring de cabecera).
function canvasPointToAtlas(event: PointerEvent): { x: number; y: number } | null {
  const canvas = canvasRef.value
  const atlas = textureEditorStore.atlas
  if (!canvas || !atlas) {
    return null
  }
  const rect = canvas.getBoundingClientRect()
  if (rect.width === 0 || rect.height === 0) {
    return null
  }
  const scaleX = canvas.width / rect.width
  const scaleY = canvas.height / rect.height
  const x = Math.floor((event.clientX - rect.left) * scaleX)
  const y = Math.floor((event.clientY - rect.top) * scaleY)
  if (x < 0 || y < 0 || x >= atlas.width || y >= atlas.height) {
    return null
  }
  return { x, y }
}

function activeColor(): RgbaColor {
  return activeTool.value === 'eraser' ? TRANSPARENT : hexToRgba(activeColorHex.value)
}

// -- Guardado (ticket 058, reutiliza el flush de 056) --------------------
let toolToastTimer: ReturnType<typeof setTimeout> | null = null

function markDirty(): void {
  if (saveState.value !== 'saving') {
    saveState.value = 'dirty'
  }
}

async function handleSave(): Promise<void> {
  if (saveState.value === 'saving') {
    return
  }
  saveState.value = 'saving'
  try {
    const flushedModel = await flushPaintedTexture(props.model)
    if (flushedModel !== props.model) {
      draftModelStore.commitExternalModel(flushedModel)
    }
    await saveRevision(flushedModel.mobId, flushedModel)
    saveState.value = 'saved'
  } catch {
    saveState.value = 'error'
    return
  }

  // Thumbnail: side-effect best-effort, nunca revierte el guardado ya
  // completado arriba (mismo criterio que `EditorToolbar.handleSave`).
  try {
    const png = await threeViewportService.captureThumbnail()
    await uploadThumbnail(props.model.mobId, png)
  } catch (error) {
    console.warn('[TextureCanvas] no se pudo generar/subir el thumbnail:', error)
  }
}

function openAiGenerator(): void {
  router.push(`/projects/${props.model.projectId}/mobs/${props.model.mobId}/texture/generate-ai`)
}

// -- Trazo de Pincel/Borrador: UN solo recordPatch() por trazo completo --
let strokeBeforeFull: Uint8ClampedArray | null = null
let strokeWorking: Uint8ClampedArray | null = null
let strokeBounds: PixelBounds = createEmptyBounds()
let lastPoint: { x: number; y: number } | null = null

function beginStroke(point: { x: number; y: number }): void {
  const atlas = textureEditorStore.atlas
  if (!atlas) {
    return
  }
  strokeBeforeFull = textureEditorStore.readRegion({ x: 0, y: 0, width: atlas.width, height: atlas.height })!
  strokeWorking = strokeBeforeFull.slice()
  strokeBounds = createEmptyBounds()
  lastPoint = point
  stampSquare({ pixels: strokeWorking, width: atlas.width, height: atlas.height }, point, brushSize.value, activeColor(), strokeBounds)
  redraw(strokeWorking)
}

function continueStroke(point: { x: number; y: number }): void {
  const atlas = textureEditorStore.atlas
  if (!atlas || !strokeWorking || !lastPoint) {
    return
  }
  stampLine(
    { pixels: strokeWorking, width: atlas.width, height: atlas.height },
    lastPoint,
    point,
    brushSize.value,
    activeColor(),
    strokeBounds,
  )
  lastPoint = point
  redraw(strokeWorking)
}

function finishStroke(): void {
  const atlas = textureEditorStore.atlas
  const rect = boundsToRect(strokeBounds)
  if (atlas && strokeBeforeFull && strokeWorking && rect) {
    const before = readRectFrom(strokeBeforeFull, atlas.width, rect)
    const after = readRectFrom(strokeWorking, atlas.width, rect)
    textureEditorStore.recordPatch(rect, before, after) // ÚNICA llamada del trazo completo
    syncDataTexture()
    redraw()
    markDirty()
  }
  strokeBeforeFull = null
  strokeWorking = null
  lastPoint = null
  strokeBounds = createEmptyBounds()
}

function handlePointerDown(event: PointerEvent): void {
  if (spaceDown.value) {
    return // barra espaciadora mantenida -- este pointerdown es para pan, no para pintar (ver handleViewportPointerDown).
  }
  const atlas = textureEditorStore.atlas
  const point = canvasPointToAtlas(event)
  if (!atlas || !point) {
    return
  }

  if (activeTool.value === 'eyedropper') {
    const color = pickColorAt(atlas, point.x, point.y)
    if (color) {
      activeColorHex.value = rgbaToHex(color)
      showEyedropperToast(activeColorHex.value)
    }
    return
  }

  if (activeTool.value === 'fill') {
    const result = computeFloodFill(atlas, point, hexToRgba(activeColorHex.value))
    if (result) {
      textureEditorStore.recordPatch(result.rect, result.beforePixels, result.afterPixels) // ÚNICA llamada del fill
      syncDataTexture()
      redraw()
      markDirty()
    }
    return
  }

  if (typeof canvasRef.value?.setPointerCapture === 'function') {
    canvasRef.value.setPointerCapture(event.pointerId)
  }
  beginStroke(point)
}

function handlePointerMove(event: PointerEvent): void {
  if (!strokeWorking) {
    return
  }
  const point = canvasPointToAtlas(event)
  if (point) {
    continueStroke(point)
  }
}

function handlePointerUp(event: PointerEvent): void {
  const canvas = canvasRef.value
  if (canvas && typeof canvas.hasPointerCapture === 'function' && canvas.hasPointerCapture(event.pointerId)) {
    canvas.releasePointerCapture(event.pointerId)
  }
  finishStroke()
}

/** AC ticket 058: el cuentagotas debe dar una confirmación VISIBLE de qué color capturó -- antes era un cambio silencioso (solo el swatch, sin ningún otro indicio). */
function showEyedropperToast(hex: string): void {
  pickedColorToast.value = `Cuentagotas: color capturado ${hex}`
  if (toolToastTimer) {
    clearTimeout(toolToastTimer)
  }
  toolToastTimer = setTimeout(() => {
    pickedColorToast.value = null
  }, EYEDROPPER_TOAST_MS)
}
</script>

<template>
  <div class="texture-canvas">
    <div class="texture-canvas__toolbar">
      <div class="texture-canvas__tb-group texture-canvas__tb-group--region">
        <GSelect v-model="selectedRegionKey" :options="regionOptions" label="Región UV a enfocar" />
      </div>

      <span class="texture-canvas__tb-sep" aria-hidden="true"></span>

      <fieldset class="texture-canvas__tb-group" aria-label="Herramientas de pintado">
        <legend class="texture-canvas__sr-only">Herramientas de pintado</legend>
        <IconButton label="Pincel" :active="activeTool === 'brush'" @click="activeTool = 'brush'"><IconBrush /></IconButton>
        <IconButton label="Borrador" :active="activeTool === 'eraser'" @click="activeTool = 'eraser'"><IconEraser /></IconButton>
        <IconButton label="Cubeta" :active="activeTool === 'fill'" @click="activeTool = 'fill'"><IconBucket /></IconButton>
        <IconButton label="Selector de color (eyedropper)" :active="activeTool === 'eyedropper'" @click="activeTool = 'eyedropper'"><IconEyedropper /></IconButton>
      </fieldset>

      <div class="texture-canvas__tb-group">
        <IconButton label="Cuadrícula" :active="showGrid" @click="showGrid = !showGrid"><IconGrid /></IconButton>
      </div>

      <div class="texture-canvas__tb-group texture-canvas__tb-group--brush">
        <GSelect :model-value="String(brushSize)" :options="brushSizeOptions" label="Tamaño de pincel en píxeles del atlas" @update:model-value="(v) => (brushSize = Number(v))" />
      </div>

      <TextureColorPicker v-model="activeColorHex" />

      <span class="texture-canvas__tb-sep" aria-hidden="true"></span>

      <div class="texture-canvas__tb-group texture-canvas__zoomctl">
        <IconButton label="Alejar zoom" shortcut="-" @click="zoomOut"><IconZoomOut /></IconButton>
        <GSelect :model-value="String(zoomPercent)" :options="zoomOptions" :placeholder="`${zoomPercent}%`" label="Nivel de zoom del lienzo" @update:model-value="handleZoomSelect" />
        <IconButton label="Acercar zoom" shortcut="+" @click="zoomIn"><IconZoomIn /></IconButton>
        <IconButton label="Restablecer zoom" shortcut="0" @click="zoomReset"><IconZoomReset /></IconButton>
      </div>

      <span class="texture-canvas__tb-sep" aria-hidden="true"></span>

      <div class="texture-canvas__tb-import">
        <TextureImportPanel :target-label="importTargetLabel" :resolve-target="resolveImportTarget" @imported="handleImported" />
      </div>

      <GButton variant="ghost" @click="openAiGenerator"><template #icon><IconSparkle :size="16" /></template>Generar con IA</GButton>

      <div class="texture-canvas__tb-grow"></div>

      <TextureSaveStatus :state="saveState" />
      <GButton variant="primary" :disabled="saveState === 'saving'" @click="handleSave"><template #icon><IconSave :size="16" /></template>{{ saveState === 'saving' ? 'Guardando…' : 'Guardar' }}</GButton>
    </div>

    <div class="texture-canvas__main">
      <section class="texture-canvas__canvas-panel" aria-label="Lienzo de textura">
        <div
          ref="canvasViewportRef"
          class="texture-canvas__viewport app-scroll"
          :class="{ 'texture-canvas__viewport--pan-ready': spaceDown, 'texture-canvas__viewport--panning': isPanning }"
          @wheel="handleViewportWheel"
          @pointerdown="handleViewportPointerDown"
          @pointermove="handleViewportPointerMove"
          @pointerup="handleViewportPointerUp"
          @pointercancel="handleViewportPointerUp"
        >
          <div class="texture-canvas__stage" :style="{ width: `${stageWidthPx}px`, height: `${stageHeightPx}px` }">
            <canvas ref="canvasRef" :width="atlasWidth" :height="atlasHeight" class="texture-canvas__bitmap" aria-label="Atlas de textura del mob -- superficie de pintado" @pointerdown="handlePointerDown" @pointermove="handlePointerMove" @pointerup="handlePointerUp" @pointercancel="handlePointerUp"></canvas>
            <svg class="texture-canvas__overlay" :viewBox="`0 0 ${atlasWidth} ${atlasHeight}`" preserveAspectRatio="none" aria-hidden="true">
              <g v-if="showGrid" class="texture-canvas__grid">
                <line v-for="x in gridLinesX" :key="`gx-${x}`" :x1="x" y1="0" :x2="x" :y2="atlasHeight" />
                <line v-for="y in gridLinesY" :key="`gy-${y}`" x1="0" :y1="y" :x2="atlasWidth" :y2="y" />
              </g>
              <g>
                <rect v-for="region in selectableRegions" :key="regionKey(region)" v-bind="regionRect(region)" class="texture-canvas__region" :class="{ 'texture-canvas__region--selected': isRegionSelected(region) }" />
              </g>
            </svg>
          </div>
          <p v-if="pickedColorToast" class="texture-canvas__toast" role="status" aria-live="polite">{{ pickedColorToast }}</p>
        </div>
      </section>

      <aside class="texture-canvas__preview-panel" aria-label="Preview 3D">
        <div ref="previewContainerRef" class="texture-canvas__preview" aria-hidden="true"></div>
      </aside>
    </div>

    <div class="texture-canvas__statusbar">
      <span>Región: <strong>{{ activeRegionLabel }}</strong></span>
      <span class="texture-canvas__statusbar-sep" aria-hidden="true">|</span>
      <span>Zoom: <strong>{{ zoomPercent }}%</strong></span>
      <span class="texture-canvas__statusbar-sep" aria-hidden="true">|</span>
      <span>Herramienta: <strong>{{ activeToolLabel }}</strong></span>
      <span class="texture-canvas__statusbar-grow"></span>
      <TextureSaveStatus :state="saveState" />
    </div>
  </div>
</template>

<style scoped>
/* Sin borde/radio/`overflow: hidden` en la raíz a propósito (mismo
   criterio que la versión 047/050 de este componente, que tampoco los
   tenía): `MobEditor.vue` ya le da su propio padding a `.mob-editor`, y
   los paneles desplegables de `GSelect`/`TextureColorPicker` en la
   toolbar son `position: absolute` -- necesitan poder escapar
   visualmente de este contenedor, un `overflow: hidden` acá los
   recortaría contra el borde del panel. */
.texture-canvas {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  background: var(--bg);
}

/* ---- Toolbar horizontal (zona 1) --------------------------------- */
.texture-canvas__toolbar {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-2) var(--space-3);
  background: var(--panel);
  border-bottom: var(--border-width) solid var(--border);
  flex-wrap: wrap;
}

.texture-canvas__tb-group {
  display: flex;
  align-items: center;
  gap: 2px;
  margin: 0;
  padding: 2px;
  border: none;
  background: var(--surface);
  border: var(--border-width) solid var(--border);
  border-radius: var(--radius-md);
  min-width: 0;
}

.texture-canvas__tb-group--region {
  min-width: 168px;
}

.texture-canvas__tb-group--brush {
  width: 104px;
}

.texture-canvas__tb-sep {
  width: var(--border-width);
  align-self: stretch;
  margin: var(--space-1) 2px;
  background: var(--border);
}

.texture-canvas__tb-grow {
  flex: 1 1 auto;
}

.texture-canvas__tb-import {
  display: contents;
}

.texture-canvas__zoomctl {
  display: flex;
  align-items: center;
  gap: 2px;
}

.texture-canvas__zoomctl :deep(.g-select) {
  min-width: 84px;
}

.texture-canvas__sr-only {
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

/* Confirmación del import de PNG -- flota debajo del botón trigger en vez
   de empujar el resto de la toolbar (la fila horizontal no tiene alto
   variable). `TextureImportPanel.vue` no se tocó -- se posiciona su
   bloque de confirmación desde afuera vía `:deep()`. */
.texture-canvas__tb-import :deep(.texture-import-panel) {
  position: relative;
}

.texture-canvas__tb-import :deep(.texture-import-panel__confirm) {
  position: absolute;
  top: calc(100% + var(--space-2));
  left: 0;
  z-index: 50;
  width: 260px;
  box-shadow: var(--shadow-md);
}

/* ---- Zona central: lienzo (zona 2, dominante) + preview 3D (zona 3) --- */
.texture-canvas__main {
  flex: 1;
  min-height: 0;
  display: flex;
}

.texture-canvas__canvas-panel {
  flex: 1;
  min-width: 0;
  position: relative;
  display: flex;
  background: var(--bg);
  border-right: var(--border-width) solid var(--border);
}

.texture-canvas__viewport {
  flex: 1;
  min-width: 0;
  overflow: auto;
  position: relative;
  background-image:
    linear-gradient(rgba(255, 255, 255, 0.025) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 255, 255, 0.025) 1px, transparent 1px);
  background-size: 24px 24px;
  cursor: crosshair;
}

.texture-canvas__viewport--pan-ready {
  cursor: grab;
}

.texture-canvas__viewport--panning {
  cursor: grabbing;
}

.texture-canvas__stage {
  position: absolute;
  top: var(--space-6);
  left: var(--space-6);
  box-shadow: 0 0 0 1px var(--border), var(--shadow-md);
}

.texture-canvas__bitmap {
  display: block;
  width: 100%;
  height: 100%;
  image-rendering: pixelated;
  background-image:
    linear-gradient(45deg, #2a2a2a 25%, transparent 25%), linear-gradient(-45deg, #2a2a2a 25%, transparent 25%),
    linear-gradient(45deg, transparent 75%, #2a2a2a 75%), linear-gradient(-45deg, transparent 75%, #2a2a2a 75%);
  background-size: 16px 16px;
  background-position:
    0 0,
    0 8px,
    8px -8px,
    -8px 0;
  touch-action: none;
  cursor: crosshair;
}

.texture-canvas__overlay {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
}

.texture-canvas__grid line {
  stroke: rgba(255, 255, 255, 0.25);
  stroke-width: 0.5;
  vector-effect: non-scaling-stroke;
}

.texture-canvas__region {
  fill: none;
  stroke: var(--muted);
  stroke-width: 0.5;
  vector-effect: non-scaling-stroke;
}

.texture-canvas__region--selected {
  stroke: var(--accent);
  stroke-width: 1.5;
  fill: var(--accent-soft);
}

.texture-canvas__toast {
  position: absolute;
  top: var(--space-3);
  left: 50%;
  transform: translateX(-50%);
  margin: 0;
  background: rgba(17, 24, 32, 0.92);
  border: var(--border-width) solid var(--border);
  color: var(--text);
  font-size: var(--text-xs);
  padding: 6px 12px;
  border-radius: 999px;
  pointer-events: none;
  z-index: 10;
}

.texture-canvas__preview-panel {
  width: 320px;
  flex: 0 0 auto;
  display: flex;
  flex-direction: column;
  background: var(--panel);
  min-width: 0;
}

.texture-canvas__preview {
  flex: 1;
  min-height: 0;
}

/* ---- Barra de estado inferior (opcional, ticket 058) ------------------ */
.texture-canvas__statusbar {
  flex: 0 0 auto;
  height: var(--statusbar-h, 30px);
  display: flex;
  align-items: center;
  gap: var(--space-4);
  padding: 0 var(--space-4);
  background: var(--panel);
  border-top: var(--border-width) solid var(--border);
  font-size: var(--text-xs);
  color: var(--muted);
  font-family: var(--font-mono);
}

.texture-canvas__statusbar-sep {
  color: var(--border);
  font-family: var(--font-sans);
}

.texture-canvas__statusbar-grow {
  flex: 1;
}

.texture-canvas__statusbar strong {
  color: var(--text);
  font-weight: 600;
  font-family: var(--font-mono);
}
</style>
