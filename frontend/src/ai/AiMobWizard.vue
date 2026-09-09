<script setup lang="ts">
/**
 * Wizard de generación IA (ticket 027, HU-10, mockups 02-04) -- navega
 * los 4 pasos Referencia → Configuración → Generación → Resultado.
 *
 * Orden real de efectos secundarios, importante: la imagen elegida en
 * "Referencia" se guarda SOLO en memoria (nunca se sube todavía) porque
 * subirla requiere un `mobId` real (024, `POST /api/mobs/{mobId}/references`)
 * y el mob ni siquiera tiene nombre hasta "Configuración" -- recién al
 * confirmar ese paso se crea el mob de verdad (022) y DESPUÉS se sube la
 * imagen ya retenida. "Generación"/"Resultado" son shells visuales sin
 * job real todavía (VoBo del PO, ticket 029/030 los conectan).
 */
import { computed, onBeforeUnmount, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import GSidebar, { type GSidebarKey } from '../design-system/components/GSidebar.vue'
import WizardStepper, { type WizardStepKey } from './WizardStepper.vue'
import ReferenceStep from './steps/ReferenceStep.vue'
import ConfigurationStep from './steps/ConfigurationStep.vue'
import GenerationStep from './steps/GenerationStep.vue'
import { createMob, type BaseType } from '../projects/mobsApi'
import { uploadReferenceImage } from '../api/referenceImagesApi'
import { ApiError } from '../api/ApiError'

const route = useRoute()
const router = useRouter()
const projectId = route.params.projectId as string

const step = ref<WizardStepKey>('reference')
const referenceFile = ref<File | null>(null)
const referencePreviewUrl = ref<string | null>(null)
const submitting = ref(false)
const submitError = ref<string | null>(null)

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
      <GenerationStep v-else-if="step === 'generation'" @back-to-project="backToProject" />
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
</style>
