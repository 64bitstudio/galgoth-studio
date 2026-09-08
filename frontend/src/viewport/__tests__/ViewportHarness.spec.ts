import { flushPromises, shallowMount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { MobProjectModel } from '../../domain/MobProjectModel'

// ViewportHarness.vue importa ThreeViewport.vue -> ThreeViewportService.ts,
// que construye el singleton (new WebGLRenderer(...)) al cargar el módulo
// -- mismo motivo que en ThreeViewport.spec.ts, necesario aunque este test
// use shallowMount (el grafo de módulos se evalúa igual antes de montar).
vi.mock('three', async (importOriginal) => {
  const actual = await importOriginal<typeof import('three')>()
  class FakeWebGLRenderer {
    domElement = document.createElement('canvas')
    setSize = vi.fn()
    render = vi.fn()
  }
  return { ...actual, WebGLRenderer: FakeWebGLRenderer }
})

const { default: ViewportHarness } = await import('../ViewportHarness.vue')

function fixtureModel(): MobProjectModel {
  return {
    mobId: 'dev-fixture-carcomido',
    projectId: 'dev-fixture-project',
    name: 'Carcomido_Cuboids',
    baseType: 'humanoid',
    units: 'minecraft_pixels',
    bones: [],
    cuboids: [],
    texture: { width: 64, height: 64, storageKey: null },
    uv: { textureWidth: 64, textureHeight: 64, regions: [] },
    animations: [],
    exportSettings: { preferredFormatVersion: 'v4' },
    referenceImages: [],
  }
}

describe('ViewportHarness.vue', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('mientras carga el fixture, muestra el estado de carga', () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(() => new Promise(() => {})), // nunca resuelve -- deja el componente en estado "cargando"
    )

    const wrapper = shallowMount(ViewportHarness)

    expect(wrapper.text()).toContain('Cargando fixture')
  })

  it('cuando el fixture carga bien, muestra la etiqueta con el nombre del mob y monta el viewport', async () => {
    const model = fixtureModel()
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => new Response(JSON.stringify(model), { status: 200 })),
    )

    const wrapper = shallowMount(ViewportHarness)
    await flushPromises()

    expect(wrapper.text()).toContain('Carcomido_Cuboids')
    expect(wrapper.findComponent({ name: 'ThreeViewport' }).exists()).toBe(true)
  })

  it('cuando el fetch del fixture falla (HTTP no-ok), muestra un mensaje de error explícito', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => new Response(null, { status: 404 })),
    )

    const wrapper = shallowMount(ViewportHarness)
    await flushPromises()

    expect(wrapper.text()).toContain('404')
    expect(wrapper.findComponent({ name: 'ThreeViewport' }).exists()).toBe(false)
  })
})
