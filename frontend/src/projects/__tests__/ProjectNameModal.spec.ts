import { mount } from '@vue/test-utils'
import { beforeAll, describe, expect, it } from 'vitest'
import ProjectNameModal from '../ProjectNameModal.vue'

// jsdom (a la fecha, v30) no implementa HTMLDialogElement.showModal()/close()
// -- son parte del layout real que jsdom no simula. Se parchea el mínimo
// necesario (togglear el atributo `open`) para poder montar el componente
// en tests; el navegador real siempre tiene estos métodos.
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

describe('ProjectNameModal.vue', () => {
  it('modo create: título "Nuevo proyecto" y campo vacío por defecto', () => {
    const wrapper = mount(ProjectNameModal, { props: { mode: 'create' } })

    expect(wrapper.text()).toContain('Nuevo proyecto')
    expect((wrapper.find('input').element as HTMLInputElement).value).toBe('')
  })

  it('modo rename: título "Editar proyecto" y campo pre-llenado con initialName', () => {
    const wrapper = mount(ProjectNameModal, { props: { mode: 'rename', initialName: 'Galgoth' } })

    expect(wrapper.text()).toContain('Editar proyecto')
    expect((wrapper.find('input').element as HTMLInputElement).value).toBe('Galgoth')
  })

  // Ticket 073 -- el campo de descripción solo existe en modo "rename" (create no lo pide, AC #3 de HU-01).
  it('modo create: no muestra el campo de descripción', () => {
    const wrapper = mount(ProjectNameModal, { props: { mode: 'create' } })

    expect(wrapper.find('textarea').exists()).toBe(false)
  })

  it('modo rename: muestra el campo de descripción pre-llenado con initialDescription', () => {
    const wrapper = mount(ProjectNameModal, { props: { mode: 'rename', initialName: 'Galgoth', initialDescription: 'Un bosque maldito.' } })

    expect((wrapper.find('textarea').element as HTMLTextAreaElement).value).toBe('Un bosque maldito.')
  })

  it('confirmar con un nombre válido emite confirm con el nombre recortado (trim) y descripción null (modo create)', async () => {
    const wrapper = mount(ProjectNameModal, { props: { mode: 'create' } })

    await wrapper.find('input').setValue('  Carcomido  ')
    await wrapper.findAll('button').find((b) => b.text() === 'Crear proyecto')!.trigger('click')

    expect(wrapper.emitted('confirm')).toEqual([['Carcomido', null]])
  })

  it('modo rename: confirmar emite confirm con nombre y descripción recortados (trim)', async () => {
    const wrapper = mount(ProjectNameModal, { props: { mode: 'rename', initialName: 'Galgoth' } })

    await wrapper.find('input').setValue('Galgoth')
    await wrapper.find('textarea').setValue('  Un bosque maldito.  ')
    await wrapper.findAll('button').find((b) => b.text() === 'Guardar')!.trigger('click')

    expect(wrapper.emitted('confirm')).toEqual([['Galgoth', 'Un bosque maldito.']])
  })

  it('modo rename: descripción vacía emite null, no una cadena vacía', async () => {
    const wrapper = mount(ProjectNameModal, { props: { mode: 'rename', initialName: 'Galgoth' } })

    await wrapper.findAll('button').find((b) => b.text() === 'Guardar')!.trigger('click')

    expect(wrapper.emitted('confirm')).toEqual([['Galgoth', null]])
  })

  it('confirmar con el campo vacío muestra un error y NO emite confirm', async () => {
    const wrapper = mount(ProjectNameModal, { props: { mode: 'create' } })

    await wrapper.findAll('button').find((b) => b.text() === 'Crear proyecto')!.trigger('click')

    expect(wrapper.text()).toContain('El nombre no puede estar vacío.')
    expect(wrapper.emitted('confirm')).toBeUndefined()
  })

  it('Cancelar emite cancel', async () => {
    const wrapper = mount(ProjectNameModal, { props: { mode: 'create' } })

    await wrapper.findAll('button').find((b) => b.text() === 'Cancelar')!.trigger('click')

    expect(wrapper.emitted('cancel')).toHaveLength(1)
  })

  it('Enter en el input confirma igual que el botón', async () => {
    const wrapper = mount(ProjectNameModal, { props: { mode: 'create' } })

    await wrapper.find('input').setValue('Tejedora')
    await wrapper.find('input').trigger('keyup.enter')

    expect(wrapper.emitted('confirm')).toEqual([['Tejedora', null]])
  })

  it('clic en el propio <dialog> (el ::backdrop nativo aterriza ahí) emite cancel', async () => {
    const wrapper = mount(ProjectNameModal, { props: { mode: 'create' } })

    await wrapper.find('.app-dialog').trigger('click')

    expect(wrapper.emitted('cancel')).toHaveLength(1)
  })

  it('clic dentro del contenido del modal (ej. el título) NO emite cancel', async () => {
    const wrapper = mount(ProjectNameModal, { props: { mode: 'create' } })

    await wrapper.find('.app-dialog__title').trigger('click')

    expect(wrapper.emitted('cancel')).toBeUndefined()
  })
})
