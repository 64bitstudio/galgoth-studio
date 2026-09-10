import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'
import { afterEach, beforeAll, describe, expect, it, vi } from 'vitest'
import ProjectDetail from '../ProjectDetail.vue'
import type { MobSummary } from '../mobsApi'

// jsdom no implementa HTMLDialogElement.showModal()/close() -- este
// componente monta AddMobModal.vue, mismo motivo que en
// ProjectNameModal.spec.ts (ticket 021).
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

function mob(overrides: Partial<MobSummary> = {}): MobSummary {
  return { id: 'm1', name: 'Carcomido', baseType: 'humanoid', status: 'draft', thumbnailKey: null, updatedAt: '', ...overrides }
}

async function routerAt(id: string): Promise<Router> {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', component: { template: '<div />' } },
      { path: '/projects/:id', component: ProjectDetail },
      { path: '/projects/:projectId/mobs/new-ai', component: { template: '<div />' } },
      { path: '/projects/:projectId/mobs/:mobId/edit', component: { template: '<div />' } },
      { path: '/projects/:projectId/mobs/:mobId/export', component: { template: '<div />' } },
    ],
  })
  await router.push(`/projects/${id}`)
  return router
}

/** GET /api/projects/{id} y GET /api/projects/{id}/mobs en cualquier orden -- Promise.all no garantiza cuál llega primero. */
function stubProjectAndMobs(project: object, mobs: MobSummary[]): ReturnType<typeof vi.fn> {
  return vi.fn<typeof fetch>(async (url) => {
    if (String(url).includes('/mobs')) {
      return jsonResponse(mobs)
    }
    return jsonResponse(project)
  })
}

describe('ProjectDetail.vue', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('carga y muestra el nombre del proyecto, el conteo y el grid de mobs', async () => {
    vi.stubGlobal(
      'fetch',
      stubProjectAndMobs({ id: 'p1', name: 'Galgoth', mobCount: 2, createdAt: '', updatedAt: '' }, [
        mob({ id: 'm1', name: 'Carcomido' }),
        mob({ id: 'm2', name: 'Augur' }),
      ]),
    )
    const wrapper = mount(ProjectDetail, { global: { plugins: [await routerAt('p1')] } })
    await flushPromises()

    expect(wrapper.text()).toContain('Galgoth')
    expect(wrapper.text()).toContain('2 criaturas')
    expect(wrapper.text()).toContain('Carcomido')
    expect(wrapper.text()).toContain('Augur')
  })

  it('el buscador filtra el grid por nombre, AC #3', async () => {
    vi.stubGlobal(
      'fetch',
      stubProjectAndMobs({ id: 'p1', name: 'Galgoth', mobCount: 2, createdAt: '', updatedAt: '' }, [
        mob({ id: 'm1', name: 'Carcomido' }),
        mob({ id: 'm2', name: 'Augur' }),
      ]),
    )
    const wrapper = mount(ProjectDetail, { global: { plugins: [await routerAt('p1')] } })
    await flushPromises()

    await wrapper.find('.project-detail__search').setValue('carc')

    expect(wrapper.text()).toContain('Carcomido')
    expect(wrapper.text()).not.toContain('Augur')
  })

  it('el grid muestra el estado (Ready/In progress/Draft) por cada mob, AC #4', async () => {
    vi.stubGlobal(
      'fetch',
      stubProjectAndMobs({ id: 'p1', name: 'Galgoth', mobCount: 3, createdAt: '', updatedAt: '' }, [
        mob({ id: 'm1', name: 'Uno', status: 'draft' }),
        mob({ id: 'm2', name: 'Dos', status: 'in_progress' }),
        mob({ id: 'm3', name: 'Tres', status: 'ready' }),
      ]),
    )
    const wrapper = mount(ProjectDetail, { global: { plugins: [await routerAt('p1')] } })
    await flushPromises()

    expect(wrapper.text()).toContain('Draft')
    expect(wrapper.text()).toContain('In progress')
    expect(wrapper.text()).toContain('Ready')
  })

  it('un proyecto inexistente muestra un mensaje de error explícito', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn<typeof fetch>(async () => jsonResponse({ error: 'PROJECT_NOT_FOUND', message: 'No existe ese proyecto.' }, 404)),
    )
    const wrapper = mount(ProjectDetail, { global: { plugins: [await routerAt('no-existe')] } })
    await flushPromises()

    expect(wrapper.text()).toContain('No existe ese proyecto.')
  })

  describe('Agregar mob (HU-03)', () => {
    it('clic en "+ Agregar mob" abre el modal', async () => {
      vi.stubGlobal('fetch', stubProjectAndMobs({ id: 'p1', name: 'Galgoth', mobCount: 0, createdAt: '', updatedAt: '' }, []))
      const wrapper = mount(ProjectDetail, { global: { plugins: [await routerAt('p1')] } })
      await flushPromises()

      await wrapper.findAll('button').find((b) => b.text() === 'Agregar mob')!.trigger('click')

      expect(wrapper.findComponent({ name: 'AddMobModal' }).exists()).toBe(true)
    })

    it('clic en la tarjeta "+ Nuevo mob" del grid también abre el modal', async () => {
      vi.stubGlobal('fetch', stubProjectAndMobs({ id: 'p1', name: 'Galgoth', mobCount: 0, createdAt: '', updatedAt: '' }, []))
      const wrapper = mount(ProjectDetail, { global: { plugins: [await routerAt('p1')] } })
      await flushPromises()

      await wrapper.find('.project-detail__new-mob-cta').trigger('click')

      expect(wrapper.findComponent({ name: 'AddMobModal' }).exists()).toBe(true)
    })

    it('confirmar en el modal crea el mob y lo refleja en el grid con estado Draft, AC #1 y #2', async () => {
      let created = false
      vi.stubGlobal(
        'fetch',
        vi.fn<typeof fetch>(async (url, init) => {
          if (init?.method === 'POST') {
            created = true
            return jsonResponse(
              { id: 'new-id', name: 'Nuevo', baseType: 'humanoid', status: 'draft', thumbnailKey: null, updatedAt: '' },
              201,
            )
          }
          if (String(url).includes('/mobs')) {
            return jsonResponse(created ? [mob({ id: 'new-id', name: 'Nuevo', status: 'draft' })] : [])
          }
          return jsonResponse({ id: 'p1', name: 'Galgoth', mobCount: created ? 1 : 0, createdAt: '', updatedAt: '' })
        }),
      )
      const wrapper = mount(ProjectDetail, { global: { plugins: [await routerAt('p1')] } })
      await flushPromises()

      await wrapper.findAll('button').find((b) => b.text() === 'Agregar mob')!.trigger('click')
      const modal = wrapper.findComponent({ name: 'AddMobModal' })
      await modal.find('input').setValue('Nuevo')
      await modal.findAll('button').find((b) => b.text() === 'Agregar mob')!.trigger('click')
      await flushPromises()

      expect(wrapper.findComponent({ name: 'AddMobModal' }).exists()).toBe(false) // el modal se cierra
      expect(wrapper.text()).toContain('Nuevo')
      expect(wrapper.text()).toContain('Draft')
    })
  })

  it('el enlace "Crear con IA" apunta al wizard del ticket 027, scoped al proyecto actual', async () => {
    vi.stubGlobal('fetch', stubProjectAndMobs({ id: 'p1', name: 'Galgoth', mobCount: 0, createdAt: '', updatedAt: '' }, []))
    const wrapper = mount(ProjectDetail, { global: { plugins: [await routerAt('p1')] } })
    await flushPromises()

    const link = wrapper.find('.project-detail__ai-mob')
    expect(link.text()).toBe('Crear con IA')
    expect(link.attributes('href')).toBe('/projects/p1/mobs/new-ai')
  })

  it('cada tarjeta de mob del grid navega a su ruta real de edición, ticket 034 (ticket 039: MobCard ahora emite open, no un router-link envolvente)', async () => {
    vi.stubGlobal(
      'fetch',
      stubProjectAndMobs({ id: 'p1', name: 'Galgoth', mobCount: 1, createdAt: '', updatedAt: '' }, [mob({ id: 'm1', name: 'Carcomido' })]),
    )
    const router = await routerAt('p1')
    const pushSpy = vi.spyOn(router, 'push')
    const wrapper = mount(ProjectDetail, { global: { plugins: [router] } })
    await flushPromises()

    await wrapper.find('.mob-card__open').trigger('click')

    expect(pushSpy).toHaveBeenCalledWith('/projects/p1/mobs/m1/edit')
  })

  it('"Mis proyectos" en el sidebar navega de vuelta al dashboard', async () => {
    vi.stubGlobal('fetch', stubProjectAndMobs({ id: 'p1', name: 'Galgoth', mobCount: 0, createdAt: '', updatedAt: '' }, []))
    const router = await routerAt('p1')
    const pushSpy = vi.spyOn(router, 'push')
    const wrapper = mount(ProjectDetail, { global: { plugins: [router] } })
    await flushPromises()

    await wrapper.findAll('.g-sidebar__item')[1]!.trigger('click')

    expect(pushSpy).toHaveBeenCalledWith('/projects')
  })

  describe('ticket 039 -- edición del nombre del proyecto desde el detalle', () => {
    it('el ícono junto al título abre el diálogo de renombrar pre-llenado y guarda el nuevo nombre', async () => {
      const fetchMock = vi.fn(async (url: RequestInfo | URL, init?: RequestInit) => {
        if (init?.method === 'PATCH') {
          return jsonResponse({ id: 'p1', name: 'Nuevo nombre', mobCount: 0, createdAt: '', updatedAt: '' })
        }
        if (String(url).includes('/mobs')) {
          return jsonResponse([])
        }
        return jsonResponse({ id: 'p1', name: 'Galgoth', mobCount: 0, createdAt: '', updatedAt: '' })
      })
      vi.stubGlobal('fetch', fetchMock)
      const wrapper = mount(ProjectDetail, { global: { plugins: [await routerAt('p1')] } })
      await flushPromises()

      await wrapper.find('button[aria-label="Renombrar proyecto"]').trigger('click')
      expect((wrapper.find('[aria-label="Nombre del proyecto"]').element as HTMLInputElement).value).toBe('Galgoth')

      await wrapper.find('[aria-label="Nombre del proyecto"]').setValue('Nuevo nombre')
      await wrapper.findAll('button').find((b) => b.text() === 'Guardar')!.trigger('click')
      await flushPromises()

      const patchCall = fetchMock.mock.calls.find((c) => (c[1] as RequestInit | undefined)?.method === 'PATCH')!
      expect(String(patchCall[0])).toContain('/api/projects/p1')
    })
  })

  describe('ticket 039 -- CRUD de mobs desde el grid', () => {
    it('Renombrar abre el diálogo pre-llenado y llama a PATCH /api/mobs/{mobId}', async () => {
      const fetchMock = vi.fn(async (url: RequestInfo | URL, init?: RequestInit) => {
        if (init?.method === 'PATCH') {
          return jsonResponse({ id: 'm1', name: 'Nuevo nombre', baseType: 'humanoid', status: 'draft', thumbnailKey: null, updatedAt: '' })
        }
        if (String(url).includes('/mobs') && !String(url).includes('/api/mobs/')) {
          return jsonResponse([mob()])
        }
        return jsonResponse({ id: 'p1', name: 'Galgoth', mobCount: 1, createdAt: '', updatedAt: '' })
      })
      vi.stubGlobal('fetch', fetchMock)
      const wrapper = mount(ProjectDetail, { global: { plugins: [await routerAt('p1')] } })
      await flushPromises()

      await wrapper.find('.g-menu__trigger').trigger('click')
      await wrapper.findAll('[role="menuitem"]').find((i) => i.text() === 'Renombrar')!.trigger('click')
      expect((wrapper.find('[aria-label="Nombre del mob"]').element as HTMLInputElement).value).toBe('Carcomido')

      await wrapper.find('[aria-label="Nombre del mob"]').setValue('Nuevo nombre')
      await wrapper.findAll('button').find((b) => b.text() === 'Guardar')!.trigger('click')
      await flushPromises()

      const patchCall = fetchMock.mock.calls.find((c) => (c[1] as RequestInit | undefined)?.method === 'PATCH')!
      expect(String(patchCall[0])).toContain('/api/mobs/m1')
    })

    it('Exportar navega a la pantalla real de exportación del mob', async () => {
      vi.stubGlobal('fetch', stubProjectAndMobs({ id: 'p1', name: 'Galgoth', mobCount: 1, createdAt: '', updatedAt: '' }, [mob()]))
      const router = await routerAt('p1')
      const pushSpy = vi.spyOn(router, 'push')
      const wrapper = mount(ProjectDetail, { global: { plugins: [router] } })
      await flushPromises()

      await wrapper.find('.g-menu__trigger').trigger('click')
      await wrapper.findAll('[role="menuitem"]').find((i) => i.text() === 'Exportar')!.trigger('click')

      expect(pushSpy).toHaveBeenCalledWith('/projects/p1/mobs/m1/export')
    })

    it('Eliminar muestra una confirmación diseñada, y confirmar llama a DELETE /api/mobs/{mobId} y refresca el grid', async () => {
      let mobsListCalls = 0
      const fetchMock = vi.fn(async (url: RequestInfo | URL, init?: RequestInit) => {
        if (init?.method === 'DELETE') {
          return new Response(null, { status: 204 })
        }
        if (String(url).includes('/mobs') && !String(url).includes('/api/mobs/')) {
          mobsListCalls += 1
          return jsonResponse(mobsListCalls === 1 ? [mob()] : [])
        }
        return jsonResponse({ id: 'p1', name: 'Galgoth', mobCount: 1, createdAt: '', updatedAt: '' })
      })
      vi.stubGlobal('fetch', fetchMock)
      const wrapper = mount(ProjectDetail, { global: { plugins: [await routerAt('p1')] } })
      await flushPromises()

      await wrapper.find('.g-menu__trigger').trigger('click')
      await wrapper.findAll('[role="menuitem"]').find((i) => i.text() === 'Eliminar')!.trigger('click')
      expect(wrapper.text()).toContain('¿Eliminar el mob "Carcomido"?')

      await wrapper.findAll('button').find((b) => b.text() === 'Eliminar')!.trigger('click')
      await flushPromises()

      expect(fetchMock.mock.calls.some((c) => (c[1] as RequestInit | undefined)?.method === 'DELETE')).toBe(true)
      expect(wrapper.text()).not.toContain('Carcomido')
    })
  })
})
