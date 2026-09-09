<script setup lang="ts">
/**
 * Wizard de generación IA (tickets 027/029/030, HU-10/HU-11/HU-12,
 * mockups 02-04) -- navega los 4 pasos Referencia → Configuración →
 * Generación → Resultado, con las 4 etapas de la generación real (SSE,
 * 029) y las 3 acciones reales de Resultado (030).
 *
 * Orden real de efectos secundarios, importante: la imagen elegida en
 * "Referencia" se guarda SOLO en memoria (nunca se sube todavía) porque
 * subirla requiere un `mobId` real (024, `POST /api/mobs/{mobId}/references`)
 * y el mob ni siquiera tiene nombre hasta "Configuración" -- recién al
 * confirmar ese paso se crea el mob de verdad (022) y DESPUÉS se sube la
 * imagen ya retenida.
 *
 * **Gap conocido, documentado a propósito (VoBo del PO, ticket 030)**:
 * "Usar este modelo" navega de vuelta a `/projects/:projectId` en vez de
 * abrir un editor real -- ninguna ruta real de "Editar modelo" existe
 * todavía (el editor manual, 016-018, solo se ejerció vía el harness de
 * desarrollo `/dev/viewport-harness`). Cerrar esa ruta es alcance de un
 * ticket futuro.
 */
import { computed, onBeforeUnmount, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import GSidebar, { type GSidebarKey } from '../design-system/components/GSidebar.vue'
import GButton from '../design-system/components/GButton.vue'
import WizardStepper, { type WizardStepKey } from './WizardStepper.vue'
import ReferenceStep from './steps/ReferenceStep.vue'
import ConfigurationStep from './steps/ConfigurationStep.vue'
import GenerationStep from './steps/GenerationStep.vue'
import ResultStep from './steps/ResultStep.vue'
import { createMob, type BaseType, type MobSummary } from '../projects/mobsApi'
import { uploadReferenceImage } from '../api/referenceImagesApi'
import { applyGeneration, getGenerationResult, type GenerationResult } from '../api/generationResultApi'
import { ApiError } from '../api/ApiError'

const route = useRoute()
const router = useRouter()
const projectId = route.params.projectId as string

const step = ref<WizardStepKey>('reference')
const referenceFile = ref<File | null>(null)
const referencePreviewUrl = ref<string | null>(null)
const submitting = ref(false)
const submitError = ref<string | null>(null)
const createdMob = ref<MobSummary | null>(null)

const activeJobId = ref<string | null>(null)
const generationResult = ref<GenerationResult | null>(null)
const resultLoading = ref(false)
const resultFetchError = ref<string | null>(null)
const resultActionBusy = ref(false)
const resultActionError = ref<string | null>(null)

function handleReferenceSelected(file: File): void {
  if (referencePreviewUrl.value) {
    URL.revokeObjectURL(referencePreviewUrl.value)
  }
  referenceFile.value = file
  referencePreviewUrl.value = URL.createObjectURL(file)
  step.value = 'configuration'
}

function backToReference(): void {
  step.value = 'reference'
}

async function handleConfigurationConfirm(data: { name: string; baseType: BaseType }): Promise<void> {
  const file = referenceFile.value
  if (!file) {
    return
  }
  submitting.value = true
  submitError.value = null
  try {
    const mob = await createMob(projectId, data.name, data.baseType)
    await uploadReferenceImage(mob.id, file)
    createdMob.value = mob
    step.value = 'generation'
  } catch (error) {
    submitError.value = error instanceof ApiError ? error.message : 'No se pudo crear el mob con esta referencia.'
  } finally {
    submitting.value = false
  }
}

function backToProject(): void {
  router.push(`/projects/${projectId}`)
}

async function fetchResult(jobId: string): Promise<void> {
  resultLoading.value = true
  resultFetchError.value = null
  try {
    generationResult.value = await getGenerationResult(jobId)
  } catch (error) {
    resultFetchError.value = error instanceof ApiError ? error.message : 'No se pudo cargar el resultado de la generación.'
  } finally {
    resultLoading.value = false
  }
}

async function handleGenerationCompleted(jobId: string): Promise<void> {
  activeJobId.value = jobId
  step.value = 'result'
  await fetchResult(jobId)
}

function retryFetchResult(): void {
  if (activeJobId.value) {
    fetchResult(activeJobId.value)
  }
}

function handleDiscard(): void {
  // AC del ticket 030: "se abandona sin crear draft ni revisión" -- ya
  // es el caso por construcción (nada se creó todavía), así que no hace
  // falta ninguna llamada al backend acá, solo navegar.
  backToProject()
}

function handleRegenerate(): void {
  generationResult.value = null
  resultFetchError.value = null
  activeJobId.value = null
  // GenerationStep se desmonta (Resultado estaba activo) y se vuelve a
  // montar al volver a 'generation' -- su propio onMounted dispara un
  // POST /generate nuevo, reutilizando la misma imagen de referencia ya
  // subida en el paso 2.
  step.value = 'generation'
}

async function handleApply(): Promise<void> {
  if (!activeJobId.value) {
    return
  }
  resultActionBusy.value = true
  resultActionError.value = null
  try {
    await applyGeneration(activeJobId.value)
    backToProject()
  } catch (error) {
    resultActionError.value = error instanceof ApiError ? error.message : 'No se pudo aceptar este modelo.'
  } finally {
    resultActionBusy.value = false
  }
}

function handleSidebarSelect(key: GSidebarKey): void {
  if (key === 'home' || key === 'projects') {
    router.push('/projects')
  }
}

const previewUrl = computed(() => referencePreviewUrl.value ?? '')

onBeforeUnmount(() => {
  if (referencePreviewUrl.value) {
    URL.revokeObjectURL(referencePreviewUrl.value)
  }
})
</script>

<template>
  <div class="ai-mob-wizard-shell">
    <GSidebar active="projects" @select="handleSidebarSelect" />
    <main class="ai-mob-wizard">
      <WizardStepper :current-step="step" class="ai-mob-wizard__stepper" />

      <ReferenceStep v-if="step === 'reference'" @selected="handleReferenceSelected" />
      <ConfigurationStep
        v-else-if="step === 'configuration'"
        :reference-preview-url="previewUrl"
        :submitting="submitting"
        :submit-error="submitError"
        @confirm="handleConfigurationConfirm"
        @back="backToReference"
      />
      <GenerationStep
        v-else-if="step === 'generation' && createdMob"
        :mob-id="createdMob.id"
        :project-id="projectId"
        :mob-name="createdMob.name"
        :base-type="createdMob.baseType"
        @completed="handleGenerationCompleted"
        @back-to-project="backToProject"
      />
      <div v-else-if="step === 'result' && resultLoading" class="ai-mob-wizard__result-status">Cargando resultado…</div>
      <div v-else-if="step === 'result' && resultFetchError" class="ai-mob-wizard__result-status">
        <p>{{ resultFetchError }}</p>
        <GButton @click="retryFetchResult">Reintentar</GButton>
        <GButton variant="ghost" @click="backToProject">Ir al proyecto</GButton>
      </div>
      <ResultStep
        v-else-if="step === 'result' && generationResult"
        :job-id="generationResult.jobId"
        :mob-name="generationResult.mobName"
        :cuboid-count="generationResult.cuboidCount"
        :bone-count="generationResult.boneCount"
        :texture-width="generationResult.textureWidth"
        :texture-height="generationResult.textureHeight"
        :fmm-compatible="generationResult.fmmCompatible"
        :fmm-issues="generationResult.fmmIssues"
        :busy="resultActionBusy"
        :action-error="resultActionError"
        @discard="handleDiscard"
        @regenerate="handleRegenerate"
        @apply="handleApply"
      />
    </main>
  </div>
</template>

<style scoped>
.ai-mob-wizard-shell {
  display: flex;
  height: 100vh;
}

.ai-mob-wizard {
  flex: 1;
  padding: var(--space-6);
  overflow: auto;
  display: flex;
  flex-direction: column;
  gap: var(--space-6);
}

.ai-mob-wizard__stepper {
  align-self: flex-start;
}

.ai-mob-wizard__result-status {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: var(--space-2);
  color: var(--muted);
}
</style>
