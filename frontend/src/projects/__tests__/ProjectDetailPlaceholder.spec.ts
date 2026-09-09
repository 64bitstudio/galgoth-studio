import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import ProjectDetailPlaceholder from '../ProjectDetailPlaceholder.vue'

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

async function routerAt(id: string): Promise<Router> {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/projects/:id', component: ProjectDetailPlaceholder }],
  })
  await router.push(`/projects/${id}`)
  return router
}

describe('ProjectDetailPlaceholder.vue', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('muestra "Cargando…" antes de que la respuesta llegue', async () => {
    vi.stubGlobal('fetch', vi.fn(() => new Promise(() => {})))
    const router = await routerAt('p1')
    const wrapper = mount(ProjectDetailPlaceholder, { global: { plugins: [router] } })

    expect(wrapper.text()).toContain('Cargando')
  })

  it('carga y muestra el nombre y el conteo de mobs del proyecto real, AC #1 (redirección a un detalle real)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => jsonResponse({ id: 'p1', name: 'Tejedora', mobCount: 3, createdAt: '', updatedAt: '' })),
    )
    const router = await routerAt('p1')
    const wrapper = mount(ProjectDetailPlaceholder, { global: { plugins: [router] } })
    await flushPromises()

    expect(wrapper.text()).toContain('Tejedora')
    expect(wrapper.text()).toContain('3 mob(s)')
  })

  it('un proyecto inexistente muestra un mensaje de error explícito', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => jsonResponse({ error: 'PROJECT_NOT_FOUND', message: 'No existe ese proyecto.' }, 404)),
    )
    const router = await routerAt('no-existe')
    const wrapper = mount(ProjectDetailPlaceholder, { global: { plugins: [router] } })
    await flushPromises()

    expect(wrapper.text()).toContain('No existe ese proyecto.')
  })

  it('"Mis proyectos" en el sidebar navega de vuelta al dashboard', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => jsonResponse({ id: 'p1', name: 'Tejedora', mobCount: 0, createdAt: '', updatedAt: '' })))
    const router = await routerAt('p1')
    const pushSpy = vi.spyOn(router, 'push')
    const wrapper = mount(ProjectDetailPlaceholder, { global: { plugins: [router] } })
    await flushPromises()

    await wrapper.findAll('.g-sidebar__item')[1]!.trigger('click')

    expect(pushSpy).toHaveBeenCalledWith('/projects')
  })
})
