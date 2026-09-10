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
 *
 * Ticket 038 (recuperación tras refresh de página): `step`/`createdMob`
 * se persisten en `sessionStorage` apenas el mob real existe (recién ahí
 * hay algo real que recuperar -- antes de eso, perder la imagen elegida
 * en un refresh es aceptable, nunca hubo nada persistido en el backend).
 * `GenerationStep.vue` maneja su PROPIA recuperación del `jobId` (ver su
 * propio comentario) -- acá solo se recupera lo necesario para volver a
 * MONTARLO con el mismo mob real, sin lo cual nunca llegaría a
 * ejecutarse esa lógica.
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
import type { MobProjectModel } from '../domain/MobProjectModel'

const route = useRoute()
const router = useRouter()
const projectId = route.params.projectId as string

/** Ticket 038 -- una entrada por proyecto alcanza (un wizard activo por pestaña de navegador). */
const RESUME_STORAGE_KEY = `galgoth:ai-wizard-resume:${projectId}`

interface ResumableWizardState {
  step: Extract<WizardStepKey, 'generation'>
  mob: MobSummary
}

/**
 * Solo `'generation'` es recuperable de verdad: es el único paso cuyo
 * estado completo (el `jobId`, manejado por `GenerationStep.vue` mismo)
 * se puede reconstruir 100% desde el backend (backlog de `ai_job_events`
 * vía SSE). `'result'` necesitaría persistir también el `jobId` acá y
 * volver a pedir `GET /result` -- фuera de alcance de este ticket
 * (el bug reportado es específicamente el de "Generación"); tras un
 * refresh en "Resultado" el wizard vuelve a "Referencia" como hoy, sin
 * cambio de comportamiento respecto a antes de este ticket.
 */
function readResumableState(): ResumableWizardState | null {
  try {
    const raw = sessionStorage.getItem(RESUME_STORAGE_KEY)
    if (!raw) {
      return null
    }
    const parsed = JSON.parse(raw) as ResumableWizardState
    if (parsed.step === 'generation' && parsed.mob?.id) {
      return parsed
    }
    return null
  } catch {
    return null // sessionStorage corrupto/inaccesible (modo privado, cuota) -- nunca bloquea el wizard, solo se pierde la recuperación.
  }
}

function persistResumableState(mob: MobSummary): void {
  try {
    sessionStorage.setItem(RESUME_STORAGE_KEY, JSON.stringify({step: 'generation', mob}))
  } catch {
    // Ver readResumableState -- mismo criterio, nunca bloquea el wizard.
  }
}

function clearResumableState(): void {
  try {
    sessionStorage.removeItem(RESUME_STORAGE_KEY)
  } catch {
    // Ver readResumableState.
  }
}

const resumed = readResumableState()

const step = ref<WizardStepKey>(resumed?.step ?? 'reference')
const referenceFile = ref<File | null>(null)
const referencePreviewUrl = ref<string | null>(null)
const submitting = ref(false)
const submitError = ref<string | null>(null)
const createdMob = ref<MobSummary | null>(resumed?.mob ?? null)

const activeJobId = ref<string | null>(null)
const finalPreviewModel = ref<MobProjectModel | null>(null)
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
    persistResumableState(mob)
  } catch (error) {
    submitError.value = error instanceof ApiError ? error.message : 'No se pudo crear el mob con esta referencia.'
  } finally {
    submitting.value = false
  }
}

function backToProject(): void {
  clearResumableState()
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

async function handleGenerationCompleted(jobId: string, finalModel: MobProjectModel): Promise<void> {
  activeJobId.value = jobId
  finalPreviewModel.value = finalModel
  step.value = 'result'
  clearResumableState() // "Generación" ya terminó -- un refresh a partir de acá vuelve a "Referencia", igual que antes de este ticket.
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
  finalPreviewModel.value = null
  // GenerationStep se desmonta (Resultado estaba activo) y se vuelve a
  // montar al volver a 'generation' -- su propio onMounted dispara un
  // POST /generate nuevo, reutilizando la misma imagen de referencia ya
  // subida en el paso 2.
  step.value = 'generation'
  if (createdMob.value) {
    persistResumableState(createdMob.value)
  }
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
    <main class="ai-mob-wizard app-scroll">
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
        :preview-model="finalPreviewModel"
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
