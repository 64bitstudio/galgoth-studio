import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import WizardStepper from '../WizardStepper.vue'

describe('WizardStepper.vue', () => {
  it('marca el paso actual como activo y los anteriores como completados', () => {
    const wrapper = mount(WizardStepper, { props: { currentStep: 'generation' } })

    const steps = wrapper.findAll('li')
    expect(steps).toHaveLength(4)
    expect(steps[0]!.classes()).toContain('wizard-stepper__step--done') // Referencia
    expect(steps[1]!.classes()).toContain('wizard-stepper__step--done') // Configuración
    expect(steps[2]!.classes()).toContain('wizard-stepper__step--active') // Generación
    expect(steps[3]!.classes()).toContain('wizard-stepper__step--pending') // Resultado
  })

  it('muestra un check en los pasos completados y el número en los demás', () => {
    const wrapper = mount(WizardStepper, { props: { currentStep: 'configuration' } })

    const markers = wrapper.findAll('.wizard-stepper__marker')
    expect(markers[0]!.text()).toBe('✓') // Referencia, ya completado
    expect(markers[1]!.text()).toBe('2') // Configuración, activo
    expect(markers[2]!.text()).toBe('3') // Generación, pendiente
  })

  it('el primer paso (Referencia) no marca nada como completado', () => {
    const wrapper = mount(WizardStepper, { props: { currentStep: 'reference' } })

    const steps = wrapper.findAll('li')
    expect(steps[0]!.classes()).toContain('wizard-stepper__step--active')
    expect(steps.filter((s) => s.classes().includes('wizard-stepper__step--done'))).toHaveLength(0)
  })
})
