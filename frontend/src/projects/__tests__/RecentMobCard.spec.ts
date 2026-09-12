import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { API_BASE_URL } from '../../api/apiConfig'
import RecentMobCard from '../RecentMobCard.vue'
import type { RecentMobSummary } from '../mobsApi'

function mob(overrides: Partial<RecentMobSummary> = {}): RecentMobSummary {
  return {
    id: 'm1',
    projectId: 'p1',
    name: 'Carcomido',
    baseType: 'humanoid',
    status: 'draft',
    thumbnailKey: null,
    updatedAt: new Date().toISOString(),
    ...overrides,
  }
}

describe('RecentMobCard.vue', () => {
  it('muestra el nombre del mob y la fecha relativa de edición', () => {
    const wrapper = mount(RecentMobCard, { props: { mob: mob({ name: 'Augur', updatedAt: new Date().toISOString() }) } })

    expect(wrapper.text()).toContain('Augur')
    expect(wrapper.text()).toContain('Editado hoy')
  })

  it('sin thumbnailKey, muestra el placeholder genérico', () => {
    const wrapper = mount(RecentMobCard, { props: { mob: mob({ thumbnailKey: null }) } })

    expect(wrapper.find('img').exists()).toBe(false)
    expect(wrapper.find('.recent-mob-card__placeholder').exists()).toBe(true)
  })

  it('con thumbnailKey, renderiza la imagen real con la URL completa del backend', () => {
    const wrapper = mount(RecentMobCard, { props: { mob: mob({ thumbnailKey: '/api/mobs/m1/thumbnail' }) } })

    expect(wrapper.find('img').attributes('src')).toBe(`${API_BASE_URL}/api/mobs/m1/thumbnail`)
  })

  it('mapea el status "in_progress" (guion bajo en la BD) al pill "En progreso"', () => {
    const wrapper = mount(RecentMobCard, { props: { mob: mob({ status: 'in_progress' }) } })

    expect(wrapper.text()).toContain('En progreso')
  })

  it('click en abrir emite open con el id real del mob (no el projectId)', async () => {
    const wrapper = mount(RecentMobCard, { props: { mob: mob({ id: 'm9', projectId: 'p9' }) } })

    await wrapper.find('.recent-mob-card__open').trigger('click')

    expect(wrapper.emitted('open')).toEqual([['m9']])
  })

  it('el menú incluye Renombrar/Exportar/Eliminar, funcional (no decorativo)', async () => {
    const wrapper = mount(RecentMobCard, { props: { mob: mob({ id: 'm9' }) } })

    await wrapper.find('.g-menu__trigger').trigger('click')
    const items = wrapper.findAll('[role="menuitem"]').map((i) => i.text())

    expect(items).toEqual(['Renombrar', 'Exportar', 'Eliminar'])
  })

  it('elegir "Eliminar" del menú emite action con la key real y el id del mob', async () => {
    const wrapper = mount(RecentMobCard, { props: { mob: mob({ id: 'm9' }) } })

    await wrapper.find('.g-menu__trigger').trigger('click')
    await wrapper.findAll('[role="menuitem"]').find((i) => i.text() === 'Eliminar')!.trigger('click')

    expect(wrapper.emitted('action')).toEqual([['delete', 'm9']])
  })

  it('clic en el menú de acciones NO dispara open (evita navegar por accidente)', async () => {
    const wrapper = mount(RecentMobCard, { props: { mob: mob({ id: 'm9' }) } })

    await wrapper.find('.recent-mob-card__side').trigger('click')

    expect(wrapper.emitted('open')).toBeUndefined()
  })
})
