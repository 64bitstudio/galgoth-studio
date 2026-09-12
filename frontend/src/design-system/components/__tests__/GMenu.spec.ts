import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import GMenu, { type GMenuItem } from '../GMenu.vue'
import IconTrash from '../../icons/IconTrash.vue'

const ITEMS: GMenuItem[] = [
  { key: 'rename', label: 'Rename' },
  { key: 'export', label: 'Export', disabled: true, disabledReason: 'Disponible próximamente' },
  { key: 'delete', label: 'Delete', danger: true },
]

// Ticket 071: el cierre anima la salida antes de desmontar (mismo patrón que GDrawer.vue) -- ver esa suite para el precedente de vi.useFakeTimers()/advanceTimersByTime().
beforeEach(() => {
  vi.useFakeTimers()
})

describe('GMenu.vue', () => {
  it('el menú está cerrado por defecto', () => {
    const wrapper = mount(GMenu, { props: { items: ITEMS, label: 'Acciones' } })

    expect(wrapper.find('[role="menu"]').exists()).toBe(false)
  })

  it('el trigger abre y cierra el menú', async () => {
    const wrapper = mount(GMenu, { props: { items: ITEMS, label: 'Acciones' } })

    await wrapper.find('.g-menu__trigger').trigger('click')
    expect(wrapper.find('[role="menu"]').exists()).toBe(true)

    // deja correr el requestAnimationFrame de la transición de entrada antes del segundo click (toggle decide según isOpen).
    vi.advanceTimersByTime(20)
    await wrapper.vm.$nextTick()

    await wrapper.find('.g-menu__trigger').trigger('click')
    vi.advanceTimersByTime(140)
    await wrapper.vm.$nextTick()
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

    vi.advanceTimersByTime(140)
    await wrapper.vm.$nextTick()
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

  // Ticket 071 -- hallazgo real reportado en vivo: el menú se quedaba abierto para siempre al clickear afuera.
  it('clickear fuera del componente cierra el menú', async () => {
    const wrapper = mount(GMenu, { props: { items: ITEMS, label: 'Acciones' }, attachTo: document.body })
    await wrapper.get('.g-menu__trigger').trigger('click')
    expect(wrapper.find('[role="menu"]').exists()).toBe(true)

    document.body.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    await wrapper.vm.$nextTick()
    vi.advanceTimersByTime(140)
    await wrapper.vm.$nextTick()

    expect(wrapper.find('[role="menu"]').exists()).toBe(false)
    wrapper.unmount()
  })

  it('Escape cierra el menú y devuelve el foco al trigger', async () => {
    const wrapper = mount(GMenu, { props: { items: ITEMS, label: 'Acciones' }, attachTo: document.body })
    await wrapper.get('.g-menu__trigger').trigger('click')
    expect(wrapper.find('[role="menu"]').exists()).toBe(true)

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
    await wrapper.vm.$nextTick()
    vi.advanceTimersByTime(140)
    await wrapper.vm.$nextTick()

    expect(wrapper.find('[role="menu"]').exists()).toBe(false)
    expect(document.activeElement).toBe(wrapper.get('.g-menu__trigger').element)
    wrapper.unmount()
  })

  // Post-076 -- ítem con `icon` opcional (pedido explícito del PO, menú "⋮" del editor de mob).
  it('un ítem con icon lo renderiza junto a la etiqueta; uno sin icon no rompe nada', async () => {
    const itemsWithIcon: GMenuItem[] = [
      { key: 'delete', label: 'Eliminar', icon: IconTrash, danger: true },
      { key: 'rename', label: 'Renombrar' },
    ]
    const wrapper = mount(GMenu, { props: { items: itemsWithIcon, label: 'Acciones' } })
    await wrapper.find('.g-menu__trigger').trigger('click')

    const deleteItem = wrapper.findAll('[role="menuitem"]').find((i) => i.text().includes('Eliminar'))!
    const renameItem = wrapper.findAll('[role="menuitem"]').find((i) => i.text().includes('Renombrar'))!

    expect(deleteItem.find('svg').exists()).toBe(true)
    expect(renameItem.find('svg').exists()).toBe(false)
  })

  // Ticket 071 -- hallazgo real reportado en vivo: sin detección de espacio, la lista podía desbordar el viewport hacia abajo en una card cerca del borde inferior.
  it('sin espacio suficiente debajo del trigger, se muestra hacia arriba', async () => {
    const wrapper = mount(GMenu, { props: { items: ITEMS, label: 'Acciones' }, attachTo: document.body })
    vi.spyOn(window, 'innerHeight', 'get').mockReturnValue(768)
    // El trigger está pegado al borde inferior del viewport (poco espacio abajo); la lista es más alta que ese espacio.
    vi.spyOn(Element.prototype, 'getBoundingClientRect').mockImplementation(function (this: Element) {
      const rect = { top: 0, bottom: 0, left: 0, right: 0, width: 0, height: 0, x: 0, y: 0, toJSON: () => ({}) }
      if (this.classList.contains('g-menu__trigger')) {
        return { ...rect, top: 700, bottom: 720, height: 20 }
      }
      if (this.classList.contains('g-menu__list')) {
        return { ...rect, height: 200, width: 160 }
      }
      return rect
    })

    await wrapper.get('.g-menu__trigger').trigger('click')

    expect(wrapper.get('[role="menu"]').classes()).toContain('g-menu__list--above')
    wrapper.unmount()
  })
})
