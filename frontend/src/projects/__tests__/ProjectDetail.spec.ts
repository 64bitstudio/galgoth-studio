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

  it('el grid muestra el estado (Listo/En progreso/Draft) por cada mob, AC #4', async () => {
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
    expect(wrapper.text()).toContain('En progreso')
    expect(wrapper.text()).toContain('Listo')
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

      await wrapper.find('button[aria-label="Editar proyecto"]').trigger('click')
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

      // aria-label específico del mob -- ticket 073: ya hay un segundo `GMenu` en la pantalla (el de acciones del proyecto), `.g-menu__trigger` a secas es ambiguo.
      await wrapper.find('[aria-label="Acciones de Carcomido"]').trigger('click')
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

      await wrapper.find('[aria-label="Acciones de Carcomido"]').trigger('click')
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

      await wrapper.find('[aria-label="Acciones de Carcomido"]').trigger('click')
      await wrapper.findAll('[role="menuitem"]').find((i) => i.text() === 'Eliminar')!.trigger('click')
      expect(wrapper.text()).toContain('¿Eliminar el mob "Carcomido"?')

      // Escopado a `.app-dialog` -- ticket 071: el ítem "Eliminar" del menú ⋮ sigue montado animando su salida (mismo texto que este botón).
      await wrapper.get('.app-dialog').findAll('button').find((b) => b.text() === 'Eliminar')!.trigger('click')
      await flushPromises()

      expect(fetchMock.mock.calls.some((c) => (c[1] as RequestInit | undefined)?.method === 'DELETE')).toBe(true)
      expect(wrapper.text()).not.toContain('Carcomido')
    })
  })

  // Ticket 073 -- rediseño del detalle de proyecto: breadcrumb, descripción, menú ⋮ del PROYECTO, filtros.
  describe('ticket 073 -- rediseño del detalle de proyecto', () => {
    it('muestra el breadcrumb "Galgoth Studio > Mis proyectos > {nombre}"', async () => {
      vi.stubGlobal('fetch', stubProjectAndMobs({ id: 'p1', name: 'Galgoth', mobCount: 0, createdAt: '', updatedAt: '' }, []))
      const wrapper = mount(ProjectDetail, { global: { plugins: [await routerAt('p1')] } })
      await flushPromises()

      const breadcrumb = wrapper.get('.project-detail__breadcrumb')
      expect(breadcrumb.text()).toContain('Galgoth Studio')
      expect(breadcrumb.text()).toContain('Mis proyectos')
      expect(breadcrumb.text()).toContain('Galgoth')
    })

    it('con descripción, la muestra bajo la meta-línea', async () => {
      vi.stubGlobal(
        'fetch',
        stubProjectAndMobs({ id: 'p1', name: 'Galgoth', description: 'Un bosque maldito.', mobCount: 0, createdAt: '', updatedAt: '' }, []),
      )
      const wrapper = mount(ProjectDetail, { global: { plugins: [await routerAt('p1')] } })
      await flushPromises()

      expect(wrapper.get('.project-detail__description').text()).toBe('Un bosque maldito.')
    })

    it('sin descripción, no renderiza la línea de descripción', async () => {
      vi.stubGlobal('fetch', stubProjectAndMobs({ id: 'p1', name: 'Galgoth', description: null, mobCount: 0, createdAt: '', updatedAt: '' }, []))
      const wrapper = mount(ProjectDetail, { global: { plugins: [await routerAt('p1')] } })
      await flushPromises()

      expect(wrapper.find('.project-detail__description').exists()).toBe(false)
    })

    describe('menú ⋮ de acciones del PROYECTO (Duplicar/Eliminar)', () => {
      it('Duplicar llama al endpoint y navega al proyecto duplicado', async () => {
        const fetchMock = vi.fn(async (url: RequestInfo | URL, init?: RequestInit) => {
          if (init?.method === 'POST' && String(url).includes('/duplicate')) {
            return jsonResponse({ id: 'p2', name: 'Galgoth (copia)', mobCount: 0, createdAt: '', updatedAt: '' }, 201)
          }
          if (String(url).includes('/mobs')) {
            return jsonResponse([])
          }
          return jsonResponse({ id: 'p1', name: 'Galgoth', mobCount: 0, createdAt: '', updatedAt: '' })
        })
        vi.stubGlobal('fetch', fetchMock)
        const router = await routerAt('p1')
        const pushSpy = vi.spyOn(router, 'push')
        const wrapper = mount(ProjectDetail, { global: { plugins: [router] } })
        await flushPromises()

        await wrapper.get('[aria-label="Más acciones del proyecto"]').trigger('click')
        await wrapper.findAll('[role="menuitem"]').find((i) => i.text() === 'Duplicar')!.trigger('click')
        await flushPromises()

        expect(fetchMock.mock.calls.some((c) => String(c[0]).includes('/duplicate'))).toBe(true)
        expect(pushSpy).toHaveBeenCalledWith('/projects/p2')
      })

      it('Eliminar muestra una confirmación explícita, y confirmar llama a DELETE y navega a /projects', async () => {
        const fetchMock = vi.fn(async (url: RequestInfo | URL, init?: RequestInit) => {
          if (init?.method === 'DELETE') {
            return new Response(null, { status: 204 })
          }
          if (String(url).includes('/mobs')) {
            return jsonResponse([])
          }
          return jsonResponse({ id: 'p1', name: 'Galgoth', mobCount: 0, createdAt: '', updatedAt: '' })
        })
        vi.stubGlobal('fetch', fetchMock)
        const router = await routerAt('p1')
        const pushSpy = vi.spyOn(router, 'push')
        const wrapper = mount(ProjectDetail, { global: { plugins: [router] } })
        await flushPromises()

        await wrapper.get('[aria-label="Más acciones del proyecto"]').trigger('click')
        await wrapper.findAll('[role="menuitem"]').find((i) => i.text() === 'Eliminar')!.trigger('click')
        expect(wrapper.text()).toContain('¿Eliminar el proyecto "Galgoth"?')

        // Escopado a `.app-dialog` -- mismo motivo que el resto de la suite: el ítem "Eliminar" del menú ⋮ sigue montado animando su salida.
        await wrapper.get('.app-dialog').findAll('button').find((b) => b.text() === 'Eliminar')!.trigger('click')
        await flushPromises()

        expect(fetchMock.mock.calls.some((c) => (c[1] as RequestInit | undefined)?.method === 'DELETE')).toBe(true)
        expect(pushSpy).toHaveBeenCalledWith('/projects')
      })

      it('Cancelar en la confirmación de Eliminar no llama a DELETE', async () => {
        const fetchMock = vi.fn<typeof fetch>(async (url) => {
          if (String(url).includes('/mobs')) {
            return jsonResponse([])
          }
          return jsonResponse({ id: 'p1', name: 'Galgoth', mobCount: 0, createdAt: '', updatedAt: '' })
        })
        vi.stubGlobal('fetch', fetchMock)
        const wrapper = mount(ProjectDetail, { global: { plugins: [await routerAt('p1')] } })
        await flushPromises()

        await wrapper.get('[aria-label="Más acciones del proyecto"]').trigger('click')
        await wrapper.findAll('[role="menuitem"]').find((i) => i.text() === 'Eliminar')!.trigger('click')
        await wrapper.findAll('button').find((b) => b.text() === 'Cancelar')!.trigger('click')

        expect(fetchMock.mock.calls.some((c) => (c[1] as RequestInit | undefined)?.method === 'DELETE')).toBe(false)
      })
    })

    describe('filtros de Tipo/Estado y orden (GSelect, sin <select> nativo)', () => {
      function mobsFixture(): MobSummary[] {
        return [
          mob({ id: 'm1', name: 'Zeta', baseType: 'humanoid', status: 'draft', updatedAt: '2024-01-01T00:00:00.000Z' }),
          mob({ id: 'm2', name: 'Alfa', baseType: 'arachnid', status: 'ready', updatedAt: '2024-01-03T00:00:00.000Z' }),
        ]
      }

      it('el toolbar no usa ningún <select> nativo', async () => {
        vi.stubGlobal('fetch', stubProjectAndMobs({ id: 'p1', name: 'Galgoth', mobCount: 2, createdAt: '', updatedAt: '' }, mobsFixture()))
        const wrapper = mount(ProjectDetail, { global: { plugins: [await routerAt('p1')] } })
        await flushPromises()

        expect(wrapper.find('select').exists()).toBe(false)
        expect(wrapper.findAllComponents({ name: 'GSelect' })).toHaveLength(3)
      })

      it('filtrar por Tipo "Arácnido" deja solo los mobs de ese tipo', async () => {
        vi.stubGlobal('fetch', stubProjectAndMobs({ id: 'p1', name: 'Galgoth', mobCount: 2, createdAt: '', updatedAt: '' }, mobsFixture()))
        const wrapper = mount(ProjectDetail, { global: { plugins: [await routerAt('p1')] } })
        await flushPromises()

        const typeSelect = wrapper.findAllComponents({ name: 'GSelect' })[0]!
        await typeSelect.find('.g-select__trigger').trigger('click')
        await typeSelect.findAll('.g-select__option').find((o) => o.text() === 'Arácnido')!.trigger('click')

        expect(wrapper.get('.project-detail__grid').text()).toContain('Alfa')
        expect(wrapper.get('.project-detail__grid').text()).not.toContain('Zeta')
      })

      it('filtrar por Estado "Listo" deja solo los mobs listos', async () => {
        vi.stubGlobal('fetch', stubProjectAndMobs({ id: 'p1', name: 'Galgoth', mobCount: 2, createdAt: '', updatedAt: '' }, mobsFixture()))
        const wrapper = mount(ProjectDetail, { global: { plugins: [await routerAt('p1')] } })
        await flushPromises()

        const statusSelect = wrapper.findAllComponents({ name: 'GSelect' })[1]!
        await statusSelect.find('.g-select__trigger').trigger('click')
        await statusSelect.findAll('.g-select__option').find((o) => o.text() === 'Listo')!.trigger('click')

        expect(wrapper.get('.project-detail__grid').text()).toContain('Alfa')
        expect(wrapper.get('.project-detail__grid').text()).not.toContain('Zeta')
      })

      it('ordenar "Nombre A-Z" ordena el grid alfabéticamente', async () => {
        vi.stubGlobal('fetch', stubProjectAndMobs({ id: 'p1', name: 'Galgoth', mobCount: 2, createdAt: '', updatedAt: '' }, mobsFixture()))
        const wrapper = mount(ProjectDetail, { global: { plugins: [await routerAt('p1')] } })
        await flushPromises()

        const sortSelect = wrapper.findAllComponents({ name: 'GSelect' })[2]!
        await sortSelect.find('.g-select__trigger').trigger('click')
        await sortSelect.findAll('.g-select__option').find((o) => o.text() === 'Nombre A-Z')!.trigger('click')

        const names = wrapper.findAll('.mob-card__name').map((n) => n.text())
        expect(names).toEqual(['Alfa', 'Zeta'])
      })

      it('sin coincidencias por los filtros (sin buscador activo), muestra un mensaje explícito distinto al del buscador', async () => {
        vi.stubGlobal('fetch', stubProjectAndMobs({ id: 'p1', name: 'Galgoth', mobCount: 2, createdAt: '', updatedAt: '' }, mobsFixture()))
        const wrapper = mount(ProjectDetail, { global: { plugins: [await routerAt('p1')] } })
        await flushPromises()

        const typeSelect = wrapper.findAllComponents({ name: 'GSelect' })[0]!
        await typeSelect.find('.g-select__trigger').trigger('click')
        await typeSelect.findAll('.g-select__option').find((o) => o.text() === 'Volador')!.trigger('click')

        expect(wrapper.text()).toContain('Ningún mob coincide con los filtros aplicados.')
      })
    })
  })
})
