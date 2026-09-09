import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import ProjectNameModal from '../ProjectNameModal.vue'

describe('ProjectNameModal.vue', () => {
  it('modo create: título "Nuevo proyecto" y campo vacío por defecto', () => {
    const wrapper = mount(ProjectNameModal, { props: { mode: 'create' } })

    expect(wrapper.text()).toContain('Nuevo proyecto')
    expect((wrapper.find('input').element as HTMLInputElement).value).toBe('')
  })

  it('modo rename: título "Renombrar proyecto" y campo pre-llenado con initialName', () => {
    const wrapper = mount(ProjectNameModal, { props: { mode: 'rename', initialName: 'Galgoth' } })

    expect(wrapper.text()).toContain('Renombrar proyecto')
    expect((wrapper.find('input').element as HTMLInputElement).value).toBe('Galgoth')
  })

  it('confirmar con un nombre válido emite confirm con el nombre recortado (trim)', async () => {
    const wrapper = mount(ProjectNameModal, { props: { mode: 'create' } })

    await wrapper.find('input').setValue('  Carcomido  ')
    await wrapper.findAll('button').find((b) => b.text() === 'Crear proyecto')!.trigger('click')

    expect(wrapper.emitted('confirm')).toEqual([['Carcomido']])
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

    expect(wrapper.emitted('confirm')).toEqual([['Tejedora']])
  })

  it('clic en el backdrop emite cancel', async () => {
    const wrapper = mount(ProjectNameModal, { props: { mode: 'create' } })

    await wrapper.find('.project-name-modal__backdrop').trigger('click')

    expect(wrapper.emitted('cancel')).toHaveLength(1)
  })
})
