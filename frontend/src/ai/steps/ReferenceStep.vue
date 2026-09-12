<script setup lang="ts">
/**
 * Paso 1 "Referencia" del wizard (ticket 027, HU-10 AC #1, mockup 02).
 * Solo SELECCIONA y valida la imagen client-side (formato/tamaño,
 * mismos límites que el backend del ticket 024) -- la subida real
 * (`POST /api/mobs/{mobId}/references`) ocurre recién al confirmar el
 * paso "Configuración", una vez que el mob existe de verdad (el nombre
 * se pide en el paso siguiente, no acá).
 *
 * Ticket 037 (corrección de UX/fidelidad visual): mismo comportamiento
 * exacto (valida y emite `selected`, el wizard avanza) -- la única
 * corrección es de presentación, con estados reales de dropzone
 * (idle/dragover/inválido) en vez de un rectángulo punteado estático.
 *
 * Post-074 (rediseño del wizard, VoBo del PO sobre el preview interactivo):
 * agrega el panel lateral "Consejos para mejores resultados" y la
 * sección "Ejemplos de referencias" -- ambos puramente decorativos por
 * decisión explícita del PO (AskUserQuestion): ni las miniaturas de
 * ejemplo son clickeables ni "Ver más ejemplos" lleva a ningún lado real
 * todavía (no existe ninguna pantalla de galería de ejemplos en el
 * proyecto). El comportamiento real de selección/validación de imagen no
 * cambia en absoluto -- este ticket es una pasada de fidelidad visual.
 */
import { ref } from 'vue'
import GButton from '../../design-system/components/GButton.vue'
import IconArachnid from '../../design-system/icons/IconArachnid.vue'
import IconBook from '../../design-system/icons/IconBook.vue'
import IconCustomBase from '../../design-system/icons/IconCustomBase.vue'
import IconFlying from '../../design-system/icons/IconFlying.vue'
import IconImage from '../../design-system/icons/IconImage.vue'
import IconInfo from '../../design-system/icons/IconInfo.vue'
import IconSun from '../../design-system/icons/IconSun.vue'
import IconUpload from '../../design-system/icons/IconUpload.vue'
import IconUser from '../../design-system/icons/IconUser.vue'
import IconWarning from '../../design-system/icons/IconWarning.vue'
import { MAX_REFERENCE_IMAGE_BYTES, SUPPORTED_REFERENCE_IMAGE_TYPES } from '../../api/referenceImagesApi'

const emit = defineEmits<{ selected: [File] }>()

const validationError = ref<string | null>(null)
const isDragOver = ref(false)
const filePickerEl = ref<HTMLInputElement>()

/** Puramente decorativas (ver docstring) -- reusan los íconos de tipo de base ya existentes, sin representar ningún tipo real elegido. */
const EXAMPLE_ICONS = [IconUser, IconArachnid, IconCustomBase, IconFlying]

function handleFiles(files: FileList | null): void {
  const file = files?.[0]
  if (!file) {
    return
  }
  if (!SUPPORTED_REFERENCE_IMAGE_TYPES.includes(file.type)) {
    validationError.value = `Formato no soportado: '${file.type || 'desconocido'}' -- solo se aceptan PNG o JPEG.`
    return
  }
  if (file.size > MAX_REFERENCE_IMAGE_BYTES) {
    validationError.value = `La imagen pesa ${(file.size / (1024 * 1024)).toFixed(1)}MB -- el máximo soportado es 10MB.`
    return
  }
  validationError.value = null
  emit('selected', file)
}

function handleDragEnter(event: DragEvent): void {
  event.preventDefault()
  isDragOver.value = true
}

function handleDragLeave(event: DragEvent): void {
  event.preventDefault()
  isDragOver.value = false
}

function handleDrop(event: DragEvent): void {
  event.preventDefault()
  isDragOver.value = false
  handleFiles(event.dataTransfer?.files ?? null)
}

function openFilePicker(): void {
  filePickerEl.value?.click()
}
</script>

<template>
  <div class="reference-step">
    <div class="reference-step__intro">
      <h2 class="reference-step__title">Referencia</h2>
      <p class="reference-step__hint">Sube una imagen de concept art como referencia -- la IA la analiza para proponer la silueta, el rig y los cuboides del modelo.</p>
    </div>

    <div class="reference-step__layout">
      <div class="reference-step__main">
        <div
          class="reference-step__dropzone"
          :class="{ 'reference-step__dropzone--dragover': isDragOver, 'reference-step__dropzone--invalid': validationError }"
          @dragenter="handleDragEnter"
          @dragover.prevent
          @dragleave="handleDragLeave"
          @drop="handleDrop"
        >
          <!-- Ticket 074 (post-VoBo): dos `<button>` HERMANOS, no uno anidado dentro del otro (Sonar S6819 -- contenido interactivo dentro de un `<button>` es HTML inválido, mismo criterio ya establecido en ProjectCard.vue/MobCard.vue). El drag&drop vive en el `<div>` contenedor (no requiere semántica de botón); el click-to-open vive en los DOS botones. -->
          <button type="button" class="reference-step__dropzone-trigger" @click="openFilePicker">
            <span class="reference-step__dropzone-icon" aria-hidden="true">
              <IconWarning v-if="validationError" :size="32" />
              <IconImage v-else :size="32" />
            </span>
            <span class="reference-step__dropzone-title">
              {{ isDragOver ? 'Suelta la imagen aquí' : 'Arrastra una imagen aquí o haz clic para elegir un archivo' }}
            </span>
            <span class="reference-step__dropzone-formats">PNG o JPEG, máximo 10MB</span>
          </button>
          <GButton variant="accent" type="button" @click="openFilePicker"><template #icon><IconUpload :size="16" /></template>Seleccionar imagen</GButton>
        </div>
        <label class="reference-step__file-label">
          Elegir imagen de referencia
          <input ref="filePickerEl" type="file" accept="image/png,image/jpeg" aria-label="Elegir imagen de referencia" @change="handleFiles(($event.target as HTMLInputElement).files)" />
        </label>

        <p v-if="validationError" class="reference-step__error"><IconWarning :size="16" /> {{ validationError }}</p>

        <div class="reference-step__examples">
          <div class="reference-step__examples-head">
            <h3>Ejemplos de referencias</h3>
            <span class="reference-step__examples-more">Ver más ejemplos →</span>
          </div>
          <div class="reference-step__examples-grid">
            <span v-for="(ExampleIcon, index) in EXAMPLE_ICONS" :key="index" class="reference-step__example-tile" aria-hidden="true">
              <component :is="ExampleIcon" :size="40" />
            </span>
          </div>
        </div>

        <p class="reference-step__info-line"><IconInfo :size="16" /> Al elegir una imagen válida, avanzarás automáticamente a "Configuración".</p>
      </div>

      <aside class="reference-step__tips">
        <div class="reference-step__tips-head"><IconBook :size="18" /> Consejos para mejores resultados</div>
        <p class="reference-step__tips-intro">La calidad de tu imagen de referencia influye directamente en el resultado. Sigue estas recomendaciones:</p>

        <div class="reference-step__tip">
          <span class="reference-step__tip-icon"><IconImage :size="18" /></span>
          <div>
            <div class="reference-step__tip-title">Usa concept art claro</div>
            <div class="reference-step__tip-desc">Una ilustración limpia y bien definida funciona mejor que imágenes con mucho ruido o efectos.</div>
          </div>
        </div>
        <div class="reference-step__tip">
          <span class="reference-step__tip-icon"><IconUser :size="18" /></span>
          <div>
            <div class="reference-step__tip-title">Vista de cuerpo completo</div>
            <div class="reference-step__tip-desc">Muestra el personaje o criatura completo de frente o en vista 3/4.</div>
          </div>
        </div>
        <div class="reference-step__tip reference-step__tip--last">
          <span class="reference-step__tip-icon"><IconSun :size="18" /></span>
          <div>
            <div class="reference-step__tip-title">Buen contraste e iluminación</div>
            <div class="reference-step__tip-desc">Asegúrate de que la silueta se distinga bien del fondo.</div>
          </div>
        </div>

        <div class="reference-step__tip-box">
          <IconInfo :size="20" />
          <div>
            <div class="reference-step__tip-box-title">Tip</div>
            <div class="reference-step__tip-box-desc">También puedes usar bocetos o ideas rápidas. La IA sabrá interpretarlas.</div>
          </div>
        </div>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.reference-step {
  display: flex;
  flex-direction: column;
  gap: var(--space-5);
}

.reference-step__intro {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
}

.reference-step__title {
  margin: 0;
}

.reference-step__hint {
  margin: 0;
  color: var(--muted);
  max-width: 640px;
}

.reference-step__layout {
  display: flex;
  gap: var(--space-6);
  align-items: flex-start;
}

.reference-step__main {
  flex: 1;
  min-width: 0;
}

.reference-step__dropzone {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--space-3);
  width: 100%;
  min-height: 260px;
  padding: var(--space-8);
  background: var(--panel);
  border: 2px dashed var(--border);
  border-radius: var(--radius-lg);
  text-align: center;
  transition:
    border-color var(--transition-fast),
    background-color var(--transition-fast);
}

.reference-step__dropzone:has(.reference-step__dropzone-trigger:hover, .reference-step__dropzone-trigger:focus-visible) {
  border-color: var(--accent);
}

.reference-step__dropzone--dragover {
  border-color: var(--accent);
  border-style: solid;
  background: var(--accent-soft);
}

.reference-step__dropzone--invalid {
  border-color: var(--danger);
}

.reference-step__dropzone-trigger {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-3);
  width: 100%;
  background: none;
  border: none;
  color: var(--muted);
  cursor: pointer;
  text-align: center;
  font: inherit;
}

.reference-step__dropzone-trigger:hover,
.reference-step__dropzone-trigger:focus-visible {
  color: var(--text);
}

.reference-step__dropzone--dragover .reference-step__dropzone-trigger {
  color: var(--accent);
}

.reference-step__dropzone--invalid .reference-step__dropzone-trigger {
  color: var(--danger);
}

.reference-step__dropzone-icon {
  display: flex;
  color: var(--accent);
}

.reference-step__dropzone--invalid .reference-step__dropzone-icon {
  color: var(--danger);
}

.reference-step__dropzone-title {
  font-size: var(--text-md);
  font-weight: 700;
  color: inherit;
}

.reference-step__dropzone-formats {
  font-size: var(--text-xs);
}

.reference-step__file-label {
  /* Oculto visualmente pero accesible (no display:none, que lo saca del árbol de foco) -- el dropzone de arriba es el control visual real, este input cubre el caso de teclado/lector de pantalla. */
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0 0 0 0);
}

.reference-step__error {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  margin: var(--space-3) 0 0;
  color: var(--danger);
  font-size: var(--text-sm);
}

.reference-step__examples {
  margin-top: var(--space-6);
}

.reference-step__examples-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: var(--space-3);
}

.reference-step__examples-head h3 {
  margin: 0;
  font-size: var(--text-md);
  font-weight: 700;
}

.reference-step__examples-more {
  color: var(--accent);
  font-size: var(--text-sm);
  font-weight: 600;
}

.reference-step__examples-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: var(--space-3);
}

.reference-step__example-tile {
  aspect-ratio: 1;
  border-radius: var(--radius-md);
  border: var(--border-width) solid var(--border);
  background: var(--surface);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #5b6a76;
}

.reference-step__info-line {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  margin: var(--space-5) 0 0;
  color: var(--muted);
  font-size: var(--text-sm);
}

.reference-step__tips {
  width: 340px;
  flex-shrink: 0;
  background: var(--panel);
  border: var(--border-width) solid var(--border);
  border-radius: var(--radius-lg);
  padding: var(--space-5);
}

.reference-step__tips-head {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  font-weight: 700;
  font-size: var(--text-md);
  margin-bottom: var(--space-3);
  color: var(--accent);
}

.reference-step__tips-intro {
  margin: 0 0 var(--space-4);
  padding-bottom: var(--space-4);
  border-bottom: var(--border-width) solid var(--border);
  color: var(--muted);
  font-size: var(--text-sm);
}

.reference-step__tip {
  display: flex;
  gap: var(--space-3);
  padding-bottom: var(--space-4);
  margin-bottom: var(--space-4);
  border-bottom: var(--border-width) solid var(--border);
}

.reference-step__tip--last {
  padding-bottom: 0;
  margin-bottom: 0;
  border-bottom: none;
}

.reference-step__tip-icon {
  width: 36px;
  height: 36px;
  flex-shrink: 0;
  border-radius: var(--radius-md);
  background: var(--surface-2);
  color: var(--text);
  display: flex;
  align-items: center;
  justify-content: center;
}

.reference-step__tip-title {
  font-weight: 700;
  font-size: var(--text-sm);
  margin-bottom: 2px;
}

.reference-step__tip-desc {
  font-size: var(--text-sm);
  color: var(--muted);
}

.reference-step__tip-box {
  margin-top: var(--space-4);
  display: flex;
  gap: var(--space-3);
  padding: var(--space-3);
  background: var(--accent-soft);
  border: var(--border-width) solid var(--accent);
  border-radius: var(--radius-md);
  color: var(--accent);
}

.reference-step__tip-box-title {
  font-weight: 700;
  font-size: var(--text-sm);
  margin-bottom: 2px;
}

.reference-step__tip-box-desc {
  font-size: var(--text-sm);
  color: var(--text);
}
</style>
