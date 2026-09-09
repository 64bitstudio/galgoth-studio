import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import ResultStep from '../steps/ResultStep.vue'

describe('ResultStep.vue', () => {
  it('sin jobId (harness de desarrollo), muestra datos de ejemplo y un aviso explícito de que lo son', () => {
    const wrapper = mount(ResultStep)

    expect(wrapper.text()).toContain('Carcomido')
    expect(wrapper.text()).toContain('27')
    expect(wrapper.text()).toContain('datos de ejemplo')
  })

  it('con jobId real, muestra los datos reales recibidos y NO el aviso de ejemplo, ticket 030 AC1', () => {
    const wrapper = mount(ResultStep, {
      props: {
        jobId: 'job-1',
        mobName: 'Tejedora',
        cuboidCount: 12,
        boneCount: 5,
        textureWidth: 64,
        textureHeight: 64,
        fmmCompatible: true,
      },
    })

    expect(wrapper.text()).toContain('Tejedora')
    expect(wrapper.text()).toContain('12')
    expect(wrapper.text()).toContain('64×64')
    expect(wrapper.text()).not.toContain('datos de ejemplo')
  })

  it('muestra el estado de compatibilidad FMM real -- OK y con problemas, AC1', () => {
    const okWrapper = mount(ResultStep, { props: { jobId: 'job-1', fmmCompatible: true } })
    expect(okWrapper.text()).toContain('Compatible')

    const issueWrapper = mount(ResultStep, {
      props: {
        jobId: 'job-1',
        fmmCompatible: false,
        fmmIssues: [{ severity: 'ERROR', rule: 'CUBOID_ZERO_SIZE', element: 'c1', message: 'El cuboid c1 tiene dimensión cero.' }],
      },
    })
    expect(issueWrapper.text()).toContain('Con problemas')
    expect(issueWrapper.text()).toContain('El cuboid c1 tiene dimensión cero.')
  })

  it('las 3 acciones de HU-12 están presentes y habilitadas', () => {
    const wrapper = mount(ResultStep, { props: { jobId: 'job-1' } })

    const actions = wrapper.findAll('.result-step__action')
    expect(actions).toHaveLength(3)
    expect(actions.map((a) => a.text())).toEqual(['Descartar', 'Regenerar', 'Usar este modelo'])
    for (const action of actions) {
      expect(action.attributes('disabled')).toBeUndefined()
    }
  })

  it('Descartar pide confirmación antes de emitir "discard"', async () => {
    const wrapper = mount(ResultStep, { props: { jobId: 'job-1' } })

    await wrapper.findAll('.result-step__action').find((a) => a.text() === 'Descartar')!.trigger('click')
    expect(wrapper.emitted('discard')).toBeUndefined()
    expect(wrapper.text()).toContain('¿Descartar esta propuesta?')

    await wrapper.findAll('.result-step__action').find((a) => a.text() === 'Confirmar')!.trigger('click')
    expect(wrapper.emitted('discard')).toHaveLength(1)
  })

  it('Regenerar pide confirmación antes de emitir "regenerate"', async () => {
    const wrapper = mount(ResultStep, { props: { jobId: 'job-1' } })

    await wrapper.findAll('.result-step__action').find((a) => a.text() === 'Regenerar')!.trigger('click')
    await wrapper.findAll('.result-step__action').find((a) => a.text() === 'Confirmar')!.trigger('click')

    expect(wrapper.emitted('regenerate')).toHaveLength(1)
  })

  it('Usar este modelo pide confirmación antes de emitir "apply"', async () => {
    const wrapper = mount(ResultStep, { props: { jobId: 'job-1' } })

    await wrapper.findAll('.result-step__action').find((a) => a.text() === 'Usar este modelo')!.trigger('click')
    await wrapper.findAll('.result-step__action').find((a) => a.text() === 'Confirmar')!.trigger('click')

    expect(wrapper.emitted('apply')).toHaveLength(1)
  })

  it('Cancelar la confirmación no emite nada y vuelve a mostrar las 3 acciones', async () => {
    const wrapper = mount(ResultStep, { props: { jobId: 'job-1' } })

    await wrapper.findAll('.result-step__action').find((a) => a.text() === 'Descartar')!.trigger('click')
    await wrapper.findAll('.result-step__action').find((a) => a.text() === 'Cancelar')!.trigger('click')

    expect(wrapper.emitted('discard')).toBeUndefined()
    expect(wrapper.findAll('.result-step__action').map((a) => a.text())).toEqual(['Descartar', 'Regenerar', 'Usar este modelo'])
  })

  it('busy deshabilita las acciones y actionError muestra el mensaje real', () => {
    const wrapper = mount(ResultStep, { props: { jobId: 'job-1', busy: true, actionError: 'No se pudo aceptar este modelo.' } })

    for (const action of wrapper.findAll('.result-step__action')) {
      expect(action.attributes('disabled')).toBeDefined()
    }
    expect(wrapper.text()).toContain('No se pudo aceptar este modelo.')
  })
})
