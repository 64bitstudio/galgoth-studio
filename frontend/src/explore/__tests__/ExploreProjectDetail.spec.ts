import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import ExploreProjectDetail from '../ExploreProjectDetail.vue'

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

async function routerAt(id: string): Promise<Router> {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', component: { template: '<div />' } },
      { path: '/projects', component: { template: '<div />' } },
      { path: '/explore', component: { template: '<div />' } },
      { path: '/explore/:id', component: ExploreProjectDetail },
    ],
  })
  await router.push(`/explore/${id}`)
  return router
}

describe('ExploreProjectDetail.vue', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('carga y muestra el nombre, descripción, dueño y mobs del proyecto público', async () => {
    const fetchMock = vi.fn<typeof fetch>(async (url) => {
      if (String(url).includes('/mobs')) {
        return jsonResponse([{ id: 'm1', name: 'Augur', baseType: 'quadruped', status: 'ready', thumbnailKey: null, updatedAt: '' }])
      }
      return jsonResponse({ id: 'p1', name: 'Bosque maldito', description: 'Un lugar oscuro.', mobCount: 1, visibility: 'PUBLIC', ownerDisplayName: 'Ada Lovelace', createdAt: '', updatedAt: '' })
    })
    vi.stubGlobal('fetch', fetchMock)
    const wrapper = mount(ExploreProjectDetail, { global: { plugins: [await routerAt('p1')] } })
    await flushPromises()

    expect(wrapper.text()).toContain('Bosque maldito')
    expect(wrapper.text()).toContain('Un lugar oscuro.')
    expect(wrapper.text()).toContain('por Ada Lovelace')
    expect(wrapper.text()).toContain('Augur')
  })

  // Ticket 088 -- AC: modo estrictamente lectura, sin ninguna acción de edición visible.
  it('no muestra ninguna acción de edición (sin renombrar, sin menú ⋮, sin "Agregar mob"/"Crear con IA")', async () => {
    const fetchMock = vi.fn<typeof fetch>(async (url) => {
      if (String(url).includes('/mobs')) {
        return jsonResponse([])
      }
      return jsonResponse({ id: 'p1', name: 'Bosque maldito', description: null, mobCount: 0, visibility: 'PUBLIC', ownerDisplayName: null, createdAt: '', updatedAt: '' })
    })
    vi.stubGlobal('fetch', fetchMock)
    const wrapper = mount(ExploreProjectDetail, { global: { plugins: [await routerAt('p1')] } })
    await flushPromises()

    expect(wrapper.find('[role="menuitem"]').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('Agregar mob')
    expect(wrapper.text()).not.toContain('Crear con IA')
    expect(wrapper.find('button[aria-label="Editar proyecto"]').exists()).toBe(false)
  })

  it('sin mobs, muestra el estado vacío explícito', async () => {
    const fetchMock = vi.fn<typeof fetch>(async (url) => {
      if (String(url).includes('/mobs')) {
        return jsonResponse([])
      }
      return jsonResponse({ id: 'p1', name: 'Bosque maldito', description: null, mobCount: 0, visibility: 'PUBLIC', ownerDisplayName: null, createdAt: '', updatedAt: '' })
    })
    vi.stubGlobal('fetch', fetchMock)
    const wrapper = mount(ExploreProjectDetail, { global: { plugins: [await routerAt('p1')] } })
    await flushPromises()

    expect(wrapper.text()).toContain('Este proyecto todavía no tiene mobs.')
  })

  // Ticket 088 -- AC: un proyecto privado (o inexistente) nunca es alcanzable vía /explore/:id -- el frontend muestra "no encontrado", no el texto crudo del backend.
  it('un proyecto inexistente o privado muestra "no encontrado" (nunca el mensaje crudo del backend)', async () => {
    vi.stubGlobal('fetch', vi.fn<typeof fetch>(async () => jsonResponse({ error: 'PROJECT_NOT_FOUND', message: 'No existe ningún proyecto con id p1.' }, 404)))
    const wrapper = mount(ExploreProjectDetail, { global: { plugins: [await routerAt('p1')] } })
    await flushPromises()

    expect(wrapper.text()).toContain('Este proyecto no existe o ya no está disponible.')
    expect(wrapper.text()).not.toContain('No existe ningún proyecto con id p1.')
  })

  it('un error real de servidor muestra un mensaje genérico distinto del de "no encontrado"', async () => {
    vi.stubGlobal('fetch', vi.fn<typeof fetch>(async () => jsonResponse({ error: 'INTERNAL', message: 'Ocurrió un error inesperado.' }, 500)))
    const wrapper = mount(ExploreProjectDetail, { global: { plugins: [await routerAt('p1')] } })
    await flushPromises()

    expect(wrapper.text()).toContain('No se pudo cargar el proyecto público.')
  })
})
