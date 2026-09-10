import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import IconButton from '../IconButton.vue'

/**
 * Ticket 039 -- corrección de producto: `title=""` nativo dejó de ser
 * aceptable como única solución de tooltip (sin estilos, sin delay
 * consistente). Cubre que el nombre accesible real sigue siendo
 * `aria-label` (nunca depende del tooltip visual) y que el shortcut
 * solo aparece cuando se pasa explícitamente.
 */
describe('IconButton.vue', () => {
  it('el aria-label es el nombre accesible real -- no depende del tooltip visual', () => {
    const wrapper = mount(IconButton, { props: {label: 'Mover'}, slots: {default: '<svg />'} })

    expect(wrapper.find('button').attributes('aria-label')).toBe('Mover')
  })

  it('ya no usa title="" nativo -- el tooltip es un elemento propio del design system', () => {
    const wrapper = mount(IconButton, { props: {label: 'Mover'}, slots: {default: '<svg />'} })

    expect(wrapper.find('button').attributes('title')).toBeUndefined()
    expect(wrapper.find('.icon-button__tooltip').exists()).toBe(true)
    expect(wrapper.find('.icon-button__tooltip').text()).toBe('Mover')
  })

  it('el tooltip es aria-hidden -- un lector de pantalla nunca lo anuncia dos veces junto al aria-label', () => {
    const wrapper = mount(IconButton, { props: {label: 'Mover'}, slots: {default: '<svg />'} })

    expect(wrapper.find('.icon-button__tooltip').attributes('aria-hidden')).toBe('true')
  })

  it('sin shortcut explícito, no muestra ningún atajo inventado', () => {
    const wrapper = mount(IconButton, { props: {label: 'Duplicar'}, slots: {default: '<svg />'} })

    expect(wrapper.find('.icon-button__tooltip-shortcut').exists()).toBe(false)
  })

  it('con shortcut explícito, lo muestra dentro del tooltip', () => {
    const wrapper = mount(IconButton, { props: {label: 'Deshacer', shortcut: 'Ctrl+Z'}, slots: {default: '<svg />'} })

    expect(wrapper.find('.icon-button__tooltip-shortcut').text()).toBe('Ctrl+Z')
  })
})
