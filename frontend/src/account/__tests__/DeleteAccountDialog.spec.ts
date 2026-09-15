import { mount } from '@vue/test-utils'
import { beforeAll, describe, expect, it } from 'vitest'
import DeleteAccountDialog from '../DeleteAccountDialog.vue'

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

describe('DeleteAccountDialog.vue', () => {
  it('el botón de confirmar arranca deshabilitado hasta escribir el identifier exacto', async () => {
    const wrapper = mount(DeleteAccountDialog, { props: { identifier: 'ada@example.com' } })

    const confirmButton = wrapper.findAll('button').find((b) => b.text().includes('Eliminar mi cuenta'))!
    expect(confirmButton.attributes('disabled')).toBeDefined()

    await wrapper.find('input').setValue('algo-distinto')
    expect(confirmButton.attributes('disabled')).toBeDefined()

    await wrapper.find('input').setValue('ada@example.com')
    expect(confirmButton.attributes('disabled')).toBeUndefined()
  })

  it('escribir el identifier exacto y confirmar emite confirm', async () => {
    const wrapper = mount(DeleteAccountDialog, { props: { identifier: 'ada@example.com' } })

    await wrapper.find('input').setValue('ada@example.com')
    const confirmButton = wrapper.findAll('button').find((b) => b.text().includes('Eliminar mi cuenta'))!
    await confirmButton.trigger('click')

    expect(wrapper.emitted('confirm')).toBeTruthy()
  })

  it('cancelar emite cancel', async () => {
    const wrapper = mount(DeleteAccountDialog, { props: { identifier: 'ada@example.com' } })

    const cancelButton = wrapper.findAll('button').find((b) => b.text() === 'Cancelar')!
    await cancelButton.trigger('click')

    expect(wrapper.emitted('cancel')).toBeTruthy()
  })

  it('con error real del backend, lo muestra', () => {
    const wrapper = mount(DeleteAccountDialog, { props: { identifier: 'ada@example.com', error: 'No se pudo purgar los proyectos.' } })

    expect(wrapper.text()).toContain('No se pudo purgar los proyectos.')
  })
})
