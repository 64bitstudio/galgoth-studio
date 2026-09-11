import { beforeEach, describe, expect, it } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
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

  describe('ticket 068: colapsar el sidebar', () => {
    beforeEach(() => {
      localStorage.clear()
    })

    it('arranca expandido por defecto (sin nada en localStorage) y oculta las etiquetas al colapsar', async () => {
      const wrapper = mount(GSidebar, { props: { active: 'projects' } })
      await flushPromises()
      expect(wrapper.find('.g-sidebar__brand').exists()).toBe(true)
      expect(wrapper.find('.g-sidebar__item span').exists()).toBe(true)

      await wrapper.get('.g-sidebar__toggle').trigger('click')

      expect(wrapper.find('.g-sidebar--collapsed').exists()).toBe(true)
      expect(wrapper.find('.g-sidebar__brand').exists()).toBe(false)
      expect(wrapper.find('.g-sidebar__item span').exists()).toBe(false)
    })

    it('los items siguen teniendo un nombre accesible (aria-label) aunque el texto visible desaparezca al colapsar', async () => {
      const wrapper = mount(GSidebar, { props: { active: 'projects' } })
      await wrapper.get('.g-sidebar__toggle').trigger('click')
      const firstItem = wrapper.findAll('.g-sidebar__item')[0]!
      expect(firstItem.attributes('aria-label')).toBe('Inicio')
    })

    it('el estado colapsado persiste en localStorage entre montajes (GSidebar se remonta en cada navegación)', async () => {
      const first = mount(GSidebar, { props: { active: 'projects' } })
      await first.get('.g-sidebar__toggle').trigger('click')
      expect(localStorage.getItem('gsidebar-collapsed')).toBe('true')
      first.unmount()

      const second = mount(GSidebar, { props: { active: 'projects' } })
      await flushPromises()
      expect(second.find('.g-sidebar--collapsed').exists()).toBe(true)
    })

    it('select sigue emitiendo la key correcta con el sidebar colapsado', async () => {
      const wrapper = mount(GSidebar, { props: { active: 'projects' } })
      await wrapper.get('.g-sidebar__toggle').trigger('click')
      await wrapper.findAll('.g-sidebar__item')[1]!.trigger('click')
      expect(wrapper.emitted('select')?.[0]).toEqual(['projects'])
    })
  })
})
