import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import ProjectCard from '../ProjectCard.vue'
import type { ProjectSummary } from '../projectsApi'

function project(overrides: Partial<ProjectSummary> = {}): ProjectSummary {
  return {
    id: 'p1',
    name: 'Galgoth',
    mobCount: 0,
    mobThumbnails: [],
    createdAt: '',
    updatedAt: '',
    ...overrides,
  }
}

describe('ProjectCard.vue', () => {
  it('muestra el nombre del proyecto', () => {
    const wrapper = mount(ProjectCard, { props: { project: project({ name: 'Carcomido' }) } })

    expect(wrapper.text()).toContain('Carcomido')
  })

  it('sin mobs, muestra un único placeholder genérico', () => {
    const wrapper = mount(ProjectCard, { props: { project: project() } })

    expect(wrapper.findAll('.project-card__thumbnail')).toHaveLength(1)
    expect(wrapper.find('.project-card__placeholder').exists()).toBe(true)
  })

  it('con más de 3 mobs, muestra hasta 3 miniaturas y el indicador "+N", AC #3', () => {
    const p = project({
      mobCount: 5,
      mobThumbnails: [
        { mobId: 'm1', thumbnailKey: null },
        { mobId: 'm2', thumbnailKey: null },
        { mobId: 'm3', thumbnailKey: null },
      ],
    })
    const wrapper = mount(ProjectCard, { props: { project: p } })

    expect(wrapper.findAll('.project-card__thumbnail')).toHaveLength(3)
    expect(wrapper.text()).toContain('+2')
  })

  it('una miniatura sin thumbnailKey muestra el placeholder, no un <img> roto, AC #5', () => {
    const p = project({ mobCount: 1, mobThumbnails: [{ mobId: 'm1', thumbnailKey: null }] })
    const wrapper = mount(ProjectCard, { props: { project: p } })

    expect(wrapper.find('img').exists()).toBe(false)
    expect(wrapper.find('.project-card__placeholder').exists()).toBe(true)
  })

  it('una miniatura CON thumbnailKey renderiza la imagen real', () => {
    const p = project({ mobCount: 1, mobThumbnails: [{ mobId: 'm1', thumbnailKey: '/thumb.png' }] })
    const wrapper = mount(ProjectCard, { props: { project: p } })

    expect(wrapper.find('img').attributes('src')).toBe('/thumb.png')
  })

  it('clic en la tarjeta emite open con el id del proyecto', async () => {
    const wrapper = mount(ProjectCard, { props: { project: project({ id: 'p42' }) } })

    await wrapper.find('.project-card').trigger('click')

    expect(wrapper.emitted('open')).toEqual([['p42']])
  })

  it('clic en el menú de acciones NO dispara open (evita navegar por accidente)', async () => {
    const wrapper = mount(ProjectCard, { props: { project: project({ id: 'p42' }) } })

    await wrapper.find('.project-card__menu').trigger('click')

    expect(wrapper.emitted('open')).toBeUndefined()
  })

  it('seleccionar "Rename" en el menú emite action con la key y el id del proyecto', async () => {
    const wrapper = mount(ProjectCard, { props: { project: project({ id: 'p42' }) } })

    await wrapper.find('.g-menu__trigger').trigger('click')
    const renameItem = wrapper.findAll('[role="menuitem"]').find((i) => i.text().includes('Rename'))!
    await renameItem.trigger('click')

    expect(wrapper.emitted('action')).toEqual([['rename', 'p42']])
  })

  it('el ítem "Export" del menú está deshabilitado con su razón visible', async () => {
    const wrapper = mount(ProjectCard, { props: { project: project() } })

    await wrapper.find('.g-menu__trigger').trigger('click')
    const exportItem = wrapper.findAll('[role="menuitem"]').find((i) => i.text().includes('Export'))!

    expect(exportItem.attributes('disabled')).toBeDefined()
    expect(exportItem.text()).toContain('Disponible cuando el proyecto tenga mobs exportables')
  })
})
