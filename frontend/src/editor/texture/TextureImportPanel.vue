<script setup lang="ts">
/**
 * Ticket 048 -- import de PNG sobre una región UV seleccionada o sobre
 * el atlas completo (Diseño técnico §8, HU-28). Montado por
 * `TextureCanvas.vue`, que decide el destino (`resolveTarget`) según el
 * selector de región ya existente (047): "Todas las caras" -> atlas
 * completo, una región puntual -> esa región.
 *
 * Flujo (mismo para los dos casos -- región o atlas completo, AC A/B):
 * 1. El usuario elige un archivo PNG.
 * 2. Se decodifica (`pngImportDecode.ts`, browser-only) a un buffer RGBA.
 * 3. Si sus dimensiones no coinciden EXACTO con el destino resuelto, se
 *    calcula el resultado de crop/pad (`textureImportTools.cropOrPad`)
 *    -- NUNCA se ofrece ni se ejecuta ningún camino de escalado/resize
 *    (AC: "test que confirma que no existe ninguna opción de escalar"
 *    -- este componente no importa, no referencia y no expone ningún
 *    control de ese tipo, por construcción; ver `TextureImportPanel.spec.ts`).
 * 4. SIEMPRE se muestra un panel de confirmación (mismo patrón visual
 *    que el toggle Antes/Después de `AiEditPanel.vue`: un solo preview,
 *    alternable) antes de aplicar nada -- si el usuario no confirma
 *    explícitamente (Cancelar, o simplemente no interactuar), el
 *    atlas/región permanece intacto (no se llama a `recordPatch`).
 * 5. Al confirmar, es EXACTAMENTE una llamada a
 *    `textureEditorStore.recordPatch(rect, before, after)` -- un único
 *    `TexturePatchCommand`, nunca N.
 *
 * Estado B (atlas congelado, ticket 042): el `rect` que termina en cada
 * `recordPatch()` SIEMPRE viene del atlas/región YA vigente al momento
 * de elegir el archivo (`resolveTarget()`, ver abajo) -- este componente
 * jamás deriva un tamaño nuevo desde el PNG importado, así que el
 * criterio "crop/pad hacia las dimensiones congeladas, nunca las
 * cambia" se cumple por construcción, con o sin contenido PAINTED.
 *
 * Decisión de diseño -- `resolveTarget` es una FUNCIÓN, no un prop de
 * valor `targetRect` estático: el destino (región enfocada vs. atlas
 * completo) depende del selector de región de `TextureCanvas.vue`, que
 * a su vez depende de `textureEditorStore.atlas` (cargado recién en el
 * `onMounted` del padre). Si el destino viajara como prop de VALOR, la
 * primera vez que este componente reacciona a un evento justo después
 * del montaje podría ver todavía el snapshot de props del primer render
 * (atlas todavía en 0×0, antes de que Vue propague el recomputo del
 * padre) -- un prop de FUNCIÓN, en cambio, se evalúa en el momento
 * exacto de `onFileChange`, leyendo el estado reactivo vigente sin
 * depender de que ya se haya "aplicado" un re-render del padre. El
 * `rect` resuelto en ese instante se guarda dentro de `pending` y es el
 * que se usa tanto para la previsualización como para el único
 * `recordPatch()` final -- inmune a que el usuario cambie el selector
 * de región mientras el diálogo de confirmación sigue abierto.
 */
import { computed, ref, watch } from 'vue'
import GButton from '../../design-system/components/GButton.vue'
import IconUpload from '../../design-system/icons/IconUpload.vue'
import { decodePngBytesToAtlasBuffer, PngDecodeError } from './pngImportDecode'
import { cropOrPad, describeImportAdjustment, type ImportAdjustment } from './textureImportTools'
import type { TextureRect } from './TexturePatchCommand'
import { useTextureEditorStore } from './textureEditorStore'

const props = defineProps<{
  /** Etiqueta cosmética para el botón disparador, ANTES de elegir archivo (ej. "el atlas completo"). */
  targetLabel: string
  /** Resuelve el destino REAL en el momento exacto de procesar el archivo elegido -- ver docstring de arriba. */
  resolveTarget: () => { rect: TextureRect; label: string }
}>()
const emit = defineEmits<{ imported: [] }>()

const textureEditorStore = useTextureEditorStore()

const fileInputRef = ref<HTMLInputElement>()
const previewCanvasRef = ref<HTMLCanvasElement>()
const error = ref<string | null>(null)
const showingAfter = ref(true)

interface PendingImport {
  rect: TextureRect
  label: string
  before: Uint8ClampedArray
  after: Uint8ClampedArray
  adjustment: ImportAdjustment
  sourceWidth: number
  sourceHeight: number
}

const pending = ref<PendingImport | null>(null)

const adjustmentMessage = computed(() => {
  if (!pending.value) {
    return ''
  }
  const { rect, label, adjustment, sourceWidth, sourceHeight } = pending.value
  if (adjustment.matches) {
    return `La imagen importada (${sourceWidth}×${sourceHeight}) coincide exactamente con ${label}.`
  }
  const parts: string[] = []
  if (adjustment.cropWidth > 0 || adjustment.cropHeight > 0) {
    parts.push(`se recortará el excedente (${adjustment.cropWidth}px de ancho, ${adjustment.cropHeight}px de alto)`)
  }
  if (adjustment.padWidth > 0 || adjustment.padHeight > 0) {
    parts.push(`se agregará margen transparente (${adjustment.padWidth}px de ancho, ${adjustment.padHeight}px de alto)`)
  }
  return `La imagen importada (${sourceWidth}×${sourceHeight}) no coincide con ${label} (${rect.width}×${rect.height}): ${parts.join(' y ')}. Nunca se escala/deforma.`
})

function triggerFileDialog(): void {
  fileInputRef.value?.click()
}

async function onFileChange(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0] ?? null
  input.value = '' // permite reimportar el mismo archivo dos veces seguidas
  if (!file) {
    return
  }

  error.value = null
  try {
    const decoded = await decodePngBytesToAtlasBuffer(file)
    const { rect, label } = props.resolveTarget()
    const before = textureEditorStore.readRegion(rect)
    if (!before) {
      error.value = 'No hay un atlas cargado sobre el cual importar.'
      return
    }
    const adjustment = describeImportAdjustment(decoded.width, decoded.height, rect.width, rect.height)
    const after = adjustment.matches ? decoded.pixels : cropOrPad(decoded, rect.width, rect.height).pixels
    pending.value = { rect, label, before, after, adjustment, sourceWidth: decoded.width, sourceHeight: decoded.height }
    showingAfter.value = true
  } catch (e) {
    error.value = e instanceof PngDecodeError ? e.message : 'No se pudo importar la imagen.'
  }
}

function confirmImport(): void {
  if (!pending.value) {
    return
  }
  textureEditorStore.recordPatch(pending.value.rect, pending.value.before, pending.value.after) // ÚNICA llamada -- un solo TexturePatchCommand
  pending.value = null
  emit('imported')
}

function cancelImport(): void {
  pending.value = null
}

function redrawPreview(): void {
  const canvas = previewCanvasRef.value
  if (!canvas || !pending.value) {
    return
  }
  const ctx = canvas.getContext('2d')
  if (!ctx) {
    // jsdom (tests) -- ver el mismo guard en TextureCanvas.vue.
    return
  }
  const source = showingAfter.value ? pending.value.after : pending.value.before
  ctx.putImageData(new ImageData(new Uint8ClampedArray(source), pending.value.rect.width, pending.value.rect.height), 0, 0)
}

watch([pending, showingAfter], redrawPreview, { flush: 'post' }) // 'post': corre DESPUÉS de que Vue patchee el DOM -- el <canvas> de preview solo existe una vez que `pending` es truthy (v-if).
</script>

<template>
  <fieldset class="texture-import-panel">
    <legend class="texture-import-panel__sr-only">Importar imagen PNG sobre {{ targetLabel }}</legend>

    <GButton type="button" variant="secondary" :title="`Importar PNG sobre ${targetLabel}`" @click="triggerFileDialog"><template #icon><IconUpload :size="16" /></template>Importar PNG</GButton>
    <input ref="fileInputRef" type="file" accept="image/png" aria-label="Archivo PNG a importar" class="texture-import-panel__file-input" @change="onFileChange" />

    <p v-if="error" class="texture-import-panel__error">{{ error }}</p>

    <div v-if="pending" class="texture-import-panel__confirm">
      <p class="texture-import-panel__message">{{ adjustmentMessage }}</p>

      <div class="texture-import-panel__toggle" role="tablist" aria-label="Antes o después del import, en la previsualización">
        <button
          type="button"
          role="tab"
          :aria-selected="!showingAfter"
          class="texture-import-panel__toggle-btn"
          :class="{ 'texture-import-panel__toggle-btn--active': !showingAfter }"
          @click="showingAfter = false"
        >
          Antes
        </button>
        <button
          type="button"
          role="tab"
          :aria-selected="showingAfter"
          class="texture-import-panel__toggle-btn"
          :class="{ 'texture-import-panel__toggle-btn--active': showingAfter }"
          @click="showingAfter = true"
        >
          Después
        </button>
      </div>

      <canvas ref="previewCanvasRef" :width="pending.rect.width" :height="pending.rect.height" class="texture-import-panel__preview" aria-label="Previsualización del import antes de confirmar"></canvas>

      <div class="texture-import-panel__actions">
        <GButton type="button" variant="secondary" @click="cancelImport">Cancelar</GButton>
        <GButton type="button" variant="primary" @click="confirmImport">Confirmar import</GButton>
      </div>
    </div>
  </fieldset>
</template>

<style scoped>
.texture-import-panel {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  border: none;
  margin: 0;
  padding: 0;
}

.texture-import-panel__sr-only {
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

/* Disparado programáticamente desde el botón "Importar PNG" -- se
   mantiene fuera de la vista (no `display: none`, para que siga siendo
   parte del árbol de foco/accesibilidad si algún AT decide exponerlo)
   en vez de mostrar el selector nativo del navegador. */
.texture-import-panel__file-input {
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

.texture-import-panel__error {
  margin: 0;
  color: var(--danger);
  font-size: var(--text-sm);
}

.texture-import-panel__confirm {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  padding: var(--space-2);
  background: var(--surface-2);
  border: var(--border-width) solid var(--border);
  border-radius: var(--radius-md);
}

.texture-import-panel__message {
  margin: 0;
  font-size: var(--text-sm);
  color: var(--text);
}

.texture-import-panel__toggle {
  display: flex;
  gap: var(--space-1);
}

.texture-import-panel__toggle-btn {
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

.texture-import-panel__toggle-btn--active {
  background: var(--panel);
  border-color: var(--accent);
  color: var(--text);
  font-weight: 600;
}

.texture-import-panel__preview {
  width: 100%;
  max-width: 160px;
  image-rendering: pixelated;
  border: var(--border-width) solid var(--border);
  border-radius: var(--radius-sm);
  align-self: center;
}

.texture-import-panel__actions {
  display: flex;
  gap: var(--space-2);
}
</style>
