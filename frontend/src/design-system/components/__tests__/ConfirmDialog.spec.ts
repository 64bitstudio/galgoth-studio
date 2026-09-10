import { mount } from '@vue/test-utils'
import { beforeAll, describe, expect, it } from 'vitest'
import ConfirmDialog from '../ConfirmDialog.vue'

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

/**
 * Ticket 039 -- Eliminar proyecto/mob comparten este componente. Cubre
 * el bloqueo de doble submit real (`busy` deshabilita ambos botones, no
 * solo el de confirmar) y que un error real del backend se muestra sin
 * cerrar el diálogo.
 */
describe('ConfirmDialog.vue', () => {
  it('muestra título, mensaje y el label de confirmación', () => {
    const wrapper = mount(ConfirmDialog, {
      props: {title: 'Eliminar proyecto', message: '¿Eliminar "Galgoth"?', confirmLabel: 'Eliminar'},
    })

    expect(wrapper.find('.app-dialog__title').text()).toBe('Eliminar proyecto')
    expect(wrapper.text()).toContain('¿Eliminar "Galgoth"?')
    expect(wrapper.findAll('button').find((b) => b.text() === 'Eliminar')).toBeDefined()
  })

  it('confirmar emite confirm; cancelar emite cancel', async () => {
    const wrapper = mount(ConfirmDialog, {props: {title: 'X', message: 'Y', confirmLabel: 'Eliminar'}})

    await wrapper.findAll('button').find((b) => b.text() === 'Eliminar')!.trigger('click')
    expect(wrapper.emitted('confirm')).toHaveLength(1)

    await wrapper.findAll('button').find((b) => b.text() === 'Cancelar')!.trigger('click')
    expect(wrapper.emitted('cancel')).toHaveLength(1)
  })

  it('busy=true deshabilita AMBOS botones -- bloquea doble submit real, no solo visual', () => {
    const wrapper = mount(ConfirmDialog, {props: {title: 'X', message: 'Y', confirmLabel: 'Eliminar', busy: true}})

    expect(wrapper.findAll('button').find((b) => b.text() === 'Procesando…')!.attributes('disabled')).toBeDefined()
    expect(wrapper.findAll('button').find((b) => b.text() === 'Cancelar')!.attributes('disabled')).toBeDefined()
  })

  it('un error real se muestra sin cerrar el diálogo', () => {
    const wrapper = mount(ConfirmDialog, {props: {title: 'X', message: 'Y', error: 'No se pudo eliminar: el proyecto tiene mobs en uso.'}})

    expect(wrapper.text()).toContain('No se pudo eliminar: el proyecto tiene mobs en uso.')
  })

  it('danger=true usa el variant danger para el botón de confirmar', () => {
    const wrapper = mount(ConfirmDialog, {props: {title: 'X', message: 'Y', confirmLabel: 'Eliminar', danger: true}})

    expect(wrapper.findAll('button').find((b) => b.text() === 'Eliminar')!.classes()).toContain('g-button--danger')
  })
})
