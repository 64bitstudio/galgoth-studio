import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'

// ResultStep.vue importa GenerationPreviewViewport.vue -> ThreeViewportService.ts,
// que construye el singleton (`new WebGLRenderer(...)`) al CARGAR el módulo
// -- ocurre con solo importar el archivo, sin importar si el componente
// llega a montarse (ticket 037, mismo motivo que EditorToolbar.spec.ts).
vi.mock('three', async (importOriginal) => {
  const actual = await importOriginal<typeof import('three')>()
  class FakeWebGLRenderer {
    domElement = document.createElement('canvas')
    setSize = vi.fn()
    render = vi.fn()
  }
  return { ...actual, WebGLRenderer: FakeWebGLRenderer }
})

const { default: ResultStep } = await import('../steps/ResultStep.vue')

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

    const actions = wrapper.findAll('button')
    expect(actions).toHaveLength(3)
    expect(actions.map((a) => a.text())).toEqual(['Descartar', 'Regenerar', 'Usar este modelo'])
    for (const action of actions) {
      expect(action.attributes('disabled')).toBeUndefined()
    }
  })

  it('Descartar pide confirmación antes de emitir "discard"', async () => {
    const wrapper = mount(ResultStep, { props: { jobId: 'job-1' } })

    await wrapper.findAll('button').find((a) => a.text() === 'Descartar')!.trigger('click')
    expect(wrapper.emitted('discard')).toBeUndefined()
    expect(wrapper.text()).toContain('¿Descartar esta propuesta?')

    await wrapper.findAll('button').find((a) => a.text() === 'Confirmar')!.trigger('click')
    expect(wrapper.emitted('discard')).toHaveLength(1)
  })

  it('Regenerar pide confirmación antes de emitir "regenerate"', async () => {
    const wrapper = mount(ResultStep, { props: { jobId: 'job-1' } })

    await wrapper.findAll('button').find((a) => a.text() === 'Regenerar')!.trigger('click')
    await wrapper.findAll('button').find((a) => a.text() === 'Confirmar')!.trigger('click')

    expect(wrapper.emitted('regenerate')).toHaveLength(1)
  })

  it('Usar este modelo pide confirmación antes de emitir "apply"', async () => {
    const wrapper = mount(ResultStep, { props: { jobId: 'job-1' } })

    await wrapper.findAll('button').find((a) => a.text() === 'Usar este modelo')!.trigger('click')
    await wrapper.findAll('button').find((a) => a.text() === 'Confirmar')!.trigger('click')

    expect(wrapper.emitted('apply')).toHaveLength(1)
  })

  it('Cancelar la confirmación no emite nada y vuelve a mostrar las 3 acciones', async () => {
    const wrapper = mount(ResultStep, { props: { jobId: 'job-1' } })

    await wrapper.findAll('button').find((a) => a.text() === 'Descartar')!.trigger('click')
    await wrapper.findAll('button').find((a) => a.text() === 'Cancelar')!.trigger('click')

    expect(wrapper.emitted('discard')).toBeUndefined()
    expect(wrapper.findAll('button').map((a) => a.text())).toEqual(['Descartar', 'Regenerar', 'Usar este modelo'])
  })

  it('busy deshabilita las acciones y actionError muestra el mensaje real', () => {
    const wrapper = mount(ResultStep, { props: { jobId: 'job-1', busy: true, actionError: 'No se pudo aceptar este modelo.' } })

    for (const action of wrapper.findAll('button')) {
      expect(action.attributes('disabled')).toBeDefined()
    }
    expect(wrapper.text()).toContain('No se pudo aceptar este modelo.')
  })

  // Post-074 -- rediseño del wizard.
  describe('rediseño post-074', () => {
    it('muestra el pill flotante de compatibilidad FMM sobre el preview, con el mismo dato real', () => {
      const okWrapper = mount(ResultStep, { props: { jobId: 'job-1', fmmCompatible: true } })
      expect(okWrapper.get('.result-step__compat-pill').text()).toContain('Compatible con FMM')

      const issueWrapper = mount(ResultStep, { props: { jobId: 'job-1', fmmCompatible: false } })
      expect(issueWrapper.get('.result-step__compat-pill').text()).toContain('Con problemas de compatibilidad')
    })

    it('sigue habiendo exactamente 3 <button> -- ningún control decorativo nuevo sin función real (ej. no se agregó "Ver detalle técnico")', () => {
      const wrapper = mount(ResultStep, { props: { jobId: 'job-1' } })
      expect(wrapper.findAll('button')).toHaveLength(3)
      expect(wrapper.text()).not.toContain('detalle técnico')
    })

    it('muestra los hints de interacción del viewport', () => {
      const wrapper = mount(ResultStep, { props: { jobId: 'job-1' } })
      const hints = wrapper.get('.result-step__viewport-hints')

      expect(hints.text()).toContain('Arrastra para rotar')
      expect(hints.text()).toContain('Usa la rueda para hacer zoom')
      expect(hints.text()).toContain('Clic derecho para mover')
    })
  })
})
