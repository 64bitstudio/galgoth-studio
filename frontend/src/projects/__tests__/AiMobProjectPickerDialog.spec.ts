import { mount } from '@vue/test-utils'
import { beforeAll, describe, expect, it } from 'vitest'
import AiMobProjectPickerDialog from '../AiMobProjectPickerDialog.vue'
import type { ProjectSummary } from '../projectsApi'

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

function project(overrides: Partial<ProjectSummary> = {}): ProjectSummary {
  return {id: 'p1', name: 'Galgoth', mobCount: 0, mobThumbnails: [], createdAt: '', updatedAt: '', ...overrides}
}

describe('AiMobProjectPickerDialog.vue', () => {
  it('sin proyectos, arranca directo en modo "nuevo proyecto" (sin selector vacío)', () => {
    const wrapper = mount(AiMobProjectPickerDialog, {props: {projects: []}})

    expect(wrapper.find('select').exists()).toBe(false)
    expect(wrapper.find('input').exists()).toBe(true)
  })

  it('sin proyectos, confirmar con un nombre real emite confirm con newProjectName', async () => {
    const wrapper = mount(AiMobProjectPickerDialog, {props: {projects: []}})

    await wrapper.find('input').setValue('Carcomido')
    await wrapper.findAll('button').find((b) => b.text() === 'Continuar')!.trigger('click')

    expect(wrapper.emitted('confirm')).toEqual([[{newProjectName: 'Carcomido'}]])
  })

  it('sin proyectos, un nombre vacío muestra un error de validación y NO emite confirm', async () => {
    const wrapper = mount(AiMobProjectPickerDialog, {props: {projects: []}})

    await wrapper.findAll('button').find((b) => b.text() === 'Continuar')!.trigger('click')

    expect(wrapper.text()).toContain('El nombre no puede estar vacío.')
    expect(wrapper.emitted('confirm')).toBeUndefined()
  })

  it('con proyectos existentes, el selector arranca en el primero y confirmar emite confirm con projectId', async () => {
    const wrapper = mount(AiMobProjectPickerDialog, {props: {projects: [project({id: 'p1'}), project({id: 'p2', name: 'Otro'})]}})

    expect(wrapper.find('select').exists()).toBe(true)
    await wrapper.findAll('button').find((b) => b.text() === 'Continuar')!.trigger('click')

    expect(wrapper.emitted('confirm')).toEqual([[{projectId: 'p1'}]])
  })

  it('con proyectos existentes, elegir "+ Nuevo proyecto" muestra el campo de nombre', async () => {
    const wrapper = mount(AiMobProjectPickerDialog, {props: {projects: [project()]}})

    await wrapper.find('select').setValue('__new__')

    expect(wrapper.find('input').exists()).toBe(true)
  })

  it('busy=true deshabilita el botón de continuar', () => {
    const wrapper = mount(AiMobProjectPickerDialog, {props: {projects: [], busy: true}})

    expect(wrapper.findAll('button').find((b) => b.text() === 'Creando…')!.attributes('disabled')).toBeDefined()
  })

  it('un error real se muestra sin cerrar el diálogo', () => {
    const wrapper = mount(AiMobProjectPickerDialog, {props: {projects: [], error: 'No se pudo crear el proyecto.'}})

    expect(wrapper.text()).toContain('No se pudo crear el proyecto.')
  })

  it('cancelar emite cancel', async () => {
    const wrapper = mount(AiMobProjectPickerDialog, {props: {projects: []}})

    await wrapper.findAll('button').find((b) => b.text() === 'Cancelar')!.trigger('click')

    expect(wrapper.emitted('cancel')).toHaveLength(1)
  })
})
