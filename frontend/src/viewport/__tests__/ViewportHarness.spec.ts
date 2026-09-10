import { flushPromises, shallowMount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { MobProjectModel } from '../../domain/MobProjectModel'
import { useDraftModelStore } from '../../editor/draftModelStore'

// ViewportHarness.vue importa ThreeViewport.vue -> ThreeViewportService.ts,
// que construye el singleton (new WebGLRenderer(...)) al cargar el módulo
// -- mismo motivo que en ThreeViewport.spec.ts, necesario aunque este test
// use shallowMount (el grafo de módulos se evalúa igual antes de montar).
vi.mock('three', async (importOriginal) => {
  const actual = await importOriginal<typeof import('three')>()
  class FakeWebGLRenderer {
    domElement = document.createElement('canvas')
    setSize = vi.fn()
    render = vi.fn()
  }
  return { ...actual, WebGLRenderer: FakeWebGLRenderer }
})

const { default: ViewportHarness } = await import('../ViewportHarness.vue')

function fixtureModel(): MobProjectModel {
  return {
    mobId: 'dev-fixture-carcomido',
    projectId: 'dev-fixture-project',
    name: 'Carcomido_Cuboids',
    baseType: 'humanoid',
    units: 'minecraft_pixels',
    bones: [],
    cuboids: [],
    texture: { width: 64, height: 64, storageKey: null },
    uv: { textureWidth: 64, textureHeight: 64, regions: [], reservations: [] },
    animations: [],
    exportSettings: { preferredFormatVersion: 'v4' },
    referenceImages: [],
  }
}

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

/**
 * Backend fake mínimo para el flujo "busca-o-crea" del harness (ticket
 * 023): permite parametrizar si el proyecto/mob de desarrollo YA existen
 * (caso "segunda vez que corre el harness") o no (caso "primera vez",
 * dispara los POST de creación).
 */
function stubHarnessBackend(options: { projectExists: boolean; mobExists: boolean }): ReturnType<typeof vi.fn> {
  const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input)
    const method = init?.method ?? 'GET'

    if (url.endsWith('carcomido-mob-project-model.json')) {
      return json(fixtureModel())
    }
    if (url.endsWith('/api/projects') && method === 'GET') {
      return json(options.projectExists ? [{ id: 'real-project-1', name: 'Dev Harness', mobCount: 1, mobThumbnails: [], createdAt: '', updatedAt: '' }] : [])
    }
    if (url.endsWith('/api/projects') && method === 'POST') {
      return json({ id: 'real-project-1', name: 'Dev Harness', mobCount: 0, createdAt: '', updatedAt: '' }, 201)
    }
    if (url.endsWith('/api/projects/real-project-1/mobs') && method === 'GET') {
      return json(
        options.mobExists
          ? [{ id: 'real-mob-1', name: 'carcomido-harness', baseType: 'custom', status: 'draft', thumbnailKey: null, updatedAt: '' }]
          : [],
      )
    }
    if (url.endsWith('/api/projects/real-project-1/mobs') && method === 'POST') {
      return json({ id: 'real-mob-1', name: 'carcomido-harness', baseType: 'custom', status: 'draft', thumbnailKey: null, updatedAt: '' }, 201)
    }
    throw new Error(`fetch inesperado en el test: ${method} ${url}`)
  })
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

describe('ViewportHarness.vue', () => {
  beforeEach(() => {
    // ViewportHarness.vue usa useDraftModelStore() (ticket 018) -- necesita
    // una Pinia activa incluso montado fuera de una app real.
    setActivePinia(createPinia())
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('mientras carga el fixture, muestra el estado de carga', () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(() => new Promise(() => {})), // nunca resuelve -- deja el componente en estado "cargando"
    )

    const wrapper = shallowMount(ViewportHarness)

    expect(wrapper.text()).toContain('Cargando fixture')
  })

  it('cuando el fixture carga bien, muestra la etiqueta con el nombre del mob y monta el viewport', async () => {
    stubHarnessBackend({ projectExists: true, mobExists: true })

    const wrapper = shallowMount(ViewportHarness)
    await flushPromises()

    expect(wrapper.text()).toContain('Carcomido_Cuboids')
    expect(wrapper.findComponent({ name: 'ThreeViewport' }).exists()).toBe(true)
  })

  it('cuando el fetch del fixture falla (HTTP no-ok), muestra un mensaje de error explícito', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => new Response(null, { status: 404 })),
    )

    const wrapper = shallowMount(ViewportHarness)
    await flushPromises()

    expect(wrapper.text()).toContain('404')
    expect(wrapper.findComponent({ name: 'ThreeViewport' }).exists()).toBe(false)
  })

  it('ticket 023: reutiliza el proyecto/mob "Dev Harness" reales si ya existen, y sobreescribe mobId/projectId del fixture con los ids reales', async () => {
    stubHarnessBackend({ projectExists: true, mobExists: true })

    shallowMount(ViewportHarness)
    await flushPromises()

    const draft = useDraftModelStore()
    expect(draft.model?.projectId).toBe('real-project-1')
    expect(draft.model?.mobId).toBe('real-mob-1')
    // La geometría real del fixture se conserva intacta -- solo cambian los ids.
    expect(draft.model?.name).toBe('Carcomido_Cuboids')
  })

  it('ticket 023: crea el proyecto/mob "Dev Harness" la primera vez que corre contra un backend limpio', async () => {
    const fetchMock = stubHarnessBackend({ projectExists: false, mobExists: false })

    shallowMount(ViewportHarness)
    await flushPromises()

    const draft = useDraftModelStore()
    expect(draft.model?.projectId).toBe('real-project-1')
    expect(draft.model?.mobId).toBe('real-mob-1')
    expect(fetchMock).toHaveBeenCalledWith(expect.stringContaining('/api/projects'), expect.objectContaining({ method: 'POST' }))
    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringContaining('/api/projects/real-project-1/mobs'),
      expect.objectContaining({ method: 'POST' }),
    )
  })

  it('ticket 023: si el backend no está disponible para preparar el proyecto/mob real, muestra un error explícito en vez de cargar el fixture con ids falsos', async () => {
    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input)
      if (url.endsWith('carcomido-mob-project-model.json')) {
        return json(fixtureModel())
      }
      throw new Error('backend caído')
    }))

    const wrapper = shallowMount(ViewportHarness)
    await flushPromises()

    expect(wrapper.text()).toContain('No se pudo preparar el proyecto/mob real')
    const draft = useDraftModelStore()
    expect(draft.model).toBeNull()
  })
})
