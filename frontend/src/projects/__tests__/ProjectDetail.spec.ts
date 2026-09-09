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

      await wrapper.findAll('button').find((b) => b.text() === '+ Agregar mob')!.trigger('click')

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

      await wrapper.findAll('button').find((b) => b.text() === '+ Agregar mob')!.trigger('click')
      const modal = wrapper.findComponent({ name: 'AddMobModal' })
      await modal.find('input').setValue('Nuevo')
      await modal.findAll('button').find((b) => b.text() === 'Agregar mob')!.trigger('click')
      await flushPromises()

      expect(wrapper.findComponent({ name: 'AddMobModal' }).exists()).toBe(false) // el modal se cierra
      expect(wrapper.text()).toContain('Nuevo')
      expect(wrapper.text()).toContain('Draft')
    })
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
})
