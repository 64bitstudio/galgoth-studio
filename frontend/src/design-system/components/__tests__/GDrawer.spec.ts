import { mount } from '@vue/test-utils'
import { beforeAll, beforeEach, describe, expect, it, vi } from 'vitest'
import GDrawer from '../GDrawer.vue'

// jsdom no implementa HTMLDialogElement.showModal()/close() -- mismo polyfill que AppDialog.spec.ts.
beforeAll(() => {
  if (!HTMLDialogElement.prototype.showModal) {
    HTMLDialogElement.prototype.showModal = function (this: HTMLDialogElement) {
      this.setAttribute('open', '')
    }
  }
  if (!HTMLDialogElement.prototype.close) {
    HTMLDialogElement.prototype.close = function (this: HTMLDialogElement) {
      this.removeAttribute('open')
    }
  }
})

beforeEach(() => {
  vi.useFakeTimers()
})

describe('GDrawer.vue (ticket 069)', () => {
  it('muestra el título y el contenido de los slots (body y footer)', () => {
    const wrapper = mount(GDrawer, {
      props: { title: 'Asistente IA' },
      slots: { default: '<p>cuerpo real</p>', footer: '<button>Aplicar</button>' },
    })

    expect(wrapper.find('.g-drawer__title').text()).toBe('Asistente IA')
    expect(wrapper.text()).toContain('cuerpo real')
    expect(wrapper.find('.g-drawer__footer').text()).toContain('Aplicar')
  })

  it('no renderiza el footer si no se le pasa el slot', () => {
    const wrapper = mount(GDrawer, { props: { title: 'X' } })

    expect(wrapper.find('.g-drawer__footer').exists()).toBe(false)
  })

  it('se abre solo (showModal) al montar', () => {
    const wrapper = mount(GDrawer, { props: { title: 'X' } })

    expect(wrapper.find('dialog').attributes('open')).toBeDefined()
  })

  it('clic en el propio <dialog> (el ::backdrop nativo aterriza ahí) emite cancel tras la animación de salida', async () => {
    const wrapper = mount(GDrawer, { props: { title: 'X' } })

    await wrapper.find('.g-drawer').trigger('click')
    expect(wrapper.emitted('cancel')).toBeUndefined() // todavía animando la salida

    vi.advanceTimersByTime(240)
    expect(wrapper.emitted('cancel')).toHaveLength(1)
  })

  it('clic dentro del contenido NO emite cancel', async () => {
    const wrapper = mount(GDrawer, { props: { title: 'X' }, slots: { default: '<p>contenido</p>' } })

    await wrapper.find('.g-drawer__body').trigger('click')
    vi.advanceTimersByTime(500)

    expect(wrapper.emitted('cancel')).toBeUndefined()
  })

  it('Escape (evento cancel nativo del <dialog>) emite cancel tras la animación de salida', async () => {
    const wrapper = mount(GDrawer, { props: { title: 'X' } })

    await wrapper.find('dialog').trigger('cancel')
    vi.advanceTimersByTime(240)

    expect(wrapper.emitted('cancel')).toHaveLength(1)
  })

  it('el botón de cerrar (×) emite cancel tras la animación de salida', async () => {
    const wrapper = mount(GDrawer, { props: { title: 'X' } })

    await wrapper.find('button[aria-label="Cerrar"]').trigger('click')
    vi.advanceTimersByTime(240)

    expect(wrapper.emitted('cancel')).toHaveLength(1)
  })

  it('un segundo intento de cierre mientras ya está animando no dispara un segundo cancel', async () => {
    const wrapper = mount(GDrawer, { props: { title: 'X' } })

    await wrapper.find('button[aria-label="Cerrar"]').trigger('click')
    await wrapper.find('dialog').trigger('cancel') // Escape mientras ya cierra -- no debe reiniciar el timer ni duplicar el emit
    vi.advanceTimersByTime(240)

    expect(wrapper.emitted('cancel')).toHaveLength(1)
  })
})
