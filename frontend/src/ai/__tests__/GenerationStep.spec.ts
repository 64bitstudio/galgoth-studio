import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import GenerationStep from '../steps/GenerationStep.vue'

describe('GenerationStep.vue', () => {
  it('muestra las 6 etapas del mockup, ninguna marcada como completada (shell sin job real, ticket 027)', () => {
    const wrapper = mount(GenerationStep)

    expect(wrapper.findAll('.generation-step__stage')).toHaveLength(6)
    expect(wrapper.text()).toContain('Analizando imagen')
    expect(wrapper.text()).toContain('Generando textura')
  })

  it('muestra un aviso explícito de que la generación real no está disponible todavía', () => {
    const wrapper = mount(GenerationStep)
    expect(wrapper.text()).toContain('todavía no está disponible')
  })

  it('click en "Ir al proyecto" emite "back-to-project"', async () => {
    const wrapper = mount(GenerationStep)
    await wrapper.find('.generation-step__back').trigger('click')
    expect(wrapper.emitted('back-to-project')).toHaveLength(1)
  })
})
