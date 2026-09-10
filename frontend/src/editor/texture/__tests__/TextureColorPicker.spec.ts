import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import TextureColorPicker from '../TextureColorPicker.vue'
import { PALETTE } from '../texturePalette'

describe('TextureColorPicker.vue', () => {
  it('el trigger muestra el color activo vigente como fondo del swatch', () => {
    const wrapper = mount(TextureColorPicker, { props: { modelValue: '#e0574c' } })

    expect(wrapper.get('.texture-color-picker__swatch-main').attributes('style')).toContain('background: rgb(224, 87, 76)')
  })

  it('clickear un swatch de la paleta fija emite update:modelValue con ese hex', async () => {
    const wrapper = mount(TextureColorPicker, { props: { modelValue: PALETTE[0]! } })
    await wrapper.get('.texture-color-picker__trigger').trigger('click')

    const target = PALETTE[2]!
    await wrapper.get(`button[aria-label="Color ${target}"]`).trigger('click')

    expect(wrapper.emitted('update:modelValue')?.[0]).toEqual([target])
  })

  it('AC: el campo hex acepta un color que NO está en la paleta fija y lo confirma con Enter', async () => {
    const wrapper = mount(TextureColorPicker, { props: { modelValue: PALETTE[0]! } })
    await wrapper.get('.texture-color-picker__trigger').trigger('click')

    const hexInput = wrapper.get('input[aria-label="Código hexadecimal del color activo"]')
    await hexInput.setValue('#123abc')
    await hexInput.trigger('keydown', { key: 'Enter' })

    expect(wrapper.emitted('update:modelValue')?.[0]).toEqual(['#123abc'])
  })

  it('un hex inválido en el campo de texto se descarta -- vuelve al color activo vigente, sin emitir', async () => {
    const wrapper = mount(TextureColorPicker, { props: { modelValue: '#48e5a0' } })
    await wrapper.get('.texture-color-picker__trigger').trigger('click')

    const hexInput = wrapper.get('input[aria-label="Código hexadecimal del color activo"]')
    await hexInput.setValue('no-es-un-hex')
    await hexInput.trigger('blur')

    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
    expect((hexInput.element as HTMLInputElement).value).toBe('#48e5a0')
  })

  it('AC: el swatch "personalizado" dispara el input de color nativo oculto (única excepción a "sin controles nativos")', async () => {
    const wrapper = mount(TextureColorPicker, { props: { modelValue: PALETTE[0]! } })
    await wrapper.get('.texture-color-picker__trigger').trigger('click')

    const nativeInput = wrapper.get('input[type="color"]').element as HTMLInputElement
    const clickSpy = vi.fn()
    nativeInput.click = clickSpy

    await wrapper.get('button[aria-label="Elegir cualquier color"]').trigger('click')

    expect(clickSpy).toHaveBeenCalled()
  })

  it('a11y: el trigger, cada swatch y el campo hex tienen un nombre accesible', async () => {
    const wrapper = mount(TextureColorPicker, { props: { modelValue: PALETTE[0]! } })
    expect(wrapper.get('button[aria-label="Color activo"]')).toBeTruthy()

    await wrapper.get('.texture-color-picker__trigger').trigger('click')
    for (const color of PALETTE) {
      expect(wrapper.find(`button[aria-label="Color ${color}"]`).exists()).toBe(true)
    }
    expect(wrapper.find('button[aria-label="Elegir cualquier color"]').exists()).toBe(true)
    expect(wrapper.find('input[aria-label="Código hexadecimal del color activo"]').exists()).toBe(true)
  })

  it('nunca renderiza un <select> nativo', () => {
    const wrapper = mount(TextureColorPicker, { props: { modelValue: PALETTE[0]! } })
    expect(wrapper.find('select').exists()).toBe(false)
  })
})
