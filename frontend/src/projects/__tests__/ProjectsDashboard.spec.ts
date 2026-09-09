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
  return { id: 'p1', name: 'Galgoth', mobCount: 0, mobThumbnails: [], createdAt: '', updatedAt: '', ...overrides }
}

function testRouter(): Router {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', component: { template: '<div />' } },
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

  it('sin proyectos, muestra el estado vacío', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => jsonResponse([])))
    const wrapper = mount(ProjectsDashboard, { global: { plugins: [testRouter()] } })
    await flushPromises()

    expect(wrapper.text()).toContain('Todavía no tienes proyectos')
  })

  it('si la carga falla, muestra un mensaje de error explícito', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => jsonResponse({ message: 'Servidor caído' }, 500)))
    const wrapper = mount(ProjectsDashboard, { global: { plugins: [testRouter()] } })
    await flushPromises()

    expect(wrapper.text()).toContain('Servidor caído')
  })

  it('el CTA "Crear un mob con IA" está deshabilitado', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => jsonResponse([])))
    const wrapper = mount(ProjectsDashboard, { global: { plugins: [testRouter()] } })
    await flushPromises()

    const aiCta = wrapper.findAll('button').find((b) => b.text().includes('Crear un mob con IA'))!
    expect(aiCta.attributes('disabled')).toBeDefined()
  })

  describe('crear proyecto (HU-01)', () => {
    it('abre el modal, crea el proyecto y redirige a su detalle, AC #1', async () => {
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

      await wrapper.findAll('button').find((b) => b.text().includes('Proyecto vacío'))!.trigger('click')
      await wrapper.find('input').setValue('Tejedora')
      await wrapper.findAll('button').find((b) => b.text() === 'Crear proyecto')!.trigger('click')
      await flushPromises()

      expect(pushSpy).toHaveBeenCalledWith('/projects/new-id')
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

      await wrapper.findAll('button').find((b) => b.text().includes('Proyecto vacío'))!.trigger('click')
      await wrapper.find('input').setValue('x') // el frontend valida vacío; forzamos el rechazo del backend igual
      await wrapper.findAll('button').find((b) => b.text() === 'Crear proyecto')!.trigger('click')
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
      const renameItem = wrapper.findAll('[role="menuitem"]').find((i) => i.text().includes('Rename'))!
      await renameItem.trigger('click')

      expect((wrapper.find('input').element as HTMLInputElement).value).toBe('Galgoth')

      await wrapper.find('input').setValue('Nuevo nombre')
      await wrapper.findAll('button').find((b) => b.text() === 'Guardar')!.trigger('click')
      await flushPromises()

      const patchCall = fetchMock.mock.calls.find((c) => (c[1] as RequestInit | undefined)?.method === 'PATCH')!;
      expect(String(patchCall[0])).toContain('/api/projects/p1')
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
      const duplicateItem = wrapper.findAll('[role="menuitem"]').find((i) => i.text().includes('Duplicate'))!
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
      const deleteItem = wrapper.findAll('[role="menuitem"]').find((i) => i.text().includes('Delete'))!
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
      const deleteItem = wrapper.findAll('[role="menuitem"]').find((i) => i.text().includes('Delete'))!
      await deleteItem.trigger('click')
      await wrapper.findAll('button').find((b) => b.text() === 'Eliminar')!.trigger('click')
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

  it('elegir "Inicio" o "Mis proyectos" en el sidebar navega a /projects (ambos apuntan al mismo dashboard)', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => jsonResponse([])))
    const router = testRouter()
    const pushSpy = vi.spyOn(router, 'push')
    const wrapper = mount(ProjectsDashboard, { global: { plugins: [router] } })
    await flushPromises()

    const items = wrapper.findAll('.g-sidebar__item')
    await items[0]!.trigger('click') // Inicio
    await items[1]!.trigger('click') // Mis proyectos

    expect(pushSpy).toHaveBeenCalledWith('/projects')
    expect(pushSpy).toHaveBeenCalledTimes(2)
  })
})
