import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import ReferenceStep from '../steps/ReferenceStep.vue'

function fileOf(bytes: number, type: string): File {
  return new File([new Uint8Array(bytes)], 'test.png', { type })
}

describe('ReferenceStep.vue', () => {
  it('un PNG válido dentro del límite emite "selected" con el archivo', async () => {
    const wrapper = mount(ReferenceStep)
    const input = wrapper.find('input[type="file"]')
    const file = fileOf(1024, 'image/png')

    Object.defineProperty(input.element, 'files', { value: [file] })
    await input.trigger('change')

    expect(wrapper.emitted('selected')).toEqual([[file]])
    expect(wrapper.find('.reference-step__error').exists()).toBe(false)
  })

  it('un formato no soportado se rechaza con mensaje claro y NO emite "selected"', async () => {
    const wrapper = mount(ReferenceStep)
    const input = wrapper.find('input[type="file"]')
    const file = fileOf(1024, 'image/gif')

    Object.defineProperty(input.element, 'files', { value: [file] })
    await input.trigger('change')

    expect(wrapper.emitted('selected')).toBeUndefined()
    expect(wrapper.text()).toContain('image/gif')
  })

  it('un archivo que excede 10MB se rechaza con mensaje claro y NO emite "selected"', async () => {
    const wrapper = mount(ReferenceStep)
    const input = wrapper.find('input[type="file"]')
    const file = fileOf(10 * 1024 * 1024 + 1, 'image/png')

    Object.defineProperty(input.element, 'files', { value: [file] })
    await input.trigger('change')

    expect(wrapper.emitted('selected')).toBeUndefined()
    expect(wrapper.text()).toContain('10MB')
  })
})
