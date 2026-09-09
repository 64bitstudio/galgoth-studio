import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import MobCard from '../MobCard.vue'
import type { MobSummary } from '../mobsApi'

function mob(overrides: Partial<MobSummary> = {}): MobSummary {
  return { id: 'm1', name: 'Carcomido', baseType: 'humanoid', status: 'draft', thumbnailKey: null, updatedAt: '', ...overrides }
}

describe('MobCard.vue', () => {
  it('muestra el nombre del mob', () => {
    const wrapper = mount(MobCard, { props: { mob: mob({ name: 'Augur' }) } })

    expect(wrapper.text()).toContain('Augur')
  })

  it('sin thumbnailKey, muestra el placeholder genérico', () => {
    const wrapper = mount(MobCard, { props: { mob: mob({ thumbnailKey: null }) } })

    expect(wrapper.find('img').exists()).toBe(false)
    expect(wrapper.find('.mob-card__placeholder').exists()).toBe(true)
  })

  it('con thumbnailKey, renderiza la imagen real', () => {
    const wrapper = mount(MobCard, { props: { mob: mob({ thumbnailKey: '/thumb.png' }) } })

    expect(wrapper.find('img').attributes('src')).toBe('/thumb.png')
  })

  it('mapea el status "draft" al pill correspondiente', () => {
    const wrapper = mount(MobCard, { props: { mob: mob({ status: 'draft' }) } })

    expect(wrapper.text()).toContain('Draft')
  })

  it('mapea el status "in_progress" (guion bajo en la BD) al pill "in-progress" (guion medio)', () => {
    const wrapper = mount(MobCard, { props: { mob: mob({ status: 'in_progress' }) } })

    expect(wrapper.text()).toContain('In progress')
  })

  it('mapea el status "ready" al pill correspondiente', () => {
    const wrapper = mount(MobCard, { props: { mob: mob({ status: 'ready' }) } })

    expect(wrapper.text()).toContain('Ready')
  })
})
