import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'
import { afterEach, beforeAll, describe, expect, it, vi } from 'vitest'
import HomeView from '../HomeView.vue'
import type { ProjectSummary } from '../projectsApi'
import type { RecentMobSummary } from '../mobsApi'

// jsdom no implementa HTMLDialogElement.showModal()/close() -- este
// componente monta ProjectNameModal.vue/MobRenameDialog.vue/
// AiMobProjectPickerDialog.vue, mismo motivo que en ProjectsDashboard.spec.ts.
beforeAll(() => {
  if (!HTMLDialogElement.prototype.showModal) {
    HTMLDialogElement.prototype.showModal = function (this: HTMLDialogElement) {
      this.setAttribute('open', '')
    }
  }
  if (!HTMLDialogElement.prototype.close) {
    HTMLDialogElement.prototype.close = function (this: HTMLDialogElement) {
      this.removeAttribute('open')
    }
  }
})

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

function mob(overrides: Partial<RecentMobSummary> = {}): RecentMobSummary {
  return {
    id: 'm1',
    projectId: 'p1',
    name: 'Carcomido',
    baseType: 'humanoid',
    status: 'draft',
    thumbnailKey: null,
    updatedAt: new Date().toISOString(),
    ...overrides,
  }
}

function project(overrides: Partial<ProjectSummary> = {}): ProjectSummary {
  return { id: 'p1', name: 'Galgoth', description: null, mobCount: 0, mobThumbnails: [], status: 'draft', createdAt: '', updatedAt: new Date().toISOString(), ...overrides }
}

function testRouter(): Router {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', component: HomeView },
      { path: '/projects', component: { template: '<div />' } },
      { path: '/projects/:id', component: { template: '<div />' } },
      { path: '/projects/:projectId/mobs/new-ai', component: { template: '<div />' } },
      { path: '/projects/:projectId/mobs/:mobId/edit', component: { template: '<div />' } },
      { path: '/projects/:projectId/mobs/:mobId/export', component: { template: '<div />' } },
    ],
  })
}

/** GET /api/mobs/recent y GET /api/projects en cualquier orden -- Promise.all no garantiza cuál llega primero. */
function stubRecentAndProjects(mobs: RecentMobSummary[], projects: ProjectSummary[]): ReturnType<typeof vi.fn> {
  return vi.fn<typeof fetch>(async (url) => {
    if (String(url).includes('/mobs/recent')) {
      return jsonResponse(mobs)
    }
    return jsonResponse(projects)
  })
}

describe('HomeView.vue', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('muestra el saludo fijo', async () => {
    vi.stubGlobal('fetch', stubRecentAndProjects([], []))
    const wrapper = mount(HomeView, { global: { plugins: [testRouter()] } })
    await flushPromises()

    expect(wrapper.text()).toContain('Buenos días')
    expect(wrapper.text()).toContain('¿Qué quieres crear hoy?')
  })

  it('carga y muestra los mobs recientes (endpoint cross-proyecto) y los proyectos recientes', async () => {
    vi.stubGlobal(
      'fetch',
      stubRecentAndProjects(
        [mob({ name: 'Tejedora' })],
        [project({ name: 'Criaturas del Nether' })],
      ),
    )
    const wrapper = mount(HomeView, { global: { plugins: [testRouter()] } })
    await flushPromises()

    expect(wrapper.text()).toContain('Tejedora')
    expect(wrapper.text()).toContain('Criaturas del Nether')
  })

  it('solo muestra hasta 2 proyectos recientes aunque listProjects() devuelva más', async () => {
    vi.stubGlobal(
      'fetch',
      stubRecentAndProjects(
        [],
        [project({ id: 'p1', name: 'Galgoth' }), project({ id: 'p2', name: 'Nether' }), project({ id: 'p3', name: 'Tercero' })],
      ),
    )
    const wrapper = mount(HomeView, { global: { plugins: [testRouter()] } })
    await flushPromises()

    expect(wrapper.text()).toContain('Galgoth')
    expect(wrapper.text()).toContain('Nether')
    expect(wrapper.text()).not.toContain('Tercero')
  })

  it('sin mobs recientes, muestra el estado vacío con CTA a Crear con IA', async () => {
    vi.stubGlobal('fetch', stubRecentAndProjects([], [project()]))
    const wrapper = mount(HomeView, { global: { plugins: [testRouter()] } })
    await flushPromises()

    expect(wrapper.text()).toContain('Aún no has creado ningún mob.')
  })

  it('sin proyectos, muestra el estado vacío con CTA a Crear proyecto', async () => {
    vi.stubGlobal('fetch', stubRecentAndProjects([mob()], []))
    const wrapper = mount(HomeView, { global: { plugins: [testRouter()] } })
    await flushPromises()

    expect(wrapper.text()).toContain('Todavía no tienes proyectos.')
  })

  it('"Ver todos" de ambas secciones navega a /projects', async () => {
    vi.stubGlobal('fetch', stubRecentAndProjects([mob()], [project()]))
    const router = testRouter()
    const wrapper = mount(HomeView, { global: { plugins: [router] } })
    await flushPromises()

    const links = wrapper.findAll('.home-section-header__link')
    expect(links).toHaveLength(2)
    expect(links[0]!.attributes('href')).toBe('/projects')
    expect(links[1]!.attributes('href')).toBe('/projects')
  })

  it('clic en un mob reciente navega usando SU projectId, no el de otro mob/proyecto', async () => {
    vi.stubGlobal('fetch', stubRecentAndProjects([mob({ id: 'm7', projectId: 'p-otro' })], [project({ id: 'p1' })]))
    const router = testRouter()
    const pushSpy = vi.spyOn(router, 'push')
    const wrapper = mount(HomeView, { global: { plugins: [router] } })
    await flushPromises()

    await wrapper.find('.recent-mob-card__open').trigger('click')

    expect(pushSpy).toHaveBeenCalledWith('/projects/p-otro/mobs/m7/edit')
  })

  it('clic en un proyecto reciente navega a su detalle', async () => {
    vi.stubGlobal('fetch', stubRecentAndProjects([], [project({ id: 'p9' })]))
    const router = testRouter()
    const pushSpy = vi.spyOn(router, 'push')
    const wrapper = mount(HomeView, { global: { plugins: [router] } })
    await flushPromises()

    await wrapper.find('.recent-project-card__open').trigger('click')

    expect(pushSpy).toHaveBeenCalledWith('/projects/p9')
  })

  it('"Empezar ahora" abre el selector de proyecto para el wizard IA', async () => {
    vi.stubGlobal('fetch', stubRecentAndProjects([], [project()]))
    const wrapper = mount(HomeView, { global: { plugins: [testRouter()] } })
    await flushPromises()

    await wrapper.findAll('button').find((b) => b.text().includes('Empezar ahora'))!.trigger('click')

    expect(wrapper.text()).toContain('Continuar')
  })

  it('"Crear proyecto" abre el modal y crea el proyecto', async () => {
    const fetchMock = vi.fn(async (url: RequestInfo | URL, init?: RequestInit) => {
      if (init?.method === 'POST') {
        return jsonResponse({ id: 'new-id', name: 'Tejedora', mobCount: 0, createdAt: '', updatedAt: '' }, 201)
      }
      if (String(url).includes('/mobs/recent')) {
        return jsonResponse([])
      }
      // Proyectos NO vacío a propósito -- con la lista vacía, la sección
      // "Proyectos recientes" también muestra su propio botón "Crear
      // proyecto" (estado vacío) ANTES del modal en el DOM, y colisionaría
      // con el selector de abajo.
      return jsonResponse([project({ id: 'p1' })])
    })
    vi.stubGlobal('fetch', fetchMock)
    const router = testRouter()
    const pushSpy = vi.spyOn(router, 'push')
    const wrapper = mount(HomeView, { global: { plugins: [router] } })
    await flushPromises()

    await wrapper.findAll('button').find((b) => b.text().includes('Crear nuevo proyecto'))!.trigger('click')
    await wrapper.find('input').setValue('Tejedora')
    await wrapper.findAll('button').find((b) => b.text() === 'Crear proyecto')!.trigger('click')
    await flushPromises()

    expect(pushSpy).toHaveBeenCalledWith('/projects/new-id')
  })

  it('Eliminar un mob reciente + Confirmar llama al DELETE real', async () => {
    const fetchMock = vi.fn(async (url: RequestInfo | URL, init?: RequestInit) => {
      if (init?.method === 'DELETE') {
        return new Response(null, { status: 204 })
      }
      if (String(url).includes('/mobs/recent')) {
        return jsonResponse([mob({ id: 'm9' })])
      }
      return jsonResponse([project()])
    })
    vi.stubGlobal('fetch', fetchMock)
    const wrapper = mount(HomeView, { global: { plugins: [testRouter()] } })
    await flushPromises()

    await wrapper.find('.recent-mob-card .g-menu__trigger').trigger('click')
    await wrapper.findAll('[role="menuitem"]').find((i) => i.text() === 'Eliminar')!.trigger('click')
    // Escopado a `.app-dialog` -- ticket 071: el ítem "Eliminar" del menú ⋮ sigue montado animando su salida (mismo texto que este botón).
    await wrapper.get('.app-dialog').findAll('button').find((b) => b.text() === 'Eliminar')!.trigger('click')
    await flushPromises()

    expect(fetchMock.mock.calls.some((c) => (c[1] as RequestInit | undefined)?.method === 'DELETE' && String(c[0]).includes('/api/mobs/m9'))).toBe(true)
  })

  it('Duplicar un proyecto reciente llama al endpoint real', async () => {
    const fetchMock = vi.fn(async (url: RequestInfo | URL, init?: RequestInit) => {
      if (init?.method === 'POST' && String(url).includes('/duplicate')) {
        return jsonResponse({ id: 'p2', name: 'Galgoth (copia)', mobCount: 0, createdAt: '', updatedAt: '' }, 201)
      }
      if (String(url).includes('/mobs/recent')) {
        return jsonResponse([])
      }
      return jsonResponse([project({ id: 'p1' })])
    })
    vi.stubGlobal('fetch', fetchMock)
    const wrapper = mount(HomeView, { global: { plugins: [testRouter()] } })
    await flushPromises()

    await wrapper.find('.recent-project-card .g-menu__trigger').trigger('click')
    await wrapper.findAll('[role="menuitem"]').find((i) => i.text().includes('Duplicar'))!.trigger('click')
    await flushPromises()

    expect(fetchMock.mock.calls.some((c) => String(c[0]).includes('/duplicate'))).toBe(true)
  })

  it('ticket 071 -- sidebar: "Mis proyectos" navega a /projects', async () => {
    vi.stubGlobal('fetch', stubRecentAndProjects([], []))
    const router = testRouter()
    const pushSpy = vi.spyOn(router, 'push')
    const wrapper = mount(HomeView, { global: { plugins: [router] } })
    await flushPromises()

    await wrapper.findAll('.g-sidebar__item')[1]!.trigger('click')

    expect(pushSpy).toHaveBeenCalledWith('/projects')
  })
})
