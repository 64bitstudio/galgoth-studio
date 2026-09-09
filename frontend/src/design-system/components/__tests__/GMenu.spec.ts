import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import GMenu, { type GMenuItem } from '../GMenu.vue'

const ITEMS: GMenuItem[] = [
  { key: 'rename', label: 'Rename' },
  { key: 'export', label: 'Export', disabled: true, disabledReason: 'Disponible próximamente' },
  { key: 'delete', label: 'Delete', danger: true },
]

describe('GMenu.vue', () => {
  it('el menú está cerrado por defecto', () => {
    const wrapper = mount(GMenu, { props: { items: ITEMS, label: 'Acciones' } })

    expect(wrapper.find('[role="menu"]').exists()).toBe(false)
  })

  it('el trigger abre y cierra el menú', async () => {
    const wrapper = mount(GMenu, { props: { items: ITEMS, label: 'Acciones' } })

    await wrapper.find('.g-menu__trigger').trigger('click')
    expect(wrapper.find('[role="menu"]').exists()).toBe(true)

    await wrapper.find('.g-menu__trigger').trigger('click')
    expect(wrapper.find('[role="menu"]').exists()).toBe(false)
  })

  it('un ítem deshabilitado muestra su razón como texto visible', async () => {
    const wrapper = mount(GMenu, { props: { items: ITEMS, label: 'Acciones' } })
    await wrapper.find('.g-menu__trigger').trigger('click')

    const exportItem = wrapper.findAll('[role="menuitem"]').find((i) => i.text().includes('Export'))!
    expect(exportItem.text()).toContain('Disponible próximamente')
    expect(exportItem.attributes('disabled')).toBeDefined()
  })

  it('clic en un ítem habilitado emite select con su key y cierra el menú', async () => {
    const wrapper = mount(GMenu, { props: { items: ITEMS, label: 'Acciones' } })
    await wrapper.find('.g-menu__trigger').trigger('click')

    const renameItem = wrapper.findAll('[role="menuitem"]').find((i) => i.text().includes('Rename'))!
    await renameItem.trigger('click')

    expect(wrapper.emitted('select')).toEqual([['rename']])
    expect(wrapper.find('[role="menu"]').exists()).toBe(false)
  })

  it('clic en un ítem deshabilitado no emite select ni cierra el menú', async () => {
    const wrapper = mount(GMenu, { props: { items: ITEMS, label: 'Acciones' } })
    await wrapper.find('.g-menu__trigger').trigger('click')

    const exportItem = wrapper.findAll('[role="menuitem"]').find((i) => i.text().includes('Export'))!
    await exportItem.trigger('click')

    expect(wrapper.emitted('select')).toBeUndefined()
    expect(wrapper.find('[role="menu"]').exists()).toBe(true)
  })
})
