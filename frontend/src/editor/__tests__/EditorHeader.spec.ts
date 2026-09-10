import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import EditorHeader from '../EditorHeader.vue'

/**
 * Ticket 050 (HU-41): el tab "Textura" deja de estar deshabilitado --
 * pasa a ser una tab funcional, controlada por `activeTab`/`update:active-tab`
 * (mismo mecanismo de v-model que ya expone `GTabs.vue`). "Animación"
 * permanece "Próximamente" (fuera de alcance de Fase 3, ticket 002/036).
 */
describe('EditorHeader.vue', () => {
  it('el tab "Textura" ya NO está deshabilitado', () => {
    const wrapper = mount(EditorHeader, { props: { mobName: 'Carcomido', activeTab: 'modelo' } })
    const textura = wrapper.findAll('[role="tab"]').find((el) => el.text().includes('Textura'))!

    expect(textura.attributes('disabled')).toBeUndefined()
    expect(textura.text()).not.toContain('Próximamente')
  })

  it('el tab "Animación" sigue deshabilitado con "Próximamente" visible (fuera de alcance de Fase 3)', () => {
    const wrapper = mount(EditorHeader, { props: { mobName: 'Carcomido', activeTab: 'modelo' } })
    const animacion = wrapper.findAll('[role="tab"]').find((el) => el.text().includes('Animación'))!

    expect(animacion.attributes('disabled')).toBeDefined()
    expect(animacion.text()).toContain('Próximamente')
  })

  it('clickear el tab "Textura" emite update:active-tab con "textura"', async () => {
    const wrapper = mount(EditorHeader, { props: { mobName: 'Carcomido', activeTab: 'modelo' } })
    const textura = wrapper.findAll('[role="tab"]').find((el) => el.text().includes('Textura'))!

    await textura.trigger('click')

    expect(wrapper.emitted('update:activeTab')?.[0]).toEqual(['textura'])
  })

  it('refleja el `activeTab` recibido como la tab activa (aria-selected)', () => {
    const wrapper = mount(EditorHeader, { props: { mobName: 'Carcomido', activeTab: 'textura' } })
    const textura = wrapper.findAll('[role="tab"]').find((el) => el.text().includes('Textura'))!
    const modelo = wrapper.findAll('[role="tab"]').find((el) => el.text().includes('Modelo'))!

    expect(textura.attributes('aria-selected')).toBe('true')
    expect(modelo.attributes('aria-selected')).toBe('false')
  })
})
