import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import GButton from '../GButton.vue'

describe('GButton', () => {
  it('renderiza el slot y emite click', async () => {
    const wrapper = mount(GButton, { slots: { default: 'Exportar ahora' } })
    expect(wrapper.text()).toBe('Exportar ahora')

    await wrapper.trigger('click')
    expect(wrapper.emitted('click')).toHaveLength(1)
  })

  it('respeta la variante como clase modificadora', () => {
    const wrapper = mount(GButton, { props: { variant: 'primary' } })
    expect(wrapper.classes()).toContain('g-button--primary')
  })

  it('deshabilitado usa el atributo nativo disabled (no solo estilo)', () => {
    const wrapper = mount(GButton, { props: { disabled: true } })
    expect(wrapper.attributes('disabled')).toBeDefined()
  })
})
