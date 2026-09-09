import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest'

// AiMobWizard.vue -> GenerationStep.vue -> GenerationPreviewViewport.vue ->
// ThreeViewportService.ts construye el singleton (`new WebGLRenderer(...)`)
// al cargar el módulo -- mismo motivo/mismo patrón que EditorToolbar.spec.ts.
vi.mock('three', async (importOriginal) => {
  const actual = await importOriginal<typeof import('three')>()
  class FakeWebGLRenderer {
    domElement = document.createElement('canvas')
    setSize = vi.fn()
    render = vi.fn()
  }
  return { ...actual, WebGLRenderer: FakeWebGLRenderer }
})

const { default: AiMobWizard } = await import('../AiMobWizard.vue')

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

/** jsdom no implementa `EventSource` -- el detalle de sus eventos vive en `GenerationStep.spec.ts`, acá solo hace falta poder disparar el evento terminal a mano para probar la navegación de "Ir al proyecto". */
class FakeEventSource {
  static instances: FakeEventSource[] = []
  url: string
  close = vi.fn()
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

async function routerAt(projectId: string): Promise<Router> {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/projects/:id', component: { template: '<div />' } },
      { path: '/projects/:projectId/mobs/new-ai', component: AiMobWizard },
    ],
  })
  await router.push(`/projects/${projectId}/mobs/new-ai`)
  return router
}

function pngFile(): File {
  return new File([new Uint8Array(4)], 'ref.png', { type: 'image/png' })
}

async function selectReferenceImage(wrapper: ReturnType<typeof mount>): Promise<void> {
  const input = wrapper.find('input[type="file"]')
  Object.defineProperty(input.element, 'files', { value: [pngFile()] })
  await input.trigger('change')
}

beforeAll(() => {
  // jsdom no implementa URL.createObjectURL/revokeObjectURL -- solo hace falta que exista, el valor no importa para estos tests.
  if (!URL.createObjectURL) {
    URL.createObjectURL = vi.fn(() => 'blob:fake-preview')
  }
  if (!URL.revokeObjectURL) {
    URL.revokeObjectURL = vi.fn()
  }
})

describe('AiMobWizard.vue', () => {
  beforeEach(() => {
    FakeEventSource.instances = []
    vi.stubGlobal('EventSource', FakeEventSource)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('arranca en el paso Referencia', async () => {
    const wrapper = mount(AiMobWizard, { global: { plugins: [await routerAt('p1')] } })
    expect(wrapper.findComponent({ name: 'ReferenceStep' }).exists()).toBe(true)
  })

  it('elegir una imagen válida avanza a Configuración, AC #1', async () => {
    const wrapper = mount(AiMobWizard, { global: { plugins: [await routerAt('p1')] } })

    await selectReferenceImage(wrapper)

    expect(wrapper.findComponent({ name: 'ConfigurationStep' }).exists()).toBe(true)
    expect(wrapper.findComponent({ name: 'ReferenceStep' }).exists()).toBe(false)
  })

  it('"Cambiar imagen" en Configuración vuelve a Referencia', async () => {
    const wrapper = mount(AiMobWizard, { global: { plugins: [await routerAt('p1')] } })
    await selectReferenceImage(wrapper)

    await wrapper.find('.configuration-step__back').trigger('click')

    expect(wrapper.findComponent({ name: 'ReferenceStep' }).exists()).toBe(true)
  })

  it('confirmar Configuración crea el mob (022) y sube la referencia (024), y avanza a Generación', async () => {
    const calls: Array<{ url: string; method: string | undefined }> = []
    vi.stubGlobal(
      'fetch',
      vi.fn<typeof fetch>(async (url, init) => {
        calls.push({ url: String(url), method: init?.method })
        if (String(url).endsWith('/mobs') && init?.method === 'POST') {
          return jsonResponse({ id: 'new-mob-id', name: 'Carcomido', baseType: 'humanoid', status: 'draft', thumbnailKey: null, updatedAt: '' }, 201)
        }
        if (String(url).includes('/references') && init?.method === 'POST') {
          return jsonResponse({ id: 'ref-1', url: '/api/mobs/new-mob-id/references/ref-1', width: 1, height: 1, contentType: 'image/png', createdAt: '' }, 201)
        }
        if (String(url).endsWith('/generate') && init?.method === 'POST') {
          return jsonResponse({ jobId: 'job-1' }, 202)
        }
        throw new Error(`fetch inesperado en el test: ${init?.method} ${url}`)
      }),
    )
    const wrapper = mount(AiMobWizard, { global: { plugins: [await routerAt('p1')] } })
    await selectReferenceImage(wrapper)
    await wrapper.find('input[aria-label="Nombre del mob"]').setValue('Carcomido')

    await wrapper.find('button.g-button--primary').trigger('click')
    await flushPromises()

    expect(calls.some((c) => c.url.includes('/api/projects/p1/mobs') && c.method === 'POST')).toBe(true)
    expect(calls.some((c) => c.url.includes('/api/mobs/new-mob-id/references') && c.method === 'POST')).toBe(true)
    expect(wrapper.findComponent({ name: 'GenerationStep' }).exists()).toBe(true)
  })

  it('si crear el mob falla, muestra el error y NO avanza de paso', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn<typeof fetch>(async () => jsonResponse({ error: 'INVALID_PROJECT_NAME', message: 'Falló la creación.' }, 400)),
    )
    const wrapper = mount(AiMobWizard, { global: { plugins: [await routerAt('p1')] } })
    await selectReferenceImage(wrapper)
    await wrapper.find('input[aria-label="Nombre del mob"]').setValue('Carcomido')

    await wrapper.find('button.g-button--primary').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('Falló la creación.')
    expect(wrapper.findComponent({ name: 'ConfigurationStep' }).exists()).toBe(true)
  })

  it('al completar la generación, busca el resultado real y muestra Resultado (030)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn<typeof fetch>(async (url, init) => {
        if (String(url).endsWith('/mobs') && init?.method === 'POST') {
          return jsonResponse({ id: 'new-mob-id', name: 'Carcomido', baseType: 'humanoid', status: 'draft', thumbnailKey: null, updatedAt: '' }, 201)
        }
        if (String(url).endsWith('/generate') && init?.method === 'POST') {
          return jsonResponse({ jobId: 'job-1' }, 202)
        }
        if (String(url).endsWith('/jobs/job-1/result')) {
          return jsonResponse({
            jobId: 'job-1',
            mobId: 'new-mob-id',
            mobName: 'Carcomido',
            cuboidCount: 11,
            boneCount: 6,
            textureWidth: 128,
            textureHeight: 128,
            fmmCompatible: true,
            fmmIssues: [],
          })
        }
        return jsonResponse({ id: 'ref-1', url: '/x', width: 1, height: 1, contentType: 'image/png', createdAt: '' }, 201)
      }),
    )
    const wrapper = mount(AiMobWizard, { global: { plugins: [await routerAt('p1')] } })
    await selectReferenceImage(wrapper)
    await wrapper.find('input[aria-label="Nombre del mob"]').setValue('Carcomido')
    await wrapper.find('button.g-button--primary').trigger('click')
    await flushPromises()

    FakeEventSource.instances[0]!.emit('progress', { seq: 1, stage: 'completado', message: null, progressPct: 100, payload: null })
    await flushPromises()

    expect(wrapper.findComponent({ name: 'ResultStep' }).exists()).toBe(true)
    expect(wrapper.text()).toContain('11')
    expect(wrapper.text()).toContain('6')
  })

  it('"Descartar" en Resultado navega de vuelta a /projects/:id, sin llamar al backend', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn<typeof fetch>(async (url, init) => {
        if (String(url).endsWith('/mobs') && init?.method === 'POST') {
          return jsonResponse({ id: 'new-mob-id', name: 'Carcomido', baseType: 'humanoid', status: 'draft', thumbnailKey: null, updatedAt: '' }, 201)
        }
        if (String(url).endsWith('/generate') && init?.method === 'POST') {
          return jsonResponse({ jobId: 'job-1' }, 202)
        }
        if (String(url).endsWith('/jobs/job-1/result')) {
          return jsonResponse({
            jobId: 'job-1',
            mobId: 'new-mob-id',
            mobName: 'Carcomido',
            cuboidCount: 11,
            boneCount: 6,
            textureWidth: 128,
            textureHeight: 128,
            fmmCompatible: true,
            fmmIssues: [],
          })
        }
        return jsonResponse({ id: 'ref-1', url: '/x', width: 1, height: 1, contentType: 'image/png', createdAt: '' }, 201)
      }),
    )
    const router = await routerAt('p1')
    const pushSpy = vi.spyOn(router, 'push')
    const wrapper = mount(AiMobWizard, { global: { plugins: [router] } })
    await selectReferenceImage(wrapper)
    await wrapper.find('input[aria-label="Nombre del mob"]').setValue('Carcomido')
    await wrapper.find('button.g-button--primary').trigger('click')
    await flushPromises()
    FakeEventSource.instances[0]!.emit('progress', { seq: 1, stage: 'completado', message: null, progressPct: 100, payload: null })
    await flushPromises()

    await wrapper.findAll('.result-step__action').find((a) => a.text() === 'Descartar')!.trigger('click')
    await wrapper.findAll('.result-step__action').find((a) => a.text() === 'Confirmar')!.trigger('click')
    await flushPromises()

    expect(pushSpy).toHaveBeenCalledWith('/projects/p1')
  })

  it('"Usar este modelo" en Resultado llama a POST /apply y navega a /projects/:id, ticket 030 AC4', async () => {
    const calls: Array<{ url: string; method: string | undefined }> = []
    vi.stubGlobal(
      'fetch',
      vi.fn<typeof fetch>(async (url, init) => {
        calls.push({ url: String(url), method: init?.method })
        if (String(url).endsWith('/mobs') && init?.method === 'POST') {
          return jsonResponse({ id: 'new-mob-id', name: 'Carcomido', baseType: 'humanoid', status: 'draft', thumbnailKey: null, updatedAt: '' }, 201)
        }
        if (String(url).endsWith('/generate') && init?.method === 'POST') {
          return jsonResponse({ jobId: 'job-1' }, 202)
        }
        if (String(url).endsWith('/jobs/job-1/result')) {
          return jsonResponse({
            jobId: 'job-1',
            mobId: 'new-mob-id',
            mobName: 'Carcomido',
            cuboidCount: 11,
            boneCount: 6,
            textureWidth: 128,
            textureHeight: 128,
            fmmCompatible: true,
            fmmIssues: [],
          })
        }
        if (String(url).endsWith('/jobs/job-1/apply') && init?.method === 'POST') {
          return jsonResponse({ revisionNumber: 1, draftVersion: 1 }, 201)
        }
        return jsonResponse({ id: 'ref-1', url: '/x', width: 1, height: 1, contentType: 'image/png', createdAt: '' }, 201)
      }),
    )
    const router = await routerAt('p1')
    const pushSpy = vi.spyOn(router, 'push')
    const wrapper = mount(AiMobWizard, { global: { plugins: [router] } })
    await selectReferenceImage(wrapper)
    await wrapper.find('input[aria-label="Nombre del mob"]').setValue('Carcomido')
    await wrapper.find('button.g-button--primary').trigger('click')
    await flushPromises()
    FakeEventSource.instances[0]!.emit('progress', { seq: 1, stage: 'completado', message: null, progressPct: 100, payload: null })
    await flushPromises()

    await wrapper.findAll('.result-step__action').find((a) => a.text() === 'Usar este modelo')!.trigger('click')
    await wrapper.findAll('.result-step__action').find((a) => a.text() === 'Confirmar')!.trigger('click')
    await flushPromises()

    expect(calls.some((c) => c.url.endsWith('/jobs/job-1/apply') && c.method === 'POST')).toBe(true)
    expect(pushSpy).toHaveBeenCalledWith('/projects/p1')
  })
})
