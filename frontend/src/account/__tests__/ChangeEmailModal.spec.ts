import { mount } from '@vue/test-utils'
import { beforeAll, describe, expect, it } from 'vitest'
import ChangeEmailModal from '../ChangeEmailModal.vue'

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

describe('ChangeEmailModal.vue', () => {
  it('un correo vacío o sin @ muestra un error de validación y no emite confirm', async () => {
    const wrapper = mount(ChangeEmailModal)

    const confirmButton = wrapper.findAll('button').find((b) => b.text().includes('Enviar'))!
    await confirmButton.trigger('click')

    expect(wrapper.text()).toContain('Ingresa un correo válido.')
    expect(wrapper.emitted('confirm')).toBeUndefined()
  })

  it('un correo válido emite confirm con el valor', async () => {
    const wrapper = mount(ChangeEmailModal)

    await wrapper.find('input[type="email"]').setValue('nuevo@example.com')
    const confirmButton = wrapper.findAll('button').find((b) => b.text().includes('Enviar'))!
    await confirmButton.trigger('click')

    expect(wrapper.emitted('confirm')).toEqual([['nuevo@example.com']])
  })

  it('cancelar emite cancel', async () => {
    const wrapper = mount(ChangeEmailModal)

    const cancelButton = wrapper.findAll('button').find((b) => b.text() === 'Cancelar')!
    await cancelButton.trigger('click')

    expect(wrapper.emitted('cancel')).toBeTruthy()
  })

  it('con error real del backend, lo muestra', () => {
    const wrapper = mount(ChangeEmailModal, { props: { error: 'Ese correo ya está en uso.' } })

    expect(wrapper.text()).toContain('Ese correo ya está en uso.')
  })
})
