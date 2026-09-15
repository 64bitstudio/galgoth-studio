import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import ExploreView from '../ExploreView.vue'
import type { ProjectSummary } from '../../projects/projectsApi'

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

function project(overrides: Partial<ProjectSummary> = {}): ProjectSummary {
  return {
    id: 'p1',
    name: 'Galgoth',
    description: null,
    mobCount: 0,
    mobThumbnails: [],
    status: 'draft',
    visibility: 'PUBLIC',
    ownerDisplayName: 'Ada Lovelace',
    avatarUrl: null,
    createdAt: '',
    updatedAt: '',
    ...overrides,
  }
}

function testRouter(): Router {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', component: { template: '<div />' } },
      { path: '/projects', component: { template: '<div />' } },
      { path: '/explore', component: ExploreView },
      { path: '/explore/:id', component: { template: '<div />' } },
    ],
  })
}

describe('ExploreView.vue', () => {
  // Ticket 088 -- request() pasa por authenticatedFetch (ticket 078), que necesita un Pinia activo para leer la sesión (sin sesión, simplemente no adjunta Authorization -- exactamente el caso que este ticket ejercita).
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  // Ticket 088 -- AC: visitante sin sesión ve los proyectos públicos reales (sin stub de sessionStore -- exactamente el caso sin sesión).
  it('sin sesión, carga y muestra los proyectos públicos de GET /api/explore/projects', async () => {
    const fetchMock = vi.fn<typeof fetch>(async () => jsonResponse([project({ name: 'Público de Ada' }), project({ id: 'p2', name: 'Público de Grace', ownerDisplayName: 'Grace Hopper' })]))
    vi.stubGlobal('fetch', fetchMock)
    const router = testRouter()
    await router.push('/explore')
    const wrapper = mount(ExploreView, { global: { plugins: [router] } })
    await flushPromises()

    expect(wrapper.text()).toContain('Público de Ada')
    expect(wrapper.text()).toContain('por Ada Lovelace')
    expect(wrapper.text()).toContain('Público de Grace')
    const [url] = fetchMock.mock.calls[0]!
    expect(String(url)).toContain('/api/explore/projects')
  })

  it('sin resultados, muestra el estado vacío explícito (nunca una pantalla en blanco)', async () => {
    vi.stubGlobal('fetch', vi.fn<typeof fetch>(async () => jsonResponse([])))
    const router = testRouter()
    await router.push('/explore')
    const wrapper = mount(ExploreView, { global: { plugins: [router] } })
    await flushPromises()

    expect(wrapper.text()).toContain('Todavía no hay proyectos públicos.')
  })

  it('un error de carga muestra un mensaje explícito, no la grilla vacía en silencio', async () => {
    vi.stubGlobal('fetch', vi.fn<typeof fetch>(async () => jsonResponse({ error: 'INTERNAL', message: 'Ocurrió un error inesperado.' }, 500)))
    const router = testRouter()
    await router.push('/explore')
    const wrapper = mount(ExploreView, { global: { plugins: [router] } })
    await flushPromises()

    expect(wrapper.text()).toContain('Ocurrió un error inesperado.')
  })

  it('al abrir una tarjeta, navega a /explore/{id}', async () => {
    vi.stubGlobal('fetch', vi.fn<typeof fetch>(async () => jsonResponse([project({ id: 'p9' })])))
    const router = testRouter()
    await router.push('/explore')
    const pushSpy = vi.spyOn(router, 'push')
    const wrapper = mount(ExploreView, { global: { plugins: [router] } })
    await flushPromises()

    await wrapper.find('.explore-card').trigger('click')

    expect(pushSpy).toHaveBeenCalledWith('/explore/p9')
  })

  it('sidebar: "Mis proyectos" navega a /projects', async () => {
    vi.stubGlobal('fetch', vi.fn<typeof fetch>(async () => jsonResponse([])))
    const router = testRouter()
    await router.push('/explore')
    const pushSpy = vi.spyOn(router, 'push')
    const wrapper = mount(ExploreView, { global: { plugins: [router] } })
    await flushPromises()

    await wrapper.findAll('.g-sidebar__item')[1]!.trigger('click')

    expect(pushSpy).toHaveBeenCalledWith('/projects')
  })
})
