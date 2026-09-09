import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'
import { afterEach, beforeAll, describe, expect, it, vi } from 'vitest'
import AiMobWizard from '../AiMobWizard.vue'

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
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

  it('"Ir al proyecto" desde Generación navega de vuelta a /projects/:id', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn<typeof fetch>(async (url, init) => {
        if (String(url).endsWith('/mobs') && init?.method === 'POST') {
          return jsonResponse({ id: 'new-mob-id', name: 'Carcomido', baseType: 'humanoid', status: 'draft', thumbnailKey: null, updatedAt: '' }, 201)
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

    await wrapper.find('.generation-step__back').trigger('click')

    expect(pushSpy).toHaveBeenCalledWith('/projects/p1')
  })
})
