import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import ExploreMobCard from '../ExploreMobCard.vue'
import type { MobSummary } from '../../projects/mobsApi'

function mob(overrides: Partial<MobSummary> = {}): MobSummary {
  return { id: 'm1', name: 'Carcomido', baseType: 'humanoid', status: 'draft', thumbnailKey: null, updatedAt: '', ...overrides }
}

describe('ExploreMobCard.vue', () => {
  it('muestra el nombre del mob y su tipo de base', () => {
    const wrapper = mount(ExploreMobCard, { props: { mob: mob({ name: 'Augur', baseType: 'quadruped' }) } })

    expect(wrapper.text()).toContain('Augur')
    expect(wrapper.text()).toContain('Cuadrúpedo')
  })

  it('sin thumbnailKey, muestra el placeholder genérico', () => {
    const wrapper = mount(ExploreMobCard, { props: { mob: mob({ thumbnailKey: null }) } })

    expect(wrapper.find('img').exists()).toBe(false)
    expect(wrapper.find('.explore-mob-card__placeholder').exists()).toBe(true)
  })

  it('muestra el status derivado como pill', () => {
    const wrapper = mount(ExploreMobCard, { props: { mob: mob({ status: 'in_progress' }) } })

    expect(wrapper.text()).toContain('En progreso')
  })

  // Ticket 088 -- ficha estrictamente de lectura: sin visor 3D, sin menú de acciones, sin ningún <button> que abra algo.
  it('no es interactivo -- sin menú ⋮ ni botón que navegue a nada', () => {
    const wrapper = mount(ExploreMobCard, { props: { mob: mob() } })

    expect(wrapper.find('button').exists()).toBe(false)
    expect(wrapper.find('[role="menuitem"]').exists()).toBe(false)
  })
})
