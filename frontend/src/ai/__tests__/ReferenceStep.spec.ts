import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
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

  // Post-074 -- rediseño del wizard: botón "Seleccionar imagen" explícito, HERMANO del botón del dropzone (nunca anidado, Sonar S6819).
  describe('rediseño post-074', () => {
    it('el botón "Seleccionar imagen" y el del dropzone son botones hermanos, no anidados', () => {
      const wrapper = mount(ReferenceStep)
      const dropzone = wrapper.get('.reference-step__dropzone')

      expect(dropzone.findAll('button')).toHaveLength(2)
      // Ningún <button> contiene a otro <button> como descendiente -- HTML inválido si lo hiciera.
      for (const button of dropzone.findAll('button')) {
        expect(button.find('button').exists()).toBe(false)
      }
    })

    it('click en "Seleccionar imagen" abre el mismo selector de archivo que el dropzone', async () => {
      const wrapper = mount(ReferenceStep)
      const clickSpy = vi.spyOn(wrapper.find('input[type="file"]').element as HTMLInputElement, 'click')

      await wrapper.findAll('button').find((b) => b.text().includes('Seleccionar imagen'))!.trigger('click')

      expect(clickSpy).toHaveBeenCalled()
    })

    it('muestra la sección decorativa "Ejemplos de referencias" sin ningún control clickeable dentro', () => {
      const wrapper = mount(ReferenceStep)
      const examples = wrapper.get('.reference-step__examples')

      expect(examples.text()).toContain('Ejemplos de referencias')
      expect(examples.text()).toContain('Ver más ejemplos')
      expect(examples.find('button').exists()).toBe(false)
      expect(examples.find('a').exists()).toBe(false)
    })

    it('muestra el panel de consejos "Consejos para mejores resultados"', () => {
      const wrapper = mount(ReferenceStep)

      expect(wrapper.text()).toContain('Consejos para mejores resultados')
      expect(wrapper.text()).toContain('Usa concept art claro')
      expect(wrapper.text()).toContain('Vista de cuerpo completo')
      expect(wrapper.text()).toContain('Buen contraste e iluminación')
    })
  })
})
