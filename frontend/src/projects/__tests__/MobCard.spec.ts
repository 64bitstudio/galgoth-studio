import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { API_BASE_URL } from '../../api/apiConfig'
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

  it('con thumbnailKey, renderiza la imagen real con la URL completa del backend (ticket 023: la clave llega como ruta relativa)', () => {
    const wrapper = mount(MobCard, { props: { mob: mob({ thumbnailKey: '/api/mobs/m1/thumbnail' }) } })

    expect(wrapper.find('img').attributes('src')).toBe(`${API_BASE_URL}/api/mobs/m1/thumbnail`)
  })

  it('mapea el status "draft" al pill correspondiente', () => {
    const wrapper = mount(MobCard, { props: { mob: mob({ status: 'draft' }) } })

    expect(wrapper.text()).toContain('Draft')
  })

  it('mapea el status "in_progress" (guion bajo en la BD) al pill "in-progress" (guion medio)', () => {
    const wrapper = mount(MobCard, { props: { mob: mob({ status: 'in_progress' }) } })

    expect(wrapper.text()).toContain('En progreso')
  })

  it('mapea el status "ready" al pill correspondiente', () => {
    const wrapper = mount(MobCard, { props: { mob: mob({ status: 'ready' }) } })

    expect(wrapper.text()).toContain('Listo')
  })

  // Ticket 073 -- el footer ahora muestra el tipo de base (ícono + etiqueta) en vez de solo el pill de estado.
  describe('ticket 073 -- footer con tipo de base', () => {
    it.each([
      ['humanoid', 'Humanoide'],
      ['arachnid', 'Arácnido'],
      ['quadruped', 'Cuadrúpedo'],
      ['flying', 'Volador'],
      ['custom', 'Personalizado'],
    ] as const)('baseType "%s" muestra la etiqueta "%s"', (baseType, label) => {
      const wrapper = mount(MobCard, { props: { mob: mob({ baseType }) } })

      expect(wrapper.get('.mob-card__type').text()).toContain(label)
    })

    it('el footer incluye la fecha relativa de edición', () => {
      const wrapper = mount(MobCard, { props: { mob: mob({ updatedAt: new Date().toISOString() }) } })

      expect(wrapper.get('.mob-card__type').text()).toContain('·')
    })

    it('el pill de estado se superpone a la miniatura, no vive en el footer', () => {
      const wrapper = mount(MobCard, { props: { mob: mob({ status: 'ready' }) } })

      expect(wrapper.get('.mob-card__thumbnail').find('.g-status-pill').exists()).toBe(true)
      expect(wrapper.get('.mob-card__footer').find('.g-status-pill').exists()).toBe(false)
    })
  })

  describe('ticket 039 -- menú de acciones ⋮', () => {
    it('click en abrir emite open con el id real del mob', async () => {
      const wrapper = mount(MobCard, { props: { mob: mob({ id: 'm9' }) } })

      await wrapper.find('.mob-card__open').trigger('click')

      expect(wrapper.emitted('open')).toEqual([['m9']])
    })

    it('el menú incluye Renombrar/Exportar/Eliminar, funcional (no decorativo)', async () => {
      const wrapper = mount(MobCard, { props: { mob: mob({ id: 'm9' }) } })

      await wrapper.find('.g-menu__trigger').trigger('click')
      const items = wrapper.findAll('[role="menuitem"]').map((i) => i.text())

      expect(items).toEqual(['Renombrar', 'Exportar', 'Eliminar'])
    })

    it('elegir "Eliminar" del menú emite action con la key real y el id del mob', async () => {
      const wrapper = mount(MobCard, { props: { mob: mob({ id: 'm9' }) } })

      await wrapper.find('.g-menu__trigger').trigger('click')
      await wrapper.findAll('[role="menuitem"]').find((i) => i.text() === 'Eliminar')!.trigger('click')

      expect(wrapper.emitted('action')).toEqual([['delete', 'm9']])
    })
  })
})
