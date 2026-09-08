import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import GSidebar from '../GSidebar.vue'

describe('GSidebar', () => {
  it('renders exactly the 4 items fijos del Visual Contract, sin "Volver"', () => {
    const wrapper = mount(GSidebar, { props: { active: 'projects' } })
    const labels = wrapper
      .findAll('.g-sidebar__item')
      .map((el) => el.text().trim())

    expect(labels).toEqual([
      'Nuevo proyecto',
      'Mis proyectos',
      'Recientes',
      'Configuración',
    ])
    expect(labels).not.toContain('Volver')
  })

  it('marca el item activo con aria-current, no solo con color', () => {
    const wrapper = mount(GSidebar, { props: { active: 'recent' } })
    const active = wrapper.find('[aria-current="page"]')
    expect(active.text()).toBe('Recientes')
    expect(active.classes()).toContain('g-sidebar__item--active')
  })

  it('emite select con la key del item clickeado', async () => {
    const wrapper = mount(GSidebar, { props: { active: 'projects' } })
    await wrapper.findAll('.g-sidebar__item')[0]!.trigger('click')
    expect(wrapper.emitted('select')?.[0]).toEqual(['new-project'])
  })
})
