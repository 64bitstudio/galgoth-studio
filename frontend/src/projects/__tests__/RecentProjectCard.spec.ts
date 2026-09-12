import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { API_BASE_URL } from '../../api/apiConfig'
import RecentProjectCard from '../RecentProjectCard.vue'
import type { ProjectSummary } from '../projectsApi'

function project(overrides: Partial<ProjectSummary> = {}): ProjectSummary {
  return {
    id: 'p1',
    name: 'Galgoth',
    description: null,
    mobCount: 0,
    mobThumbnails: [],
    status: 'draft',
    createdAt: '',
    updatedAt: new Date().toISOString(),
    ...overrides,
  }
}

describe('RecentProjectCard.vue', () => {
  it('muestra el nombre del proyecto y el conteo/fecha en líneas separadas (no encimados)', () => {
    const wrapper = mount(RecentProjectCard, { props: { project: project({ name: 'Carcomido', mobCount: 3 }) } })

    expect(wrapper.find('.recent-project-card__name').text()).toBe('Carcomido')
    expect(wrapper.find('.recent-project-card__meta').text()).toContain('3 mobs')
    expect(wrapper.find('.recent-project-card__meta').text()).toContain('Editado hoy')
  })

  it('sin mobs, muestra un único placeholder genérico', () => {
    const wrapper = mount(RecentProjectCard, { props: { project: project() } })

    expect(wrapper.findAll('.recent-project-card__thumbnail')).toHaveLength(1)
    expect(wrapper.find('.recent-project-card__placeholder').exists()).toBe(true)
  })

  it('con más de 3 mobs, muestra hasta 3 miniaturas y el indicador "+N"', () => {
    const p = project({
      mobCount: 5,
      mobThumbnails: [
        { mobId: 'm1', thumbnailKey: null },
        { mobId: 'm2', thumbnailKey: null },
        { mobId: 'm3', thumbnailKey: null },
      ],
    })
    const wrapper = mount(RecentProjectCard, { props: { project: p } })

    expect(wrapper.findAll('.recent-project-card__thumbnail')).toHaveLength(3)
    expect(wrapper.text()).toContain('+2')
  })

  it('una miniatura CON thumbnailKey renderiza la imagen real con la URL completa del backend', () => {
    const p = project({ mobCount: 1, mobThumbnails: [{ mobId: 'm1', thumbnailKey: '/api/mobs/m1/thumbnail' }] })
    const wrapper = mount(RecentProjectCard, { props: { project: p } })

    expect(wrapper.find('img').attributes('src')).toBe(`${API_BASE_URL}/api/mobs/m1/thumbnail`)
  })

  it('clic en la tarjeta emite open con el id del proyecto', async () => {
    const wrapper = mount(RecentProjectCard, { props: { project: project({ id: 'p42' }) } })

    await wrapper.find('.recent-project-card__open').trigger('click')

    expect(wrapper.emitted('open')).toEqual([['p42']])
  })

  it('seleccionar "Renombrar" en el menú emite action con la key y el id del proyecto', async () => {
    const wrapper = mount(RecentProjectCard, { props: { project: project({ id: 'p42' }) } })

    await wrapper.find('.g-menu__trigger').trigger('click')
    const renameItem = wrapper.findAll('[role="menuitem"]').find((i) => i.text().includes('Renombrar'))!
    await renameItem.trigger('click')

    expect(wrapper.emitted('action')).toEqual([['rename', 'p42']])
  })

  it('el ítem "Export" del menú está deshabilitado con su razón visible', async () => {
    const wrapper = mount(RecentProjectCard, { props: { project: project() } })

    await wrapper.find('.g-menu__trigger').trigger('click')
    const exportItem = wrapper.findAll('[role="menuitem"]').find((i) => i.text().includes('Exportar'))!

    expect(exportItem.attributes('disabled')).toBeDefined()
  })
})
