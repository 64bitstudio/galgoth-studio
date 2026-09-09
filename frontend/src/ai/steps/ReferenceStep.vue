<script setup lang="ts">
/**
 * Paso 1 "Referencia" del wizard (ticket 027, HU-10 AC #1, mockup 02).
 * Solo SELECCIONA y valida la imagen client-side (formato/tamaño,
 * mismos límites que el backend del ticket 024) -- la subida real
 * (`POST /api/mobs/{mobId}/references`) ocurre recién al confirmar el
 * paso "Configuración", una vez que el mob existe de verdad (el nombre
 * se pide en el paso siguiente, no acá).
 */
import { ref } from 'vue'
import { MAX_REFERENCE_IMAGE_BYTES, SUPPORTED_REFERENCE_IMAGE_TYPES } from '../../api/referenceImagesApi'

const emit = defineEmits<{ selected: [File] }>()

const validationError = ref<string | null>(null)
const filePickerEl = ref<HTMLInputElement>()

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

function handleDrop(event: DragEvent): void {
  event.preventDefault()
  handleFiles(event.dataTransfer?.files ?? null)
}

function openFilePicker(): void {
  filePickerEl.value?.click()
}
</script>

<template>
  <div class="reference-step">
    <h2 class="reference-step__title">Referencia</h2>
    <p class="reference-step__hint">Sube una imagen de concept art como referencia para generar el modelo.</p>

    <div
      class="reference-step__dropzone"
      role="button"
      tabindex="0"
      @click="openFilePicker"
      @keydown.enter="openFilePicker"
      @dragover.prevent
      @drop="handleDrop"
    >
      <span class="reference-step__dropzone-icon" aria-hidden="true">⬆</span>
      <span>Arrastra una imagen o hace clic para elegir un archivo</span>
      <span class="reference-step__dropzone-formats">PNG o JPEG, máximo 10MB</span>
    </div>
    <label class="reference-step__file-label">
      Elegir imagen de referencia
      <input ref="filePickerEl" type="file" accept="image/png,image/jpeg" aria-label="Elegir imagen de referencia" @change="handleFiles(($event.target as HTMLInputElement).files)" />
    </label>

    <p v-if="validationError" class="reference-step__error">{{ validationError }}</p>
    <p class="reference-step__hint reference-step__hint--small">Elegir una imagen válida avanza automáticamente a "Configuración".</p>
  </div>
</template>

<style scoped>
.reference-step {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  max-width: 480px;
}

.reference-step__title {
  margin: 0;
}

.reference-step__hint {
  margin: 0;
  color: var(--muted);
}

.reference-step__hint--small {
  font-size: var(--text-sm);
}

.reference-step__dropzone {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-6);
  border: 2px dashed var(--border);
  border-radius: var(--radius-lg);
  color: var(--muted);
  cursor: pointer;
  text-align: center;
}

.reference-step__dropzone:hover,
.reference-step__dropzone:focus-visible {
  border-color: var(--accent);
  color: var(--text);
}

.reference-step__dropzone-icon {
  font-size: var(--text-lg);
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
  margin: 0;
  color: var(--danger);
}
</style>
