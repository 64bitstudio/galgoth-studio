import { mount } from '@vue/test-utils'
import { beforeAll, describe, expect, it } from 'vitest'
import AppDialog from '../AppDialog.vue'

// jsdom no implementa HTMLDialogElement.showModal()/close() -- ver la misma nota en ProjectNameModal.spec.ts.
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

describe('AppDialog.vue', () => {
  it('muestra el título y el contenido de los slots', () => {
    const wrapper = mount(AppDialog, {
      props: {title: 'Renombrar proyecto'},
      slots: {default: '<p>cuerpo real</p>', actions: '<button>Guardar</button>'},
    })

    expect(wrapper.find('.app-dialog__title').text()).toBe('Renombrar proyecto')
    expect(wrapper.text()).toContain('cuerpo real')
    expect(wrapper.find('.app-dialog__actions').text()).toContain('Guardar')
  })

  it('se abre solo (showModal) al montar', () => {
    const wrapper = mount(AppDialog, {props: {title: 'X'}})

    expect(wrapper.find('dialog').attributes('open')).toBeDefined()
  })

  it('clic en el propio <dialog> (el ::backdrop nativo aterriza ahí) emite cancel', async () => {
    const wrapper = mount(AppDialog, {props: {title: 'X'}})

    await wrapper.find('.app-dialog').trigger('click')

    expect(wrapper.emitted('cancel')).toHaveLength(1)
  })

  it('clic dentro del contenido NO emite cancel', async () => {
    const wrapper = mount(AppDialog, {props: {title: 'X'}, slots: {default: '<p>contenido</p>'}})

    await wrapper.find('.app-dialog__body').trigger('click')

    expect(wrapper.emitted('cancel')).toBeUndefined()
  })

  it('Escape (evento cancel nativo del <dialog>) emite cancel', async () => {
    const wrapper = mount(AppDialog, {props: {title: 'X'}})

    await wrapper.find('dialog').trigger('cancel')

    expect(wrapper.emitted('cancel')).toHaveLength(1)
  })
})
