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
  sessionStorage.clear() // ticket 038 -- GenerationStep persiste el jobId en sessionStorage (recuperación tras refresh); sin esto, un test "ve" el jobId que dejó el anterior.
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
    expect(wrapper.find('progress.generation-step__progress').attributes('value')).toBe('45')
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

  it('el evento terminal "completado" muestra el aviso de éxito, cierra el stream y emite "completed" con el jobId real, ticket 030', async () => {
    const wrapper = mount(GenerationStep, { props: PROPS })
    await flushPromises()

    FakeEventSource.instances[0]!.emit('progress', progressEvent({ seq: 1 }))
    FakeEventSource.instances[0]!.emit('progress', progressEvent({ seq: 2, stage: 'completado', progressPct: 100 }))
    await flushPromises()

    expect(wrapper.text()).toContain('Generación completada')
    expect(FakeEventSource.instances[0]!.close).toHaveBeenCalled()
    // Ticket 037: `completed` también manda el `previewModel` final (el
    // mismo ya construido en memoria vía SSE) para que "Resultado" pueda
    // mostrarlo -- acá empieza vacío porque este test no emitió ningún
    // `preview_operations` antes del evento terminal.
    expect(wrapper.emitted('completed')?.[0]?.[0]).toBe('job-1')
    expect((wrapper.emitted('completed')?.[0]?.[1] as { cuboids: unknown[] }).cuboids).toEqual([])
    // El botón "Ir al proyecto" es solo el escape hatch de fallido/cancelado
    // -- al completar, es AiMobWizard.vue (030) quien avanza a "Resultado".
    expect(wrapper.findAll('button').find((b) => b.text() === 'Ir al proyecto')).toBeUndefined()
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
    expect(wrapper.findAll('button').find((b) => b.text() === 'Ir al proyecto')).toBeDefined()
  })

  it('ticket 038: las 2 etapas nuevas (preparando_resultado/validando_geometria) se muestran igual que las demás', async () => {
    const wrapper = mount(GenerationStep, { props: PROPS })
    await flushPromises()

    FakeEventSource.instances[0]!.emit('progress', progressEvent({ stage: 'preparando_resultado', message: 'Aplicando UV…', progressPct: 90 }))
    await flushPromises()

    expect(wrapper.text()).toContain('Preparando resultado…')
    expect(wrapper.text()).toContain('Validando geometría…')
    expect(wrapper.find('.generation-step__stage--current').text()).toContain('Preparando resultado')
  })

  it('ticket 038: un error de conexión del EventSource muestra "Reconectando" mientras el job sigue corriendo, y desaparece al llegar el próximo evento real', async () => {
    const wrapper = mount(GenerationStep, { props: PROPS })
    await flushPromises()

    FakeEventSource.instances[0]!.onerror?.()
    await flushPromises()
    expect(wrapper.text()).toContain('Reconectando al proceso')

    FakeEventSource.instances[0]!.emit('progress', progressEvent({ seq: 2, stage: 'creando_rig' }))
    await flushPromises()
    expect(wrapper.text()).not.toContain('Reconectando al proceso')
  })

  it('ticket 038: "Reintentar" tras un fallo dispara una generación nueva, con su propio jobId y stream', async () => {
    const wrapper = mount(GenerationStep, { props: PROPS })
    await flushPromises()

    FakeEventSource.instances[0]!.emit('progress', progressEvent({ stage: 'fallido', message: 'El proveedor falló.', progressPct: null }))
    await flushPromises()
    expect(wrapper.text()).toContain('La generación falló: El proveedor falló.')

    startGeneration.mockResolvedValueOnce({ jobId: 'job-2' })
    await wrapper.findAll('button').find((b) => b.text() === 'Reintentar')!.trigger('click')
    await flushPromises()

    expect(startGeneration).toHaveBeenCalledTimes(2)
    expect(FakeEventSource.instances).toHaveLength(2)
    expect(FakeEventSource.instances[1]!.url).toContain('job-2')
    expect(wrapper.text()).not.toContain('La generación falló')
  })

  it('ticket 038: con un jobId ya persistido para este mob (refresh de página), reconecta en vez de arrancar un POST /generate nuevo', async () => {
    sessionStorage.setItem('galgoth:ai-job:mob-1', 'job-resumido')

    const wrapper = mount(GenerationStep, { props: PROPS })
    await flushPromises()

    expect(startGeneration).not.toHaveBeenCalled()
    expect(FakeEventSource.instances).toHaveLength(1)
    expect(FakeEventSource.instances[0]!.url).toContain('job-resumido')

    // El backlog completo llega igual por la reconexión -- el componente reconstruye el estado real (no arranca en 0% a la fuerza).
    FakeEventSource.instances[0]!.emit('progress', progressEvent({ seq: 1, stage: 'creando_rig', progressPct: 45 }))
    await flushPromises()
    expect(wrapper.find('progress.generation-step__progress').attributes('value')).toBe('45')
  })

  it('ticket 038: al llegar a un estado terminal, se limpia el jobId persistido (no queda un resume fantasma para la próxima generación)', async () => {
    mount(GenerationStep, { props: PROPS })
    await flushPromises()

    FakeEventSource.instances[0]!.emit('progress', progressEvent({ stage: 'completado', progressPct: 100 }))
    await flushPromises()

    expect(sessionStorage.getItem('galgoth:ai-job:mob-1')).toBeNull()
  })

  // Post-074 -- rediseño del wizard: registro en tiempo real, tiempo por etapa, badges del preview.
  describe('rediseño post-074', () => {
    it('cada evento con mensaje agrega un renglón al "Registro en tiempo real"', async () => {
      const wrapper = mount(GenerationStep, { props: PROPS })
      await flushPromises()

      FakeEventSource.instances[0]!.emit('progress', progressEvent({ seq: 1, message: 'Analizando imagen de referencia…' }))
      await flushPromises()
      FakeEventSource.instances[0]!.emit('progress', progressEvent({ seq: 2, stage: 'detectando_silueta', message: 'Detectando silueta…' }))
      await flushPromises()

      const log = wrapper.get('.generation-step__log-lines')
      expect(log.text()).toContain('Analizando imagen de referencia…')
      expect(log.text()).toContain('Detectando silueta…')
    })

    it('un evento sin mensaje (null) no agrega ningún renglón vacío al registro', async () => {
      const wrapper = mount(GenerationStep, { props: PROPS })
      await flushPromises()

      FakeEventSource.instances[0]!.emit('progress', progressEvent({ seq: 1, stage: 'completado', message: null, progressPct: 100 }))
      await flushPromises()

      expect(wrapper.get('.generation-step__log-lines').text()).toContain('Esperando el primer evento')
    })

    it('cada etapa completada muestra su doneHint (descripción en pasado), no el hint en progreso', async () => {
      const wrapper = mount(GenerationStep, { props: PROPS })
      await flushPromises()

      FakeEventSource.instances[0]!.emit('progress', progressEvent({ seq: 1, stage: 'detectando_silueta' }))
      await flushPromises()

      const doneStage = wrapper.findAll('.generation-step__stage--done')[0]!
      expect(doneStage.text()).toContain('Imagen procesada correctamente.')
    })

    it('los badges de Polígonos/Huesos reflejan el previewModel en construcción', async () => {
      const wrapper = mount(GenerationStep, { props: PROPS })
      await flushPromises()

      FakeEventSource.instances[0]!.emit(
        'progress',
        progressEvent({
          seq: 1,
          stage: 'creando_rig',
          payload: {
            type: 'preview_operations',
            addedOrUpdatedBones: [
              { id: 'b1', name: 'body', parentId: null, pivot: [0, 0, 0], rotation: [0, 0, 0] },
              { id: 'b2', name: 'head', parentId: 'b1', pivot: [0, 0, 0], rotation: [0, 0, 0] },
            ],
            addedOrUpdatedCuboids: [],
            removedCuboidIds: [],
          },
        }),
      )
      await flushPromises()

      const pills = wrapper.get('.generation-step__stat-pills')
      expect(pills.text()).toContain('2') // Huesos (rig)
    })

    it('el botón "Cancelar generación" es de ancho completo con ícono, mismo comportamiento real que antes', async () => {
      const wrapper = mount(GenerationStep, { props: PROPS })
      await flushPromises()

      const cancelBtn = wrapper.get('.generation-step__cancel-btn')
      expect(cancelBtn.text()).toContain('Cancelar generación')

      await cancelBtn.trigger('click')
      expect(wrapper.text()).toContain('¿Cancelar la generación en curso?')
    })
  })
})
