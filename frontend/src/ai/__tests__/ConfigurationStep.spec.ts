import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import ConfigurationStep from '../steps/ConfigurationStep.vue'

function mountStep(overrides: Partial<{ submitting: boolean; submitError: string | null }> = {}) {
  return mount(ConfigurationStep, {
    props: { referencePreviewUrl: 'blob:fake', submitting: false, submitError: null, ...overrides },
  })
}

describe('ConfigurationStep.vue', () => {
  it('muestra la imagen de referencia recibida por prop', () => {
    const wrapper = mountStep()
    expect(wrapper.find('img').attributes('src')).toBe('blob:fake')
  })

  it('confirmar sin nombre muestra un error y NO emite "confirm"', async () => {
    const wrapper = mountStep()

    await wrapper.find('button.g-button--primary').trigger('click')

    expect(wrapper.emitted('confirm')).toBeUndefined()
    expect(wrapper.text()).toContain('nombre no puede estar vacío')
  })

  it('confirmar con nombre y el tipo base default (humanoid) emite "confirm" con esos valores', async () => {
    const wrapper = mountStep()

    await wrapper.find('input[aria-label="Nombre del mob"]').setValue('Carcomido')
    await wrapper.find('button.g-button--primary').trigger('click')

    expect(wrapper.emitted('confirm')).toEqual([[{ name: 'Carcomido', baseType: 'humanoid' }]])
  })

  it('elegir otro tipo base lo refleja en el confirm emitido', async () => {
    const wrapper = mountStep()
    await wrapper.find('input[aria-label="Nombre del mob"]').setValue('Tejedora')

    const arachnidButton = wrapper.findAll('.configuration-step__base-type').find((b) => b.text() === 'Arácnido')!
    await arachnidButton.trigger('click')
    await wrapper.find('button.g-button--primary').trigger('click')

    expect(wrapper.emitted('confirm')).toEqual([[{ name: 'Tejedora', baseType: 'arachnid' }]])
  })

  it('click en "Cambiar imagen" emite "back"', async () => {
    const wrapper = mountStep()
    await wrapper.find('.configuration-step__back').trigger('click')
    expect(wrapper.emitted('back')).toHaveLength(1)
  })

  it('muestra submitError cuando se provee, y deshabilita el botón mientras submitting', () => {
    const wrapper = mountStep({ submitting: true, submitError: 'No se pudo crear el mob.' })

    expect(wrapper.text()).toContain('No se pudo crear el mob.')
    expect(wrapper.find('button.g-button--primary').attributes('disabled')).toBeDefined()
  })

  // Post-074 -- rediseño del wizard.
  describe('rediseño post-074', () => {
    it('el contador de caracteres del nombre refleja lo escrito, hasta el máximo de 32', async () => {
      const wrapper = mountStep()

      expect(wrapper.get('.configuration-step__count').text()).toBe('0/32')

      await wrapper.find('input[aria-label="Nombre del mob"]').setValue('Carcomido')

      expect(wrapper.get('.configuration-step__count').text()).toBe('9/32')
      expect(wrapper.find('input[aria-label="Nombre del mob"]').attributes('maxlength')).toBe('32')
    })

    it('"Rig estimado" cambia según el tipo de entidad elegido (decisión del PO)', async () => {
      const wrapper = mountStep()

      expect(wrapper.text()).toContain('Estándar (bipedal)') // humanoid, default

      const arachnidButton = wrapper.findAll('.configuration-step__base-type').find((b) => b.text() === 'Arácnido')!
      await arachnidButton.trigger('click')
      expect(wrapper.text()).toContain('Radial (8 patas)')

      const flyingButton = wrapper.findAll('.configuration-step__base-type').find((b) => b.text() === 'Volador')!
      await flyingButton.trigger('click')
      expect(wrapper.text()).toContain('Alado (par de alas)')
    })

    it('la resolución de textura usa GSelect, nunca un <select> nativo', () => {
      const wrapper = mountStep()

      expect(wrapper.find('select').exists()).toBe(false)
      expect(wrapper.findComponent({ name: 'GSelect' }).exists()).toBe(true)
    })

    it('muestra la fila informativa fija (Vista previa / Formato de salida)', () => {
      const wrapper = mountStep()

      expect(wrapper.text()).toContain('Modelo 3D interactivo')
      expect(wrapper.text()).toContain('.bbmodel (Galgoth)')
    })
  })
})
