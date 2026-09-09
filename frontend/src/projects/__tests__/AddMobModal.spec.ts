import { mount } from '@vue/test-utils'
import { beforeAll, describe, expect, it } from 'vitest'
import AddMobModal from '../AddMobModal.vue'

// jsdom no implementa HTMLDialogElement.showModal()/close() -- ver la
// misma nota en ProjectNameModal.spec.ts (ticket 021).
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

describe('AddMobModal.vue', () => {
  it('humanoide está seleccionado por defecto', () => {
    const wrapper = mount(AddMobModal)

    const humanoid = wrapper.findAll('.add-mob-modal__base-type').find((b) => b.text() === 'Humanoide')!
    expect(humanoid.attributes('aria-pressed')).toBe('true')
  })

  it('clic en otro tipo lo selecciona y deselecciona el anterior', async () => {
    const wrapper = mount(AddMobModal)

    const flying = wrapper.findAll('.add-mob-modal__base-type').find((b) => b.text() === 'Volador')!
    await flying.trigger('click')

    expect(flying.attributes('aria-pressed')).toBe('true')
    const humanoid = wrapper.findAll('.add-mob-modal__base-type').find((b) => b.text() === 'Humanoide')!
    expect(humanoid.attributes('aria-pressed')).toBe('false')
  })

  it('confirmar con nombre y tipo válidos emite confirm(name, baseType)', async () => {
    const wrapper = mount(AddMobModal)

    await wrapper.find('input').setValue('Tejedora')
    await wrapper.findAll('.add-mob-modal__base-type').find((b) => b.text() === 'Arácnido')!.trigger('click')
    await wrapper.findAll('button').find((b) => b.text() === 'Agregar mob')!.trigger('click')

    expect(wrapper.emitted('confirm')).toEqual([['Tejedora', 'arachnid']])
  })

  it('confirmar con el nombre vacío muestra un error y no emite confirm', async () => {
    const wrapper = mount(AddMobModal)

    await wrapper.findAll('button').find((b) => b.text() === 'Agregar mob')!.trigger('click')

    expect(wrapper.text()).toContain('El nombre no puede estar vacío.')
    expect(wrapper.emitted('confirm')).toBeUndefined()
  })

  it('Cancelar emite cancel', async () => {
    const wrapper = mount(AddMobModal)

    await wrapper.findAll('button').find((b) => b.text() === 'Cancelar')!.trigger('click')

    expect(wrapper.emitted('cancel')).toHaveLength(1)
  })

  it('clic en el propio <dialog> (backdrop nativo) emite cancel', async () => {
    const wrapper = mount(AddMobModal)

    await wrapper.find('.add-mob-modal').trigger('click')

    expect(wrapper.emitted('cancel')).toHaveLength(1)
  })
})
