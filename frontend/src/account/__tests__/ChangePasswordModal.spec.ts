import { mount } from '@vue/test-utils'
import { beforeAll, describe, expect, it } from 'vitest'
import ChangePasswordModal from '../ChangePasswordModal.vue'

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

describe('ChangePasswordModal.vue', () => {
  it('con hasPassword=true pide la contraseña actual y emite ambas al confirmar', async () => {
    const wrapper = mount(ChangePasswordModal, { props: { hasPassword: true } })

    await wrapper.find('[aria-label="Contraseña actual"]').setValue('vieja1234')
    await wrapper.find('[aria-label="Nueva contraseña"]').setValue('nueva12345')
    await wrapper.findAll('button').find((b) => b.text() === 'Actualizar contraseña')!.trigger('click')

    expect(wrapper.emitted('confirm')).toEqual([[{ currentPassword: 'vieja1234', newPassword: 'nueva12345' }]])
  })

  it('con hasPassword=false no pide la contraseña actual y el título dice "Establecer contraseña"', () => {
    const wrapper = mount(ChangePasswordModal, { props: { hasPassword: false } })

    expect(wrapper.text()).toContain('Establecer contraseña')
    expect(wrapper.find('[aria-label="Contraseña actual"]').exists()).toBe(false)
  })

  it('una nueva contraseña de menos de 8 caracteres no confirma y muestra el error de validación', async () => {
    const wrapper = mount(ChangePasswordModal, { props: { hasPassword: false } })

    await wrapper.find('[aria-label="Nueva contraseña"]').setValue('corta')
    await wrapper.findAll('button').find((b) => b.text() === 'Actualizar contraseña')!.trigger('click')

    expect(wrapper.emitted('confirm')).toBeUndefined()
    expect(wrapper.text()).toContain('al menos 8 caracteres')
  })

  it('"Cancelar" emite cancel', async () => {
    const wrapper = mount(ChangePasswordModal, { props: { hasPassword: false } })

    await wrapper.findAll('button').find((b) => b.text() === 'Cancelar')!.trigger('click')

    expect(wrapper.emitted('cancel')).toHaveLength(1)
  })

  it('muestra el error del backend cuando se pasa por props', () => {
    const wrapper = mount(ChangePasswordModal, { props: { hasPassword: true, error: 'La contraseña actual no es correcta.' } })

    expect(wrapper.text()).toContain('La contraseña actual no es correcta.')
  })
})
