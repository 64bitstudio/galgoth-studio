<script setup lang="ts">
/**
 * Paso 2 "Configuración" del wizard (ticket 027, HU-10 AC #2, mockup 02):
 * nombre + tipo base + resolución de textura.
 *
 * Simplificación consciente y documentada (misma sesión que decidió el
 * alcance del ticket, VoBo del PO): el mockup muestra "Detectar con IA"
 * como una opción más de tipo base, pre-seleccionada por defecto y
 * reemplazada por la propuesta real de 028 -- ese ticket no existe
 * todavía, así que esa opción se omite este ciclo (no hay nada real que
 * "detectar") y el tipo base es una selección manual, igual que en
 * `AddMobModal.vue` (022). "Resolución de textura" es solo un campo de
 * UI por ahora -- nada en el backend lo consume todavía (`AutoUv`, 006,
 * calcula el atlas real a partir de la geometría, no de una preferencia
 * elegida de antemano).
 *
 * Post-074 (rediseño del wizard, VoBo del PO sobre el preview interactivo):
 * - El preview de la izquierda sigue mostrando la imagen REAL subida
 *   (`referencePreviewUrl`) -- el mockup de referencia usaba un ícono
 *   genérico ahí solo porque era una vista estática sin una foto real
 *   que incrustar; mostrar la foto real es estrictamente más útil y es
 *   lo que ya hacía esta pantalla antes de este ticket.
 * - "Resolución de textura" pasa de `<select>` nativo a `GSelect.vue`
 *   (regla del design system, `no-native-form-controls`).
 * - Los 5 botones de "Tipo de entidad" ahora llevan ícono (mismos 5
 *   íconos ya creados en el ticket 073 para `MobCard.vue`/`AddMobModal.vue`).
 * - Fila informativa "Rig estimado / Vista previa / Formato de salida":
 *   "Rig estimado" es DINÁMICO según el tipo elegido (decisión explícita
 *   del PO vía AskUserQuestion) -- `RIG_ESTIMATE_BY_TYPE`. Los otros dos
 *   valores son fijos, puramente informativos (no hay otro formato de
 *   salida ni otro tipo de vista previa que ofrecer todavía).
 * - Contador de caracteres del nombre (0/32) -- mismo límite que ya
 *   validaba `AddMobModal.vue`/`ProjectNameModal.vue` en otras pantallas,
 *   ahora también visible acá.
 */
import { computed, ref } from 'vue'
import GButton from '../../design-system/components/GButton.vue'
import GSelect, { type GSelectOption } from '../../design-system/components/GSelect.vue'
import IconArachnid from '../../design-system/icons/IconArachnid.vue'
import IconCustomBase from '../../design-system/icons/IconCustomBase.vue'
import IconCuboid from '../../design-system/icons/IconCuboid.vue'
import IconEye from '../../design-system/icons/IconEye.vue'
import IconExport from '../../design-system/icons/IconExport.vue'
import IconFlying from '../../design-system/icons/IconFlying.vue'
import IconHumanoid from '../../design-system/icons/IconHumanoid.vue'
import IconMosaic from '../../design-system/icons/IconMosaic.vue'
import IconQuadruped from '../../design-system/icons/IconQuadruped.vue'
import IconSparkle from '../../design-system/icons/IconSparkle.vue'
import type { BaseType } from '../../projects/mobsApi'

const NAME_MAX_LENGTH = 32

const BASE_TYPE_OPTIONS: Array<{ value: BaseType; label: string; icon: unknown }> = [
  { value: 'humanoid', label: 'Humanoide', icon: IconHumanoid },
  { value: 'arachnid', label: 'Arácnido', icon: IconArachnid },
  { value: 'quadruped', label: 'Cuadrúpedo', icon: IconQuadruped },
  { value: 'flying', label: 'Volador', icon: IconFlying },
  { value: 'custom', label: 'Personalizado', icon: IconCustomBase },
]

/** Decisión explícita del PO (AskUserQuestion, post-074): "Rig estimado" refleja el tipo de entidad elegido en vez de un texto fijo. */
const RIG_ESTIMATE_BY_TYPE: Record<BaseType, string> = {
  humanoid: 'Estándar (bipedal)',
  arachnid: 'Radial (8 patas)',
  quadruped: 'Cuadrúpedo (4 patas)',
  flying: 'Alado (par de alas)',
  custom: 'Definido a mano (sin estimar)',
}

const TEXTURE_RESOLUTION_OPTIONS: GSelectOption[] = [
  { value: '64×64', label: '64×64' },
  { value: '128×128', label: '128×128 (recomendado)' },
  { value: '256×256', label: '256×256' },
]

const props = defineProps<{ referencePreviewUrl: string; submitting: boolean; submitError: string | null }>()
const emit = defineEmits<{ confirm: [{ name: string; baseType: BaseType }]; back: [] }>()

const name = ref('')
const baseType = ref<BaseType>('humanoid')
const textureResolution = ref('128×128')
const validationError = ref<string | null>(null)

const rigEstimate = computed(() => RIG_ESTIMATE_BY_TYPE[baseType.value])

function confirm(): void {
  const trimmed = name.value.trim()
  if (!trimmed) {
    validationError.value = 'El nombre no puede estar vacío.'
    return
  }
  validationError.value = null
  emit('confirm', { name: trimmed, baseType: baseType.value })
}
</script>

<template>
  <div class="configuration-step">
    <div class="configuration-step__preview">
      <span class="configuration-step__preview-badge"><IconMosaic :size="13" /> Referencia seleccionada</span>
      <img :src="props.referencePreviewUrl" alt="Referencia elegida" class="configuration-step__preview-img" />
      <button type="button" class="configuration-step__back" @click="emit('back')">← Cambiar imagen</button>
    </div>

    <div class="configuration-step__form">
      <div class="configuration-step__intro">
        <h2 class="configuration-step__title">Información del mob</h2>
        <p class="configuration-step__subtitle">Confirma los datos antes de que la IA genere el modelo. Puedes ajustar el nombre, tipo de entidad y la resolución de las texturas.</p>
      </div>

      <label class="configuration-step__label">
        <span class="configuration-step__label-row">
          <span>Nombre</span>
          <span class="configuration-step__count">{{ name.length }}/{{ NAME_MAX_LENGTH }}</span>
        </span>
        <input v-model="name" type="text" class="configuration-step__input" aria-label="Nombre del mob" :maxlength="NAME_MAX_LENGTH" @keyup.enter="confirm" />
        <span class="configuration-step__field-hint">Este será el nombre de tu modelo en el proyecto.</span>
      </label>

      <fieldset class="configuration-step__fieldset">
        <legend>Tipo de entidad</legend>
        <div class="configuration-step__base-types">
          <button
            v-for="option in BASE_TYPE_OPTIONS"
            :key="option.value"
            type="button"
            class="configuration-step__base-type"
            :class="{ 'configuration-step__base-type--selected': baseType === option.value }"
            :aria-pressed="baseType === option.value"
            @click="baseType = option.value"
          >
            <component :is="option.icon" :size="16" />
            {{ option.label }}
          </button>
        </div>
        <span class="configuration-step__field-hint">Define la estructura base del modelo para un mejor resultado.</span>
      </fieldset>

      <label class="configuration-step__label">
        Resolución de textura
        <GSelect v-model="textureResolution" :options="TEXTURE_RESOLUTION_OPTIONS" label="Resolución de textura">
          <template #icon><IconMosaic :size="16" /></template>
        </GSelect>
        <span class="configuration-step__field-hint">Mayor resolución ofrece más detalle, pero aumenta el tiempo de generación.</span>
      </label>

      <dl class="configuration-step__info-row">
        <div class="configuration-step__info-item">
          <IconCuboid :size="16" />
          <div><dt>Rig estimado</dt><dd>{{ rigEstimate }}</dd></div>
        </div>
        <div class="configuration-step__info-item">
          <IconEye :size="16" />
          <div><dt>Vista previa</dt><dd>Modelo 3D interactivo</dd></div>
        </div>
        <div class="configuration-step__info-item">
          <IconExport :size="16" />
          <div><dt>Formato de salida</dt><dd>.bbmodel (Galgoth)</dd></div>
        </div>
      </dl>

      <p v-if="validationError" class="configuration-step__error">{{ validationError }}</p>
      <p v-if="props.submitError" class="configuration-step__error">{{ props.submitError }}</p>

      <div class="configuration-step__actions">
        <GButton variant="primary" :disabled="props.submitting" @click="confirm">
          <template #icon><IconSparkle :size="16" /></template>
          {{ props.submitting ? 'Creando…' : 'Generar con IA →' }}
        </GButton>
      </div>
    </div>
  </div>
</template>

<style scoped>
.configuration-step {
  display: flex;
  gap: var(--space-6);
  flex-wrap: wrap;
  background: var(--panel);
  border: var(--border-width) solid var(--border);
  border-radius: var(--radius-lg);
  padding: var(--space-6);
}

.configuration-step__preview {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  width: 280px;
  flex-shrink: 0;
}

.configuration-step__preview-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  align-self: flex-start;
  padding: 4px var(--space-2);
  border-radius: 999px;
  background: var(--accent-soft);
  color: var(--accent);
  font-size: var(--text-xs);
  font-weight: 600;
}

.configuration-step__preview-img {
  width: 100%;
  aspect-ratio: 1;
  object-fit: cover;
  border-radius: var(--radius-lg);
  border: var(--border-width) solid var(--border);
  box-shadow: var(--shadow-md);
}

.configuration-step__intro {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
}

.configuration-step__subtitle {
  margin: 0;
  color: var(--muted);
  font-size: var(--text-sm);
}

.configuration-step__back {
  background: none;
  border: none;
  color: var(--muted);
  cursor: pointer;
  font-size: var(--text-sm);
  padding: 0;
  text-align: left;
}

.configuration-step__back:hover {
  color: var(--accent);
}

.configuration-step__form {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
  flex: 1;
  min-width: 240px;
}

.configuration-step__title {
  margin: 0;
}

.configuration-step__label {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  font-size: var(--text-sm);
  color: var(--muted);
  font-weight: 600;
}

.configuration-step__label-row {
  display: flex;
  justify-content: space-between;
}

.configuration-step__count {
  font-size: var(--text-xs);
  font-weight: 400;
  color: var(--muted);
}

.configuration-step__input {
  min-height: var(--hit-target-min);
  padding: 0 var(--space-3);
  background: var(--surface);
  border: var(--border-width) solid var(--border);
  border-radius: var(--radius-md);
  color: var(--text);
  font-size: var(--text-base);
  font-weight: 400;
}

.configuration-step__field-hint {
  font-size: var(--text-xs);
  color: var(--muted);
  font-weight: 400;
}

.configuration-step__fieldset {
  border: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.configuration-step__fieldset legend {
  font-size: var(--text-sm);
  color: var(--muted);
  font-weight: 600;
  padding: 0;
  margin-bottom: var(--space-2);
}

.configuration-step__base-types {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.configuration-step__base-type {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  min-height: var(--hit-target-min);
  padding: 0 var(--space-3);
  background: var(--surface);
  border: var(--border-width) solid var(--border);
  border-radius: var(--radius-md);
  color: var(--text);
  cursor: pointer;
  font-size: var(--text-sm);
  font-weight: 600;
}

.configuration-step__base-type--selected {
  background: var(--accent-soft);
  border-color: var(--accent);
  color: var(--accent);
}

.configuration-step__info-row {
  display: flex;
  gap: 0;
  margin: 0;
  padding: var(--space-4) 0;
  border-top: var(--border-width) solid var(--border);
  border-bottom: var(--border-width) solid var(--border);
}

.configuration-step__info-item {
  flex: 1;
  display: flex;
  align-items: flex-start;
  gap: var(--space-2);
  padding: 0 var(--space-4);
  border-left: var(--border-width) solid var(--border);
  color: var(--muted);
}

.configuration-step__info-item:first-child {
  border-left: none;
  padding-left: 0;
}

.configuration-step__info-item dt {
  font-size: var(--text-xs);
  color: var(--muted);
}

.configuration-step__info-item dd {
  margin: 2px 0 0;
  font-size: var(--text-sm);
  font-weight: 600;
  color: var(--text);
}

.configuration-step__error {
  margin: 0;
  color: var(--danger);
  font-size: var(--text-sm);
}

.configuration-step__actions {
  display: flex;
  justify-content: flex-end;
}
</style>
