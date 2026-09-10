<script setup lang="ts">
/**
 * Ticket 047 -- editor de textura/UV manual: el canvas 2D real y sus
 * herramientas de pintado (Diseño técnico Sección 9 de
 * docs/definiciones/galgoth-studio-fase3-textura.md, HU-24/26/27).
 * Monta el mecanismo de Undo/Redo del ticket 046 (textureEditorStore)
 * sobre un atlas real y visible por primera vez.
 *
 * Alcance explícito del ticket 047 (ver "## Hecho" del ticket para el
 * detalle completo de qué se implementó/difirió): color picker + paleta,
 * Pincel, Borrador, Cubeta (flood-fill real), Eyedropper, toggle de
 * cuadrícula. NO incluía selección cruzada cuboid-UV -- ver ticket 049
 * más abajo, ya integrada en este mismo componente --, NI import de PNG
 * (ticket 048), NI Selección/Copiar-pegar/Undo-Redo con UI propia (HU-27
 * los menciona pero la sección "Qué implementar" del ticket 047 no los
 * lista -- el mecanismo de textureEditorStore.undo/redo queda disponible
 * y correctamente alimentado por cada herramienta vía recordPatch(), pero
 * sin botón/atajo de teclado en este componente). El ticket 047 tampoco
 * ensambla la pantalla completa del mockup 07 (eso es el ticket 050) --
 * este componente es la pieza que 050 va a montar.
 *
 * Arquitectura de dos capas superpuestas, deliberada:
 * - canvas (bitmap real, canvasRef): tamaño intrínseco EXACTO al atlas
 *   (atlas.width/atlas.height en px de canvas, nunca más) -- ahí se
 *   pintan los píxeles reales, 1:1, sin escalar. Se muestra más grande
 *   en pantalla vía CSS (image-rendering: pixelated) para que un atlas
 *   chico (64x64) sea usable -- ESTO NO ES UN ZOOM interactivo (no hay
 *   control de zoom en este ticket, dicho explícitamente), es una
 *   escala CSS fija; la conversión de coordenadas de puntero
 *   (canvasPointToAtlas) ya tiene en cuenta ese factor de escala tal
 *   como lo necesitaría un zoom real (AC: tamaño de pincel en píxeles
 *   del atlas, verificado con un canvas escalado).
 * - svg overlay (guía UV + grid + resaltado de región): capa SEPARADA,
 *   pointer-events: none (los eventos de puntero siguen yendo al canvas
 *   de abajo). Nunca toca atlas.pixels -- por construcción, la
 *   grilla/etiquetas JAMÁS pueden filtrarse al bitmap real (AC: "grid
 *   nunca se persiste").
 *
 * Cada trazo de Pincel/Borrador pinta sobre una copia de trabajo
 * (strokeWorking, clon de atlas.pixels tomado en pointerdown vía
 * textureEditorStore.readRegion() sobre el rect COMPLETO del atlas --
 * reutiliza la función tal como sugiere el ticket 046) -- el atlas real
 * NO cambia hasta pointerup, cuando se hace la ÚNICA llamada a
 * recordPatch() del trazo completo. La Cubeta es atómica (un solo
 * evento), así que llama a recordPatch() directo.
 *
 * Ticket 049 (HU-25, Diseño técnico §14): selección cruzada cuboid↔UV.
 * `selectedRegionKey` deja de ser un `ref` local -- ahora es un computed
 * de lectura/escritura sobre `textureSelectionStore.selectedFace`
 * (`textureSelectionStore.ts`, store nuevo y separado de
 * `selectionStore.ts`), así que el MISMO estado alimenta 3 superficies
 * sin duplicar nada: el `<select>` de región (ya existía), el resaltado
 * SVG de esa región (ya existía) y ahora también el highlight de esa cara
 * en el preview 3D (`buildMobGroup`'s `selectedFace`, ver
 * `buildMobScene.ts`). Un clic en una cara del preview 3D (`handlePreviewClick`,
 * mismo patrón click-vs-drag que `ThreeViewport.vue`) llama a
 * `pickCuboidFaceAt` y escribe al MISMO store -- de ahí sale la
 * bidireccionalidad de la selección cruzada.
 */
import { DataTexture, RGBAFormat, NearestFilter } from 'three'
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import type { FaceName, MobProjectModel } from '../../domain/MobProjectModel'
import { threeViewportService } from '../../viewport/ThreeViewportService'
import type { CuboidFaceRef } from '../../viewport/textureUvMapping'
import IconButton from '../../design-system/components/IconButton.vue'
import IconBrush from '../../design-system/icons/IconBrush.vue'
import IconBucket from '../../design-system/icons/IconBucket.vue'
import IconEraser from '../../design-system/icons/IconEraser.vue'
import IconEyedropper from '../../design-system/icons/IconEyedropper.vue'
import IconGrid from '../../design-system/icons/IconGrid.vue'
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
import { readRectFrom } from './textureRectBuffer'
import { useTextureEditorStore } from './textureEditorStore'
import { useTextureSelectionStore } from './textureSelectionStore'

type Tool = 'brush' | 'eraser' | 'fill' | 'eyedropper'

// Umbral de movimiento del mouse entre pointerdown y click en el preview
// 3D -- por encima de esto se interpreta como arrastre de órbita
// (OrbitControls), no como un click de selección de cara. Mismo criterio
// que `ThreeViewport.vue` (ticket 017).
const CLICK_DRAG_THRESHOLD_PX = 5

const PALETTE: string[] = ['#f3f6f8', '#0b0f14', '#e0574c', '#f2c66d', '#48e5a0', '#4d8bf0', '#a35bd6', '#8a5a3b']
const DEFAULT_COLOR = PALETTE[2]!
const GRID_STEP_PX = 8
const MIN_BRUSH_SIZE = 1
const MAX_BRUSH_SIZE = 32

const props = defineProps<{ model: MobProjectModel }>()

const textureEditorStore = useTextureEditorStore()
const textureSelectionStore = useTextureSelectionStore()

const canvasRef = ref<HTMLCanvasElement>()
const previewContainerRef = ref<HTMLDivElement>()

const activeTool = ref<Tool>('brush')
const activeColorHex = ref(DEFAULT_COLOR)
const brushSize = ref(4)
const showGrid = ref(false)

/**
 * Puente entre el `<select>` (formato `regionKey`, ya existente desde el
 * ticket 047) y `textureSelectionStore.selectedFace` (formato
 * `{cuboidId, face}`, ticket 049) -- el template no cambia, sigue usando
 * `v-model="selectedRegionKey"`.
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

function swatchLabel(color: string): string {
  return `Color ${color}`
}

// -- Textura 3D en vivo (HU-26) -----------------------------------------
// DataTexture envuelve DIRECTO el Uint8ClampedArray del atlas -- sin
// canvas 2D intermedio (jsdom no implementa un contexto 2D real, ver
// docstring de pixelTools.ts; DataTexture no depende de DOM en absoluto,
// así que esto es 100% testable). flipY = false: fila 0 del buffer
// (arriba en pixel-space) es la fila 0 de la textura, sin invertir -- ver
// textureUvMapping.ts para el lado correspondiente del mapeo UV.
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

/**
 * Ticket 049 (HU-25): reconstruye el mob del preview 3D con el highlight
 * de `textureSelectionStore.selectedFace` vigente -- separado de la rama
 * `else` (needsUpdate-only) de `syncDataTexture` a propósito: cada trazo
 * de pintado NO debe reconstruir todos los meshes (esa optimización del
 * ticket 047 sigue intacta), pero un cambio de selección de cara sí
 * necesita reconstruir (el highlight se agrega dentro de `buildMobGroup`).
 */
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
  // Ticket 034 (mismo criterio, sin código especial): tanto un mob con
  // revision_number >= 1 como un draft vacío en memoria llegan acá con
  // model.uv.textureWidth/textureHeight ya válidos (emptyMobProjectModel
  // usa 128x128 por defecto) -- nunca bloqueado. No existe todavía ningún
  // backend que devuelva bytes de una textura ya pintada (GET/PUT
  // /texture es HU-30/31, fuera de alcance de 040-046) -- se carga
  // siempre en blanco al tamaño real del atlas, nunca un tamaño fijo
  // inventado (ver "## Hecho" del ticket para el detalle de esta decisión).
  textureEditorStore.loadAtlas(model.uv.textureWidth, model.uv.textureHeight)
  syncDataTexture()
  redraw()
}

// -- Selección cruzada cuboid<->UV en el preview 3D (ticket 049, HU-25) --
let previewPointerDownPosition: { x: number; y: number } | null = null

function handlePreviewPointerDown(event: PointerEvent): void {
  previewPointerDownPosition = { x: event.clientX, y: event.clientY }
}

/** Mismo criterio click-vs-drag que `ThreeViewport.vue` (ticket 017) -- un arrastre de órbita (`OrbitControls`) nunca debe interpretarse como una selección de cara. */
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
})

watch(
  () => props.model.mobId,
  () => loadModelAtlas(props.model),
)

// Ticket 049 (HU-25 AC "seleccionar una región UV en el editor 2D resalta
// la cara en el preview 3D"): cualquier cambio de selección (desde el
// dropdown, desde un clic en el preview, o deseleccionar) reconstruye el
// mob del preview con el highlight vigente -- ver `applyPreviewModel`.
watch(
  () => textureSelectionStore.selectedFace,
  () => applyPreviewModel(),
)

onBeforeUnmount(() => {
  previewContainerRef.value?.removeEventListener('pointerdown', handlePreviewPointerDown)
  previewContainerRef.value?.removeEventListener('click', handlePreviewClick)
  threeViewportService.detach()
  dataTexture?.dispose()
})

// -- Conversión de coordenadas de pantalla a píxeles del ATLAS ----------
// (nunca de pantalla -- AC del ticket). getBoundingClientRect() del
// canvas puede ser MÁS GRANDE que canvas.width/height (la escala CSS
// fija descrita arriba) -- dividir por esa relación es exactamente lo
// que un zoom interactivo futuro necesitaría, aunque este ticket no
// implemente ningún control de zoom.
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
    // Llamada explícita (no vía watcher): un shallowRef de Pinia mutado
    // in-place + triggerRef() no siempre re-dispara un watch() externo
    // de forma confiable entre el store y este componente -- se refresca
    // el preview 3D/canvas 2D acá mismo, justo después del ÚNICO commit
    // real del trazo (AC HU-26: "al completarse el trazo").
    syncDataTexture()
    redraw()
  }
  strokeBeforeFull = null
  strokeWorking = null
  lastPoint = null
  strokeBounds = createEmptyBounds()
}

function handlePointerDown(event: PointerEvent): void {
  const atlas = textureEditorStore.atlas
  const point = canvasPointToAtlas(event)
  if (!atlas || !point) {
    return
  }

  if (activeTool.value === 'eyedropper') {
    const color = pickColorAt(atlas, point.x, point.y)
    if (color) {
      activeColorHex.value = rgbaToHex(color)
    }
    return
  }

  if (activeTool.value === 'fill') {
    const result = computeFloodFill(atlas, point, hexToRgba(activeColorHex.value))
    if (result) {
      textureEditorStore.recordPatch(result.rect, result.beforePixels, result.afterPixels) // ÚNICA llamada del fill
      syncDataTexture()
      redraw()
    }
    return
  }

  // `setPointerCapture`/`hasPointerCapture` no existen en jsdom (entorno
  // de test) -- guardado defensivo, no solo por eso: tampoco son
  // universales en runtimes embebidos. Sin captura, el trazo sigue
  // funcionando igual dentro del canvas; solo se pierde la continuidad
  // si el puntero sale de sus límites a mitad de un drag rápido.
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
</script>

<template>
  <div class="texture-canvas">
    <aside class="texture-canvas__tools">
      <label class="texture-canvas__field">
        Región
        <select v-model="selectedRegionKey" class="texture-canvas__select" aria-label="Región UV a enfocar">
          <option :value="ALL_REGIONS_VALUE">Todas las caras</option>
          <option v-for="region in selectableRegions" :key="regionKey(region)" :value="regionKey(region)">{{ region.label }}</option>
        </select>
      </label>

      <fieldset class="texture-canvas__tool-row">
        <legend class="texture-canvas__sr-only">Herramientas de pintado</legend>
        <IconButton label="Pincel" :active="activeTool === 'brush'" @click="activeTool = 'brush'"><IconBrush /></IconButton>
        <IconButton label="Borrador" :active="activeTool === 'eraser'" @click="activeTool = 'eraser'"><IconEraser /></IconButton>
        <IconButton label="Cubeta" :active="activeTool === 'fill'" @click="activeTool = 'fill'"><IconBucket /></IconButton>
        <IconButton label="Selector de color (eyedropper)" :active="activeTool === 'eyedropper'" @click="activeTool = 'eyedropper'"><IconEyedropper /></IconButton>
        <IconButton label="Cuadrícula" :active="showGrid" @click="showGrid = !showGrid"><IconGrid /></IconButton>
      </fieldset>

      <label class="texture-canvas__field" aria-label="Color activo">
        Color activo
        <input v-model="activeColorHex" type="color" class="texture-canvas__color-input" aria-label="Color activo" />
      </label>

      <fieldset class="texture-canvas__palette">
        <legend class="texture-canvas__sr-only">Paleta de colores</legend>
        <button v-for="color in PALETTE" :key="color" type="button" class="texture-canvas__swatch" :class="{ 'texture-canvas__swatch--active': color.toLowerCase() === activeColorHex.toLowerCase() }" :style="{ backgroundColor: color }" :aria-label="swatchLabel(color)" :aria-pressed="color.toLowerCase() === activeColorHex.toLowerCase()" @click="activeColorHex = color"></button>
      </fieldset>

      <label class="texture-canvas__field" aria-label="Tamaño de pincel en píxeles del atlas">
        Tamaño de pincel (píxeles del atlas)
        <input v-model.number="brushSize" type="number" aria-label="Tamaño de pincel en píxeles del atlas" :min="MIN_BRUSH_SIZE" :max="MAX_BRUSH_SIZE" class="texture-canvas__number-input" />
      </label>
    </aside>

    <div class="texture-canvas__stage-wrapper">
      <div class="texture-canvas__stage" :style="{ aspectRatio: `${atlasWidth} / ${atlasHeight}` }">
        <!-- S6819/S6843: este canvas es una superficie de dibujo interactiva
             (pointerdown/move/up), no una imagen estática -- por eso no
             lleva un rol de tipo imagen: sería semánticamente incorrecto
             asignar un rol no interactivo a un elemento con handlers de
             puntero reales. El aria-label describe el contenido igual,
             sin reclamar un rol que no le corresponde. -->
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
    </div>

    <div ref="previewContainerRef" class="texture-canvas__preview" aria-hidden="true"></div>
  </div>
</template>

<style scoped>
.texture-canvas {
  display: grid;
  grid-template-columns: 220px 1fr 1fr;
  gap: var(--space-4);
  height: 100%;
  min-height: 0;
}

.texture-canvas__tools {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  padding: var(--space-4);
  background: var(--panel);
  border: var(--border-width) solid var(--border);
  border-radius: var(--radius-lg);
  overflow-y: auto;
}

.texture-canvas__field {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
  font-size: var(--text-sm);
  color: var(--muted);
}

.texture-canvas__select,
.texture-canvas__number-input {
  min-height: var(--hit-target-min);
  padding: 0 var(--space-3);
  background: var(--surface);
  border: var(--border-width) solid var(--border);
  border-radius: var(--radius-md);
  color: var(--text);
  font-size: var(--text-base);
}

.texture-canvas__color-input {
  width: 100%;
  height: var(--hit-target-min);
  padding: var(--space-1);
  background: var(--surface);
  border: var(--border-width) solid var(--border);
  border-radius: var(--radius-md);
  cursor: pointer;
}

/* `<fieldset>` real (S6819/S6843, en vez de role="group" sobre un div) --
   resetea el borde/padding por defecto del navegador para conservar
   exactamente el layout que ya tenían estos contenedores como <div>. */
.texture-canvas__tool-row,
.texture-canvas__palette {
  border: none;
  margin: 0;
  padding: 0;
}

.texture-canvas__tool-row {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-1);
}

.texture-canvas__palette {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

/* `<legend>` de cada fieldset -- visible para lectores de pantalla, fuera
   del flujo visual (el nombre del grupo ya es evidente por las herramientas
   que contiene, mismo criterio que un aria-label ya usaba antes). */
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

.texture-canvas__swatch {
  width: 28px;
  height: 28px;
  border-radius: var(--radius-sm);
  border: 2px solid var(--border);
  cursor: pointer;
  padding: 0;
}

.texture-canvas__swatch--active {
  border-color: var(--accent);
  box-shadow: 0 0 0 2px var(--accent-soft);
}

.texture-canvas__stage-wrapper {
  display: flex;
  align-items: center;
  justify-content: center;
  min-width: 0;
  min-height: 0;
  background: var(--surface);
  border: var(--border-width) solid var(--border);
  border-radius: var(--radius-lg);
  padding: var(--space-4);
}

.texture-canvas__stage {
  position: relative;
  width: 100%;
  max-width: 512px;
  max-height: 100%;
}

.texture-canvas__bitmap {
  position: absolute;
  inset: 0;
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

.texture-canvas__preview {
  min-height: 280px;
  border: var(--border-width) solid var(--border);
  border-radius: var(--radius-lg);
  background: var(--surface-2);
}
</style>
