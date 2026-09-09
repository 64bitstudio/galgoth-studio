import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../../api/ApiError'

const requestEditPlan = vi.fn()
const applyEdit = vi.fn()
vi.mock('../aiEditApi', () => ({
  requestEditPlan: (...args: unknown[]) => requestEditPlan(...args),
  applyEdit: (...args: unknown[]) => applyEdit(...args),
}))

const { default: AiEditPanel } = await import('../AiEditPanel.vue')

const emptyModel: Record<string, unknown> = { mobId: 'mob-1', bones: [], cuboids: [] }

function samplePlan(overrides: Partial<Record<string, unknown>> = {}) {
  return {
    jobId: 'job-1',
    summary: 'Manos más grandes y hombros asimétricos',
    beforeCuboidCount: 14,
    beforeBoneCount: 7,
    afterCuboidCount: 14,
    afterBoneCount: 7,
    changedElements: [
      { type: 'cuboid', id: 'hand_right', name: 'hand_right', changeKind: 'modified' },
      { type: 'cuboid', id: 'hand_left', name: 'hand_left', changeKind: 'modified' },
    ],
    beforeModel: { ...emptyModel, tag: 'before' },
    afterModel: { ...emptyModel, tag: 'after' },
    ...overrides,
  }
}

describe('AiEditPanel.vue', () => {
  beforeEach(() => {
    requestEditPlan.mockReset()
    applyEdit.mockReset()
  })

  afterEach(() => {
    vi.clearAllMocks()
  })

  it('arranca con la instrucción vacía y "Generar cambios" deshabilitado', () => {
    const wrapper = mount(AiEditPanel, { props: { mobId: 'mob-1' } })

    const button = wrapper.findAll('button').find((b) => b.text() === 'Generar cambios')!
    expect(button.attributes('disabled')).toBeDefined()
  })

  it('genera un plan y muestra resumen + elementos cambiados, emite preview-model-changed con el modelo Después por default', async () => {
    requestEditPlan.mockResolvedValue(samplePlan())
    const wrapper = mount(AiEditPanel, { props: { mobId: 'mob-1' } })

    await wrapper.find('textarea').setValue('Haz las manos más grandes')
    await wrapper.findAll('button').find((b) => b.text() === 'Generar cambios')!.trigger('click')
    await flushPromises()

    expect(requestEditPlan).toHaveBeenCalledWith('mob-1', 'Haz las manos más grandes')
    expect(wrapper.text()).toContain('Manos más grandes y hombros asimétricos')
    expect(wrapper.text()).toContain('hand_right (modificado)')
    expect(wrapper.text()).toContain('2 elementos modificados')

    const events = wrapper.emitted('preview-model-changed')!
    expect(events.at(-1)![0]).toMatchObject({ tag: 'after' })
  })

  it('el toggle Antes/Después cambia el modelo emitido sin volver a llamar a la IA', async () => {
    requestEditPlan.mockResolvedValue(samplePlan())
    const wrapper = mount(AiEditPanel, { props: { mobId: 'mob-1' } })
    await wrapper.find('textarea').setValue('instrucción')
    await wrapper.findAll('button').find((b) => b.text() === 'Generar cambios')!.trigger('click')
    await flushPromises()

    await wrapper.findAll('button').find((b) => b.text() === 'Antes')!.trigger('click')

    expect(requestEditPlan).toHaveBeenCalledTimes(1)
    const events = wrapper.emitted('preview-model-changed')!
    expect(events.at(-1)![0]).toMatchObject({ tag: 'before' })
  })

  it('Cancelar limpia el plan, emite preview-model-changed(null) y vuelve al textarea', async () => {
    requestEditPlan.mockResolvedValue(samplePlan())
    const wrapper = mount(AiEditPanel, { props: { mobId: 'mob-1' } })
    await wrapper.find('textarea').setValue('instrucción')
    await wrapper.findAll('button').find((b) => b.text() === 'Generar cambios')!.trigger('click')
    await flushPromises()

    await wrapper.findAll('button').find((b) => b.text() === 'Cancelar')!.trigger('click')

    expect(wrapper.find('textarea').exists()).toBe(true)
    const events = wrapper.emitted('preview-model-changed')!
    expect(events.at(-1)![0]).toBeNull()
  })

  it('Aplicar cambios exitoso emite applied con el modelo Después y preview-model-changed(null)', async () => {
    requestEditPlan.mockResolvedValue(samplePlan())
    applyEdit.mockResolvedValue({ revisionNumber: 2, draftVersion: 2 })
    const wrapper = mount(AiEditPanel, { props: { mobId: 'mob-1' } })
    await wrapper.find('textarea').setValue('instrucción')
    await wrapper.findAll('button').find((b) => b.text() === 'Generar cambios')!.trigger('click')
    await flushPromises()

    await wrapper.findAll('button').find((b) => b.text() === 'Aplicar cambios')!.trigger('click')
    await flushPromises()

    expect(applyEdit).toHaveBeenCalledWith('job-1')
    expect(wrapper.emitted('applied')![0]![0]).toMatchObject({ tag: 'after' })
    const previewEvents = wrapper.emitted('preview-model-changed')!
    expect(previewEvents.at(-1)![0]).toBeNull()
    expect(wrapper.find('textarea').exists()).toBe(true)
  })

  it('Aplicar cambios con base vencida (409 STALE_EDIT_BASE) muestra el error y ofrece regenerar', async () => {
    requestEditPlan.mockResolvedValue(samplePlan())
    applyEdit.mockRejectedValue(new ApiError('El draft avanzó desde que se generó este plan.', 409, 'STALE_EDIT_BASE'))
    const wrapper = mount(AiEditPanel, { props: { mobId: 'mob-1' } })
    await wrapper.find('textarea').setValue('instrucción')
    await wrapper.findAll('button').find((b) => b.text() === 'Generar cambios')!.trigger('click')
    await flushPromises()

    await wrapper.findAll('button').find((b) => b.text() === 'Aplicar cambios')!.trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('El draft avanzó desde que se generó este plan.')
    const regenerateButton = wrapper.findAll('button').find((b) => b.text() === 'Regenerar contra el estado actual')
    expect(regenerateButton).toBeDefined()

    requestEditPlan.mockResolvedValue(samplePlan({ jobId: 'job-2' }))
    await regenerateButton!.trigger('click')
    await flushPromises()

    expect(requestEditPlan).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).toContain('Manos más grandes y hombros asimétricos')
  })

  it('un fallo al generar el plan (ej. NO_BASE_REVISION) muestra el mensaje real y no rompe el formulario', async () => {
    requestEditPlan.mockRejectedValue(new ApiError('Este mob todavía no tiene ninguna revisión guardada.', 400, 'NO_BASE_REVISION'))
    const wrapper = mount(AiEditPanel, { props: { mobId: 'mob-1' } })

    await wrapper.find('textarea').setValue('instrucción')
    await wrapper.findAll('button').find((b) => b.text() === 'Generar cambios')!.trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('Este mob todavía no tiene ninguna revisión guardada.')
    expect(wrapper.find('textarea').exists()).toBe(true)
    expect(wrapper.emitted('preview-model-changed')).toBeUndefined()
  })
})
