import { flushPromises, shallowMount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useDraftModelStore } from '../draftModelStore'

// MobEditor.vue -> ThreeViewport.vue -> ThreeViewportService.ts construye
// el singleton (`new WebGLRenderer(...)`) al cargar el módulo -- mismo
// motivo/mismo patrón que ViewportHarness.spec.ts.
vi.mock('three', async (importOriginal) => {
  const actual = await importOriginal<typeof import('three')>()
  class FakeWebGLRenderer {
    domElement = document.createElement('canvas')
    setSize = vi.fn()
    render = vi.fn()
  }
  return { ...actual, WebGLRenderer: FakeWebGLRenderer }
})

const { default: MobEditor } = await import('../MobEditor.vue')

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

async function routerAt(projectId: string, mobId: string): Promise<Router> {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/projects/:projectId/mobs/:mobId/edit', component: MobEditor }],
  })
  await router.push(`/projects/${projectId}/mobs/${mobId}/edit`)
  return router
}

describe('MobEditor.vue', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('con un draft real, lo carga y muestra el editor, ticket 034 AC1', async () => {
    const draftModel = {
      mobId: 'mob-1',
      projectId: 'p1',
      name: 'Carcomido',
      baseType: 'humanoid' as const,
      units: 'minecraft_pixels' as const,
      bones: [],
      cuboids: [],
      texture: { width: 128, height: 128, storageKey: null },
      uv: { textureWidth: 128, textureHeight: 128, regions: [] },
      animations: [],
      exportSettings: { preferredFormatVersion: 'v5' as const },
      referenceImages: [],
    }
    vi.stubGlobal(
      'fetch',
      vi.fn<typeof fetch>(async (url) => {
        const u = String(url)
        if (u.endsWith('/api/mobs/mob-1') || u.match(/\/api\/mobs\/mob-1$/)) {
          return jsonResponse({ id: 'mob-1', name: 'Carcomido', baseType: 'humanoid', status: 'draft', thumbnailKey: null, updatedAt: '' })
        }
        if (u.endsWith('/api/mobs/mob-1/draft')) {
          return jsonResponse({ mobId: 'mob-1', draftVersion: 2, model: draftModel, updatedAt: '' })
        }
        throw new Error(`fetch inesperado: ${u}`)
      }),
    )

    const wrapper = shallowMount(MobEditor, { global: { plugins: [await routerAt('p1', 'mob-1')] } })
    await flushPromises()

    const draft = useDraftModelStore()
    expect(draft.model?.mobId).toBe('mob-1')
    expect(draft.model?.name).toBe('Carcomido')
    expect(wrapper.findComponent({ name: 'ThreeViewport' }).exists()).toBe(true)
    expect(wrapper.findComponent({ name: 'EditorToolbar' }).exists()).toBe(true)
  })

  it('sin ningún draft todavía (DRAFT_NOT_FOUND), arranca desde un modelo vacío coherente con el mob real, ticket 034 AC2', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn<typeof fetch>(async (url) => {
        const u = String(url)
        if (u.match(/\/api\/mobs\/mob-1$/)) {
          return jsonResponse({ id: 'mob-1', name: 'Carcomido Nuevo', baseType: 'arachnid', status: 'draft', thumbnailKey: null, updatedAt: '' })
        }
        if (u.endsWith('/api/mobs/mob-1/draft')) {
          return jsonResponse({ error: 'DRAFT_NOT_FOUND', message: 'Sin draft todavía.' }, 404)
        }
        throw new Error(`fetch inesperado: ${u}`)
      }),
    )

    shallowMount(MobEditor, { global: { plugins: [await routerAt('p1', 'mob-1')] } })
    await flushPromises()

    const draft = useDraftModelStore()
    expect(draft.model?.mobId).toBe('mob-1')
    expect(draft.model?.name).toBe('Carcomido Nuevo')
    expect(draft.model?.baseType).toBe('arachnid')
    expect(draft.model?.bones).toEqual([])
    expect(draft.model?.cuboids).toEqual([])
  })

  it('un mobId inexistente (MOB_NOT_FOUND) muestra un error explícito, nunca un editor roto', async () => {
    vi.stubGlobal('fetch', vi.fn<typeof fetch>(async () => jsonResponse({ error: 'MOB_NOT_FOUND', message: 'No existe.' }, 404)))

    const wrapper = shallowMount(MobEditor, { global: { plugins: [await routerAt('p1', 'no-existe')] } })
    await flushPromises()

    expect(wrapper.text()).toContain('Este mob no existe')
    expect(wrapper.findComponent({ name: 'ThreeViewport' }).exists()).toBe(false)
  })

  it('un fallo de red real (no un 404 esperado) muestra el mensaje real del error', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn<typeof fetch>(async (url) => {
        const u = String(url)
        if (u.match(/\/api\/mobs\/mob-1$/)) {
          return jsonResponse({ id: 'mob-1', name: 'Carcomido', baseType: 'humanoid', status: 'draft', thumbnailKey: null, updatedAt: '' })
        }
        return jsonResponse({ error: 'INTERNAL', message: 'Fallo real del servidor.' }, 500)
      }),
    )

    const wrapper = shallowMount(MobEditor, { global: { plugins: [await routerAt('p1', 'mob-1')] } })
    await flushPromises()

    expect(wrapper.text()).toContain('Fallo real del servidor.')
  })
})
