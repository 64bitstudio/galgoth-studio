import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import GTabs, { type GTabItem } from '../GTabs.vue'

const items: GTabItem[] = [
  { key: 'modelo', label: 'Modelo' },
  { key: 'textura', label: 'Textura', disabled: true, disabledReason: 'próximamente' },
  { key: 'animacion', label: 'Animación', disabled: true, disabledReason: 'próximamente' },
]

describe('GTabs', () => {
  it('las tabs deshabilitadas siguen presentes (nunca ocultas) con motivo visible', () => {
    const wrapper = mount(GTabs, { props: { items, modelValue: 'modelo' } })
    const labels = wrapper.findAll('[role="tab"]').map((el) => el.text())

    expect(labels).toContain('Modelo')
    expect(labels.some((l) => l.includes('Textura'))).toBe(true)
    expect(labels.some((l) => l.includes('Animación'))).toBe(true)
    expect(wrapper.text()).toContain('próximamente')
  })

  it('clickear una tab deshabilitada NO emite update:modelValue', async () => {
    const wrapper = mount(GTabs, { props: { items, modelValue: 'modelo' } })
    const textura = wrapper.findAll('[role="tab"]')[1]!
    await textura.trigger('click')
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
  })

  it('clickear una tab habilitada emite update:modelValue con su key', async () => {
    const wrapper = mount(GTabs, {
      props: { items: [...items, { key: 'export', label: 'Exportar' }], modelValue: 'modelo' },
    })
    const exportTab = wrapper.findAll('[role="tab"]')[3]!
    await exportTab.trigger('click')
    expect(wrapper.emitted('update:modelValue')?.[0]).toEqual(['export'])
  })
})
