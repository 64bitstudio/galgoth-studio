import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest'
import ProjectsDashboard from '../ProjectsDashboard.vue'
import type { ProjectSummary } from '../projectsApi'

// jsdom no implementa HTMLDialogElement.showModal()/close() -- ver la
// misma nota en ProjectNameModal.spec.ts, este dashboard monta ese modal.
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

function summary(overrides: Partial<ProjectSummary> = {}): ProjectSummary {
  return { id: 'p1', name: 'Galgoth', description: null, mobCount: 0, mobThumbnails: [], status: 'draft', createdAt: '', updatedAt: '', ...overrides }
}

function testRouter(): Router {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', component: { template: '<div />' } },
      { path: '/projects', component: { template: '<div />' } },
      { path: '/projects/:id', component: { template: '<div />' } },
    ],
  })
}

describe('ProjectsDashboard.vue', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('al montarse, carga y muestra la lista de proyectos', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => jsonResponse([summary({ name: 'Carcomido' })])))
    const wrapper = mount(ProjectsDashboard, { global: { plugins: [testRouter()] } })
    await flushPromises()

    expect(wrapper.text()).toContain('Carcomido')
  })

  // Post-073 -- mismo breadcrumb que ProjectDetail.vue, pedido explícito del PO tras revisar el detalle en vivo.
  it('muestra el breadcrumb "Galgoth Studio > Mis proyectos"', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => jsonResponse([])))
    const wrapper = mount(ProjectsDashboard, { global: { plugins: [testRouter()] } })
    await flushPromises()

    const breadcrumb = wrapper.get('.projects-dashboard__breadcrumb')
    expect(breadcrumb.text()).toContain('Galgoth Studio')
    expect(breadcrumb.text()).toContain('Mis proyectos')
    expect(breadcrumb.get('a').attributes('href')).toBe('/')
  })

  // Ticket 072 -- sin proyectos, la card punteada "Nuevo proyecto" ES el estado vacío (sin mensaje aparte).
  it('sin proyectos, muestra solo la card "Nuevo proyecto"', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => jsonResponse([])))
    const wrapper = mount(ProjectsDashboard, { global: { plugins: [testRouter()] } })
    await flushPromises()

    expect(wrapper.find('.project-card').exists()).toBe(false)
    expect(wrapper.find('.projects-dashboard__new-card').exists()).toBe(true)
    expect(wrapper.text()).toContain('Crea un nuevo mundo de mobs desde cero')
  })

  it('si la carga falla, muestra un mensaje de error explícito', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => jsonResponse({ message: 'Servidor caído' }, 500)))
    const wrapper = mount(ProjectsDashboard, { global: { plugins: [testRouter()] } })
    await flushPromises()

    expect(wrapper.text()).toContain('Servidor caído')
  })

  // Ticket 072 -- buscador (nuevo en este rediseño), client-side, mismo criterio que el buscador de mobs de ProjectDetail.vue.
  describe('buscador de proyectos', () => {
    it('filtra la grilla por nombre', async () => {
      vi.stubGlobal('fetch', vi.fn(async () => jsonResponse([summary({ id: 'p1', name: 'Galgoth' }), summary({ id: 'p2', name: 'Bosque Lúgubre' })])))
      const wrapper = mount(ProjectsDashboard, { global: { plugins: [testRouter()] } })
      await flushPromises()

      await wrapper.get('.projects-dashboard__search-input').setValue('bosque')

      const gridText = wrapper.get('.projects-dashboard__grid').text()
      expect(gridText).toContain('Bosque Lúgubre')
      expect(gridText).not.toContain('Galgoth')
    })

    it('sin coincidencias, muestra un mensaje explícito (la card "Nuevo proyecto" sigue disponible)', async () => {
      vi.stubGlobal('fetch', vi.fn(async () => jsonResponse([summary({ name: 'Galgoth' })])))
      const wrapper = mount(ProjectsDashboard, { global: { plugins: [testRouter()] } })
      await flushPromises()

      await wrapper.get('.projects-dashboard__search-input').setValue('no existe')

      expect(wrapper.text()).toContain('Ningún proyecto coincide con "no existe"')
      expect(wrapper.find('.project-card').exists()).toBe(false)
      expect(wrapper.find('.projects-dashboard__new-card').exists()).toBe(true)
    })
  })

  describe('crear proyecto (HU-01)', () => {
    it('el botón "+ Nuevo proyecto" del encabezado abre el modal, crea el proyecto y redirige a su detalle, AC #1', async () => {
      const fetchMock = vi.fn(async (_url: RequestInfo | URL, init?: RequestInit) => {
        if (init?.method === 'POST') {
          return jsonResponse({ id: 'new-id', name: 'Tejedora', mobCount: 0, createdAt: '', updatedAt: '' }, 201)
        }
        return jsonResponse([])
      })
      vi.stubGlobal('fetch', fetchMock)
      const router = testRouter()
      const pushSpy = vi.spyOn(router, 'push')
      const wrapper = mount(ProjectsDashboard, { global: { plugins: [router] } })
      await flushPromises()

      await wrapper.get('.projects-dashboard__new-btn').trigger('click')
      await wrapper.get('.app-dialog').get('input').setValue('Tejedora')
      await wrapper.get('.app-dialog').findAll('button').find((b) => b.text() === 'Crear proyecto')!.trigger('click')
      await flushPromises()

      expect(pushSpy).toHaveBeenCalledWith('/projects/new-id')
    })

    it('la card punteada "Nuevo proyecto" de la grilla abre el mismo modal', async () => {
      vi.stubGlobal('fetch', vi.fn(async () => jsonResponse([])))
      const wrapper = mount(ProjectsDashboard, { global: { plugins: [testRouter()] } })
      await flushPromises()

      await wrapper.get('.projects-dashboard__new-card').trigger('click')

      expect(wrapper.find('.app-dialog').exists()).toBe(true)
    })

    it('un nombre rechazado por el backend muestra el error sin cerrar el flujo', async () => {
      const fetchMock = vi.fn(async (_url: RequestInfo | URL, init?: RequestInit) => {
        if (init?.method === 'POST') {
          return jsonResponse({ error: 'INVALID_PROJECT_NAME', message: 'El nombre no puede estar vacío.' }, 400)
        }
        return jsonResponse([])
      })
      vi.stubGlobal('fetch', fetchMock)
      const wrapper = mount(ProjectsDashboard, { global: { plugins: [testRouter()] } })
      await flushPromises()

      await wrapper.get('.projects-dashboard__new-btn').trigger('click')
      await wrapper.get('.app-dialog').get('input').setValue('x') // el frontend valida vacío; forzamos el rechazo del backend igual
      await wrapper.get('.app-dialog').findAll('button').find((b) => b.text() === 'Crear proyecto')!.trigger('click')
      await flushPromises()

      expect(wrapper.text()).toContain('El nombre no puede estar vacío.')
    })
  })

  describe('acciones de la tarjeta (HU-02)', () => {
    beforeEach(() => {
      vi.stubGlobal('fetch', vi.fn(async () => jsonResponse([summary()])))
    })

    it('Rename abre el modal pre-llenado y guarda el nuevo nombre', async () => {
      let listCalls = 0
      const fetchMock = vi.fn(async (_url: RequestInfo | URL, init?: RequestInit) => {
        if (init?.method === 'PATCH') {
          return jsonResponse({ id: 'p1', name: 'Nuevo nombre', mobCount: 0, createdAt: '', updatedAt: '' })
        }
        listCalls += 1
        return jsonResponse([summary({ name: listCalls === 1 ? 'Galgoth' : 'Nuevo nombre' })])
      })
      vi.stubGlobal('fetch', fetchMock)
      const wrapper = mount(ProjectsDashboard, { global: { plugins: [testRouter()] } })
      await flushPromises()

      await wrapper.find('.g-menu__trigger').trigger('click')
      const renameItem = wrapper.findAll('[role="menuitem"]').find((i) => i.text().includes('Renombrar'))!
      await renameItem.trigger('click')

      expect((wrapper.get('.app-dialog').get('input').element as HTMLInputElement).value).toBe('Galgoth')

      await wrapper.get('.app-dialog').get('input').setValue('Nuevo nombre')
      await wrapper.findAll('button').find((b) => b.text() === 'Guardar')!.trigger('click')
      await flushPromises()

      const patchCall = fetchMock.mock.calls.find((c) => (c[1] as RequestInit | undefined)?.method === 'PATCH')!;
      expect(String(patchCall[0])).toContain('/api/projects/p1')
      expect(JSON.parse((patchCall[1] as RequestInit).body as string)).toEqual({ name: 'Nuevo nombre', description: null })
    })

    // Ticket 073 -- la descripción existente se precarga en el modal y viaja intacta en el PATCH si no se toca.
    it('Rename precarga la descripción existente y la reenvía sin cambios', async () => {
      const fetchMock = vi.fn(async (_url: RequestInfo | URL, init?: RequestInit) => {
        if (init?.method === 'PATCH') {
          return jsonResponse({ id: 'p1', name: 'Galgoth', description: 'Un bosque maldito.', mobCount: 0, createdAt: '', updatedAt: '' })
        }
        return jsonResponse([summary({ description: 'Un bosque maldito.' })])
      })
      vi.stubGlobal('fetch', fetchMock)
      const wrapper = mount(ProjectsDashboard, { global: { plugins: [testRouter()] } })
      await flushPromises()

      await wrapper.find('.g-menu__trigger').trigger('click')
      const renameItem = wrapper.findAll('[role="menuitem"]').find((i) => i.text().includes('Renombrar'))!
      await renameItem.trigger('click')

      expect((wrapper.get('.app-dialog').get('textarea').element as HTMLTextAreaElement).value).toBe('Un bosque maldito.')

      await wrapper.findAll('button').find((b) => b.text() === 'Guardar')!.trigger('click')
      await flushPromises()

      const patchCall = fetchMock.mock.calls.find((c) => (c[1] as RequestInit | undefined)?.method === 'PATCH')!;
      expect(JSON.parse((patchCall[1] as RequestInit).body as string)).toEqual({ name: 'Galgoth', description: 'Un bosque maldito.' })
    })

    it('Duplicate llama al endpoint y refresca la lista', async () => {
      const fetchMock = vi.fn(async (url: RequestInfo | URL, init?: RequestInit) => {
        if (init?.method === 'POST' && String(url).includes('/duplicate')) {
          return jsonResponse({ id: 'p2', name: 'Galgoth (copia)', mobCount: 0, createdAt: '', updatedAt: '' }, 201)
        }
        return jsonResponse([summary()])
      })
      vi.stubGlobal('fetch', fetchMock)
      const wrapper = mount(ProjectsDashboard, { global: { plugins: [testRouter()] } })
      await flushPromises()

      await wrapper.find('.g-menu__trigger').trigger('click')
      const duplicateItem = wrapper.findAll('[role="menuitem"]').find((i) => i.text().includes('Duplicar'))!
      await duplicateItem.trigger('click')
      await flushPromises()

      expect(fetchMock.mock.calls.some((c) => String(c[0]).includes('/duplicate'))).toBe(true)
    })

    it('Delete muestra una advertencia explícita antes de confirmar, y Cancelar no elimina nada', async () => {
      const fetchMock = vi.fn<typeof fetch>(async () => jsonResponse([summary()]))
      vi.stubGlobal('fetch', fetchMock)
      const wrapper = mount(ProjectsDashboard, { global: { plugins: [testRouter()] } })
      await flushPromises()

      await wrapper.find('.g-menu__trigger').trigger('click')
      const deleteItem = wrapper.findAll('[role="menuitem"]').find((i) => i.text().includes('Eliminar'))!
      await deleteItem.trigger('click')

      expect(wrapper.text()).toContain('¿Eliminar el proyecto "Galgoth"?')

      await wrapper.findAll('button').find((b) => b.text() === 'Cancelar')!.trigger('click')

      expect(fetchMock.mock.calls.some((c) => (c[1] as RequestInit | undefined)?.method === 'DELETE')).toBe(false)
    })

    it('Delete + Confirmar llama al DELETE y refresca la lista', async () => {
      const fetchMock = vi.fn(async (_url: RequestInfo | URL, init?: RequestInit) => {
        if (init?.method === 'DELETE') {
          return new Response(null, { status: 204 })
        }
        return jsonResponse([summary()])
      })
      vi.stubGlobal('fetch', fetchMock)
      const wrapper = mount(ProjectsDashboard, { global: { plugins: [testRouter()] } })
      await flushPromises()

      await wrapper.find('.g-menu__trigger').trigger('click')
      const deleteItem = wrapper.findAll('[role="menuitem"]').find((i) => i.text().includes('Eliminar'))!
      await deleteItem.trigger('click')
      // Escopado a `.app-dialog` -- ticket 071: el ítem "Eliminar" del menú ⋮ sigue montado animando su salida (mismo texto que este botón).
      await wrapper.get('.app-dialog').findAll('button').find((b) => b.text() === 'Eliminar')!.trigger('click')
      await flushPromises()

      expect(fetchMock.mock.calls.some((c) => (c[1] as RequestInit | undefined)?.method === 'DELETE')).toBe(true)
    })
  })

  it('clic en una tarjeta navega a /projects/{id}', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => jsonResponse([summary({ id: 'p9' })])))
    const router = testRouter()
    const pushSpy = vi.spyOn(router, 'push')
    const wrapper = mount(ProjectsDashboard, { global: { plugins: [router] } })
    await flushPromises()

    await wrapper.find('.project-card__open').trigger('click')

    expect(pushSpy).toHaveBeenCalledWith('/projects/p9')
  })

  it('ticket 071 -- "Inicio" en el sidebar navega a "/" (ya no es sinónimo de "Mis proyectos"), "Mis proyectos" navega a /projects', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => jsonResponse([])))
    const router = testRouter()
    const pushSpy = vi.spyOn(router, 'push')
    const wrapper = mount(ProjectsDashboard, { global: { plugins: [router] } })
    await flushPromises()

    const items = wrapper.findAll('.g-sidebar__item')
    await items[0]!.trigger('click') // Inicio
    await items[1]!.trigger('click') // Mis proyectos

    expect(pushSpy).toHaveBeenNthCalledWith(1, '/')
    expect(pushSpy).toHaveBeenNthCalledWith(2, '/projects')
    expect(pushSpy).toHaveBeenCalledTimes(2)
  })
})
