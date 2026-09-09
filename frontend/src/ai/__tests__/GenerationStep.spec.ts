import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

// GenerationStep.vue -> GenerationPreviewViewport.vue -> ThreeViewportService.ts
// construye el singleton (`new WebGLRenderer(...)`) al cargar el módulo --
// mismo motivo/mismo patrón que EditorToolbar.spec.ts/ThreeViewport.spec.ts,
// jsdom no tiene WebGL real.
vi.mock('three', async (importOriginal) => {
  const actual = await importOriginal<typeof import('three')>()
  class FakeWebGLRenderer {
    domElement = document.createElement('canvas')
    setSize = vi.fn()
    render = vi.fn()
  }
  return { ...actual, WebGLRenderer: FakeWebGLRenderer }
})

const startGeneration = vi.fn()
const cancelGeneration = vi.fn()
vi.mock('../../api/generationApi', () => ({
  startGeneration: (...args: unknown[]) => startGeneration(...args),
  cancelGeneration: (...args: unknown[]) => cancelGeneration(...args),
  eventsUrl: (jobId: string) => `http://localhost:8080/api/jobs/${jobId}/events`,
}))

const { default: GenerationStep } = await import('../steps/GenerationStep.vue')

/** Doble mínimo de `EventSource` (jsdom no lo implementa) -- expone `emit(type, payload)` para que el test dispare eventos `progress` a mano, sin red real. */
class FakeEventSource {
  static instances: FakeEventSource[] = []
  url: string
  close = vi.fn()
  onerror: (() => void) | null = null
  private listeners: Record<string, ((e: MessageEvent) => void)[]> = {}

  constructor(url: string) {
    this.url = url
    FakeEventSource.instances.push(this)
  }

  addEventListener(type: string, handler: (e: MessageEvent) => void): void {
    ;(this.listeners[type] ??= []).push(handler)
  }

  emit(type: string, data: unknown): void {
    const event = { data: JSON.stringify(data) } as MessageEvent
    for (const handler of this.listeners[type] ?? []) {
      handler(event)
    }
  }
}

function progressEvent(overrides: Partial<Record<string, unknown>> = {}) {
  return { seq: 1, stage: 'analizando_referencia', message: 'Analizando imagen de referencia…', progressPct: 5, payload: null, ...overrides }
}

beforeEach(() => {
  FakeEventSource.instances = []
  vi.stubGlobal('EventSource', FakeEventSource)
  startGeneration.mockReset().mockResolvedValue({ jobId: 'job-1' })
  cancelGeneration.mockReset().mockResolvedValue(undefined)
})

afterEach(() => {
  vi.unstubAllGlobals()
})

const PROPS = { mobId: 'mob-1', projectId: 'project-1', mobName: 'Carcomido', baseType: 'humanoid' as const }

describe('GenerationStep.vue', () => {
  it('al montar, inicia la generación y abre el stream de eventos del job real', async () => {
    mount(GenerationStep, { props: PROPS })
    await flushPromises()

    expect(startGeneration).toHaveBeenCalledWith('mob-1')
    expect(FakeEventSource.instances).toHaveLength(1)
    expect(FakeEventSource.instances[0]?.url).toBe('http://localhost:8080/api/jobs/job-1/events')
  })

  it('un evento de progreso actualiza el mensaje, el porcentaje y marca la etapa activa AC1', async () => {
    const wrapper = mount(GenerationStep, { props: PROPS })
    await flushPromises()

    FakeEventSource.instances[0]!.emit('progress', progressEvent({ stage: 'creando_rig', message: 'Creando hueso: body', progressPct: 45 }))
    await flushPromises()

    expect(wrapper.text()).toContain('Creando hueso: body')
    expect(wrapper.find('[role="progressbar"]').attributes('aria-valuenow')).toBe('45')
    expect(wrapper.find('.generation-step__stage--current').text()).toContain('Creando rig')
  })

  it('un evento preview_operations aplica el delta sin lanzar y sin tocar el store del editor real', async () => {
    const wrapper = mount(GenerationStep, { props: PROPS })
    await flushPromises()

    FakeEventSource.instances[0]!.emit(
      'progress',
      progressEvent({
        stage: 'creando_rig',
        payload: {
          type: 'preview_operations',
          addedOrUpdatedBones: [{ id: 'b1', name: 'body', parentId: null, pivot: [0, 0, 0], rotation: [0, 0, 0] }],
          addedOrUpdatedCuboids: [],
          removedCuboidIds: [],
        },
      }),
    )
    await flushPromises()

    expect(wrapper.find('.generation-preview-viewport').exists()).toBe(true) // no lanzó -- el viewport siguió montado
  })

  it('el evento terminal "completado" muestra el aviso de éxito, cierra el stream y habilita volver AC1', async () => {
    const wrapper = mount(GenerationStep, { props: PROPS })
    await flushPromises()

    FakeEventSource.instances[0]!.emit('progress', progressEvent({ seq: 1 }))
    FakeEventSource.instances[0]!.emit('progress', progressEvent({ seq: 2, stage: 'completado', progressPct: 100 }))
    await flushPromises()

    expect(wrapper.text()).toContain('Generación completada')
    expect(FakeEventSource.instances[0]!.close).toHaveBeenCalled()
    expect(wrapper.find('.generation-step__back').exists()).toBe(true)
  })

  it('el evento terminal "fallido" muestra el mensaje real del error', async () => {
    const wrapper = mount(GenerationStep, { props: PROPS })
    await flushPromises()

    FakeEventSource.instances[0]!.emit('progress', progressEvent({ stage: 'fallido', message: 'El ModelIntent no validó.', progressPct: null }))
    await flushPromises()

    expect(wrapper.text()).toContain('El ModelIntent no validó.')
  })

  it('un evento con seq ya visto se ignora (idempotente ante duplicados de reconexión, AC3)', async () => {
    const wrapper = mount(GenerationStep, { props: PROPS })
    await flushPromises()

    FakeEventSource.instances[0]!.emit('progress', progressEvent({ seq: 1, message: 'primero' }))
    FakeEventSource.instances[0]!.emit('progress', progressEvent({ seq: 1, message: 'duplicado' }))
    await flushPromises()

    expect(wrapper.text()).toContain('primero')
    expect(wrapper.text()).not.toContain('duplicado')
  })

  it('Cancelar pide confirmación antes de llamar a cancelGeneration AC4', async () => {
    const wrapper = mount(GenerationStep, { props: PROPS })
    await flushPromises()

    await wrapper.find('button.g-button--danger').trigger('click')
    expect(cancelGeneration).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('¿Cancelar la generación en curso?')

    await wrapper.findAll('button').find((b) => b.text() === 'Sí, cancelar')!.trigger('click')
    await flushPromises()

    expect(cancelGeneration).toHaveBeenCalledWith('job-1')
  })

  it('un fallo al iniciar la generación muestra un error explícito en vez de quedar colgado', async () => {
    startGeneration.mockRejectedValueOnce(new Error('red caída'))
    const wrapper = mount(GenerationStep, { props: PROPS })
    await flushPromises()

    expect(wrapper.text()).toContain('No se pudo iniciar la generación.')
    expect(wrapper.find('.generation-step__back').exists()).toBe(true)
  })
})
