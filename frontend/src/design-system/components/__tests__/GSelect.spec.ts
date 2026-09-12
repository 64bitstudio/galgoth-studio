import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import GSelect from '../GSelect.vue'

const OPTIONS = [
  { value: 'a', label: 'Opción A' },
  { value: 'b', label: 'Opción B' },
  { value: 'c', label: 'Opción C' },
]

describe('GSelect.vue', () => {
  it('muestra la etiqueta de la opción activa en el trigger, nunca el value crudo', () => {
    const wrapper = mount(GSelect, { props: { modelValue: 'b', options: OPTIONS, label: 'Elegir letra' } })

    expect(wrapper.get('.g-select__trigger').text()).toBe('Opción B')
  })

  it('nunca renderiza un <select> nativo', () => {
    const wrapper = mount(GSelect, { props: { modelValue: 'a', options: OPTIONS, label: 'Elegir letra' } })

    expect(wrapper.find('select').exists()).toBe(false)
  })

  it('el trigger es accesible: aria-label, aria-haspopup=listbox, aria-expanded refleja el estado abierto/cerrado', async () => {
    const wrapper = mount(GSelect, { props: { modelValue: 'a', options: OPTIONS, label: 'Elegir letra' } })
    const trigger = wrapper.get('.g-select__trigger')

    expect(trigger.attributes('aria-label')).toBe('Elegir letra')
    expect(trigger.attributes('aria-haspopup')).toBe('listbox')
    expect(trigger.attributes('aria-expanded')).toBe('false')

    await trigger.trigger('click')

    expect(trigger.attributes('aria-expanded')).toBe('true')
    expect(wrapper.get('[role="listbox"]')).toBeTruthy()
  })

  it('clickear una opción emite update:modelValue con su value y cierra la lista', async () => {
    const wrapper = mount(GSelect, { props: { modelValue: 'a', options: OPTIONS, label: 'Elegir letra' } })
    await wrapper.get('.g-select__trigger').trigger('click')

    const optionB = wrapper.findAll('.g-select__option').find((o) => o.text().includes('Opción B'))!
    await optionB.trigger('click')

    expect(wrapper.emitted('update:modelValue')?.[0]).toEqual(['b'])
    expect(wrapper.find('[role="listbox"]').exists()).toBe(false)
  })

  it('marca la opción activa con aria-selected=true y un check visible -- las demás en false', async () => {
    const wrapper = mount(GSelect, { props: { modelValue: 'b', options: OPTIONS, label: 'Elegir letra' } })
    await wrapper.get('.g-select__trigger').trigger('click')

    const options = wrapper.findAll('[role="option"]')
    expect(options[0]!.attributes('aria-selected')).toBe('false')
    expect(options[1]!.attributes('aria-selected')).toBe('true')
    expect(options[1]!.find('.g-select__check').exists()).toBe(true)
  })

  it('ArrowDown en el trigger abre la lista; Escape en la lista la cierra y devuelve el foco al trigger', async () => {
    const wrapper = mount(GSelect, { props: { modelValue: 'a', options: OPTIONS, label: 'Elegir letra' }, attachTo: document.body })
    await wrapper.get('.g-select__trigger').trigger('keydown', { key: 'ArrowDown' })

    expect(wrapper.find('[role="listbox"]').exists()).toBe(true)

    await wrapper.get('[role="listbox"]').trigger('keydown', { key: 'Escape' })

    expect(wrapper.find('[role="listbox"]').exists()).toBe(false)
    wrapper.unmount()
  })

  it('Enter dentro de la lista elige la opción actualmente enfocada', async () => {
    const wrapper = mount(GSelect, { props: { modelValue: 'a', options: OPTIONS, label: 'Elegir letra' }, attachTo: document.body })
    await wrapper.get('.g-select__trigger').trigger('click')

    await wrapper.get('[role="listbox"]').trigger('keydown', { key: 'ArrowDown' })
    await wrapper.get('[role="listbox"]').trigger('keydown', { key: 'Enter' })

    expect(wrapper.emitted('update:modelValue')?.[0]).toEqual(['b'])
    wrapper.unmount()
  })

  it('clickear fuera del componente cierra la lista', async () => {
    const wrapper = mount(GSelect, { props: { modelValue: 'a', options: OPTIONS, label: 'Elegir letra' }, attachTo: document.body })
    await wrapper.get('.g-select__trigger').trigger('click')
    expect(wrapper.find('[role="listbox"]').exists()).toBe(true)

    document.body.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    await wrapper.vm.$nextTick()

    expect(wrapper.find('[role="listbox"]').exists()).toBe(false)
    wrapper.unmount()
  })

  it('sin ninguna opción activa (placeholder), muestra el placeholder en el trigger', () => {
    const wrapper = mount(GSelect, { props: { modelValue: '__none__', options: OPTIONS, label: 'Elegir letra', placeholder: 'Elegir…' } })

    expect(wrapper.get('.g-select__trigger').text()).toBe('Elegir…')
  })

  // Ticket 071 -- hallazgo real reportado en vivo: dentro de un contenedor con `overflow` (ej. `.app-dialog__body`), la lista se recortaba/generaba scroll interno en vez de desbordar por encima. `position: fixed` (CSS) + coordenadas medidas del trigger (inline, en vez de `absolute` con `top`/`left` fijos en CSS) la saca de ese recorte.
  it('la lista posiciona top/left/width inline según el trigger real, no con CSS estático', async () => {
    const wrapper = mount(GSelect, { props: { modelValue: 'a', options: OPTIONS, label: 'Elegir letra' }, attachTo: document.body })
    await wrapper.get('.g-select__trigger').trigger('click')

    const style = wrapper.get('[role="listbox"]').attributes('style') ?? ''
    expect(style).toContain('top:')
    expect(style).toContain('left:')
    expect(style).toContain('width:')
    wrapper.unmount()
  })

  it('sin espacio suficiente debajo del trigger, se abre hacia arriba', async () => {
    const wrapper = mount(GSelect, { props: { modelValue: 'a', options: OPTIONS, label: 'Elegir letra' }, attachTo: document.body })
    vi.spyOn(window, 'innerHeight', 'get').mockReturnValue(768)
    vi.spyOn(Element.prototype, 'getBoundingClientRect').mockImplementation(function (this: Element) {
      const rect = { top: 0, bottom: 0, left: 0, right: 0, width: 0, height: 0, x: 0, y: 0, toJSON: () => ({}) }
      if (this.classList.contains('g-select__trigger')) {
        return { ...rect, top: 700, bottom: 720, height: 20, width: 160 }
      }
      if (this.classList.contains('g-select__list')) {
        return { ...rect, height: 200 }
      }
      return rect
    })

    await wrapper.get('.g-select__trigger').trigger('click')

    // Con el trigger en y=700-720 y viewport de 768, arriba (top=700) hay más espacio que abajo (768-720=48) para una lista de 200 -- debe abrirse hacia arriba, con su borde inferior justo encima del trigger.
    const top = Number((wrapper.get('[role="listbox"]').attributes('style') ?? '').match(/top:\s*(-?\d+)/)?.[1])
    expect(top).toBeLessThan(700)
    wrapper.unmount()
  })
})
