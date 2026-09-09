<script setup lang="ts">
/**
 * Indicador de progreso del wizard de generación IA (ticket 027,
 * mockups 02-04): 4 pasos fijos -- Referencia/Configuración/Generación/
 * Resultado -- con el paso activo resaltado y los ya completados
 * marcados. Puramente visual (no navegable haciendo clic en un paso
 * futuro): el AC del ticket exige avanzar linealmente completando cada
 * paso, nunca saltar adelante.
 */
export type WizardStepKey = 'reference' | 'configuration' | 'generation' | 'result'

const STEPS: Array<{ key: WizardStepKey; label: string }> = [
  { key: 'reference', label: 'Referencia' },
  { key: 'configuration', label: 'Configuración' },
  { key: 'generation', label: 'Generación' },
  { key: 'result', label: 'Resultado' },
]

const props = defineProps<{ currentStep: WizardStepKey }>()

function stepIndex(key: WizardStepKey): number {
  return STEPS.findIndex((step) => step.key === key)
}

function stateFor(key: WizardStepKey): 'done' | 'active' | 'pending' {
  const current = stepIndex(props.currentStep)
  const index = stepIndex(key)
  if (index < current) return 'done'
  if (index === current) return 'active'
  return 'pending'
}
</script>

<template>
  <ol class="wizard-stepper" aria-label="Progreso del wizard de generación IA">
    <li
      v-for="(step, index) in STEPS"
      :key="step.key"
      class="wizard-stepper__step"
      :class="`wizard-stepper__step--${stateFor(step.key)}`"
      :aria-current="stateFor(step.key) === 'active' ? 'step' : undefined"
    >
      <span class="wizard-stepper__marker">{{ stateFor(step.key) === 'done' ? '✓' : index + 1 }}</span>
      <span class="wizard-stepper__label">{{ step.label }}</span>
    </li>
  </ol>
</template>

<style scoped>
.wizard-stepper {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  list-style: none;
  margin: 0;
  padding: 0;
}

.wizard-stepper__step {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  color: var(--muted);
  font-size: var(--text-sm);
}

.wizard-stepper__step:not(:last-child)::after {
  content: '';
  width: 24px;
  height: var(--border-width);
  background: var(--border);
  margin-left: var(--space-2);
}

.wizard-stepper__marker {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border-radius: 999px;
  border: var(--border-width) solid var(--border);
  font-size: var(--text-xs);
  font-weight: 600;
}

.wizard-stepper__step--active .wizard-stepper__marker {
  border-color: var(--accent);
  color: var(--accent);
}

.wizard-stepper__step--active .wizard-stepper__label {
  color: var(--text);
  font-weight: 600;
}

.wizard-stepper__step--done .wizard-stepper__marker {
  background: var(--accent);
  border-color: var(--accent);
  color: var(--accent-ink);
}

.wizard-stepper__step--done .wizard-stepper__label {
  color: var(--text);
}
</style>
