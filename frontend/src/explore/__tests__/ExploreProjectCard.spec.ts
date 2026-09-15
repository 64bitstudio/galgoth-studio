import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import ExploreProjectCard from '../ExploreProjectCard.vue'
import type { ProjectSummary } from '../../projects/projectsApi'

function project(overrides: Partial<ProjectSummary> = {}): ProjectSummary {
  return {
    id: 'p1',
    name: 'Galgoth',
    description: null,
    mobCount: 0,
    mobThumbnails: [],
    status: 'draft',
    visibility: 'PUBLIC',
    ownerDisplayName: null,
    createdAt: '',
    updatedAt: '',
    ...overrides,
  }
}

describe('ExploreProjectCard.vue', () => {
  it('muestra el nombre del proyecto', () => {
    const wrapper = mount(ExploreProjectCard, { props: { project: project({ name: 'Carcomido' }) } })

    expect(wrapper.text()).toContain('Carcomido')
  })

  it('con ownerDisplayName, muestra "por <nombre>"', () => {
    const wrapper = mount(ExploreProjectCard, { props: { project: project({ ownerDisplayName: 'Ada Lovelace' }) } })

    expect(wrapper.text()).toContain('por Ada Lovelace')
  })

  it('sin ownerDisplayName, no muestra ninguna línea de autor', () => {
    const wrapper = mount(ExploreProjectCard, { props: { project: project({ ownerDisplayName: null }) } })

    expect(wrapper.text()).not.toContain('por ')
  })

  it('con description, la muestra', () => {
    const wrapper = mount(ExploreProjectCard, { props: { project: project({ description: 'Un bosque maldito.' }) } })

    expect(wrapper.text()).toContain('Un bosque maldito.')
  })

  it('nunca muestra ningún menú de acciones (ticket 088: sin editar/duplicar/eliminar en Explorar)', () => {
    const wrapper = mount(ExploreProjectCard, { props: { project: project() } })

    expect(wrapper.find('[role="menuitem"]').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('Eliminar')
    expect(wrapper.text()).not.toContain('Duplicar')
  })

  it('al hacer click, emite "open" con el id del proyecto', async () => {
    const wrapper = mount(ExploreProjectCard, { props: { project: project({ id: 'p9' }) } })

    await wrapper.find('button').trigger('click')

    expect(wrapper.emitted('open')).toEqual([['p9']])
  })
})
