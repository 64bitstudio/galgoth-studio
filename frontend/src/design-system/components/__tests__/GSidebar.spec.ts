import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import GSidebar from '../GSidebar.vue'

describe('GSidebar', () => {
  it('renders exactly los items del mockup 01, sin "Volver" ni "Nuevo proyecto"', () => {
    const wrapper = mount(GSidebar, { props: { active: 'projects' } })
    const labels = wrapper
      .findAll('.g-sidebar__item')
      .map((el) => el.text().trim())

    expect(labels).toEqual([
      'Inicio',
      'Mis proyectos',
      'Explorar',
      'Plantillas',
      'Configuración',
      'Usuario',
    ])
    expect(labels).not.toContain('Volver')
    expect(labels).not.toContain('Nuevo proyecto')
  })

  it('marca el item activo con aria-current, no solo con color', () => {
    const wrapper = mount(GSidebar, { props: { active: 'explore' } })
    const active = wrapper.find('[aria-current="page"]')
    expect(active.text()).toBe('Explorar')
    expect(active.classes()).toContain('g-sidebar__item--active')
  })

  it('emite select con la key del item clickeado', async () => {
    const wrapper = mount(GSidebar, { props: { active: 'projects' } })
    await wrapper.findAll('.g-sidebar__item')[0]!.trigger('click')
    expect(wrapper.emitted('select')?.[0]).toEqual(['home'])
  })
})
