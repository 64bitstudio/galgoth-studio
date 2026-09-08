import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import GStatusPill from '../GStatusPill.vue'

describe('GStatusPill', () => {
  it.each([
    ['ready', 'Ready'],
    ['in-progress', 'In progress'],
    ['draft', 'Draft'],
  ] as const)(
    'status=%s muestra la etiqueta "%s" Y un ícono -- nunca solo color',
    (status, label) => {
      const wrapper = mount(GStatusPill, { props: { status } })
      expect(wrapper.text()).toContain(label)
      expect(wrapper.find('svg').exists()).toBe(true)
    },
  )
})
