import { flushPromises, shallowMount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useDraftModelStore } from '../draftModelStore'

// MobEditor.vue -> ThreeViewport.vue -> ThreeViewportService.ts construye
// el singleton (`new WebGLRenderer(...)`) al cargar el módulo -- mismo
// motivo/mismo patrón que ViewportHarness.spec.ts.
vi.mock('three', async (importOriginal) => {
  const actual = await importOriginal<typeof import('three')>()
  class FakeWebGLRenderer {
    domElement = document.createElement('canvas')
    setSize = vi.fn()
    render = vi.fn()
  }
  return { ...actual, WebGLRenderer: FakeWebGLRenderer }
})

// Ticket 068 -- "Guardar" sube a la fila superior compartida de este
// componente y delega en el hijo activo (`EditorToolbar.vue`/
// `TextureCanvas.vue`, vía `defineExpose`). Para probar esa delegación de
// punta a punta con los hijos REALES (no shallow-stubeados) se mockean los
// mismos módulos de guardado que ya mockean `EditorToolbar.spec.ts`/
// `TextureCanvas.spec.ts` -- `getDraft` sigue siendo el real (usa fetch,
// ya stubeado por test vía `vi.stubGlobal('fetch', ...)` más abajo).
vi.mock('../draftPersistenceApi', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../draftPersistenceApi')>()
  return { ...actual, saveRevision: vi.fn() }
})
vi.mock('../thumbnailApi', () => ({ uploadThumbnail: vi.fn() }))
vi.mock('../texture/textureFlush', () => ({ flushPaintedTexture: vi.fn(async (model) => model) }))
vi.mock('../../api/textureUploadApi', () => ({ downloadTexture: vi.fn().mockResolvedValue(null) }))

const { default: MobEditor } = await import('../MobEditor.vue')
const { saveRevision } = await import('../draftPersistenceApi')
const { threeViewportService } = await import('../../viewport/ThreeViewportService')

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

async function routerAt(projectId: string, mobId: string): Promise<Router> {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/projects/:projectId/mobs/:mobId/edit', component: MobEditor },
      { path: '/projects/:projectId/mobs/:mobId/export', component: { template: '<div>export</div>' } },
      { path: '/projects/:projectId/mobs/:mobId/texture/generate-ai', component: { template: '<div>generate-ai</div>' } },
    ],
  })
  await router.push(`/projects/${projectId}/mobs/${mobId}/edit`)
  return router
}

describe('MobEditor.vue', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('con un draft real, lo carga y muestra el editor, ticket 034 AC1', async () => {
    const draftModel = {
      mobId: 'mob-1',
      projectId: 'p1',
      name: 'Carcomido',
      baseType: 'humanoid' as const,
      units: 'minecraft_pixels' as const,
      bones: [],
      cuboids: [],
      texture: { width: 128, height: 128, storageKey: null },
      uv: { textureWidth: 128, textureHeight: 128, regions: [] },
      animations: [],
      exportSettings: { preferredFormatVersion: 'v5' as const },
      referenceImages: [],
    }
    vi.stubGlobal(
      'fetch',
      vi.fn<typeof fetch>(async (url) => {
        const u = String(url)
        if (u.endsWith('/api/mobs/mob-1') || u.match(/\/api\/mobs\/mob-1$/)) {
          return jsonResponse({ id: 'mob-1', name: 'Carcomido', baseType: 'humanoid', status: 'draft', thumbnailKey: null, updatedAt: '' })
        }
        if (u.endsWith('/api/mobs/mob-1/draft')) {
          return jsonResponse({ mobId: 'mob-1', draftVersion: 2, model: draftModel, updatedAt: '' })
        }
        throw new Error(`fetch inesperado: ${u}`)
      }),
    )

    const wrapper = shallowMount(MobEditor, { global: { plugins: [await routerAt('p1', 'mob-1')] } })
    await flushPromises()

    const draft = useDraftModelStore()
    expect(draft.model?.mobId).toBe('mob-1')
    expect(draft.model?.name).toBe('Carcomido')
    expect(wrapper.findComponent({ name: 'ThreeViewport' }).exists()).toBe(true)
    expect(wrapper.findComponent({ name: 'EditorToolbar' }).exists()).toBe(true)
  })

  it('sin ningún draft todavía (DRAFT_NOT_FOUND), arranca desde un modelo vacío coherente con el mob real, ticket 034 AC2', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn<typeof fetch>(async (url) => {
        const u = String(url)
        if (u.match(/\/api\/mobs\/mob-1$/)) {
          return jsonResponse({ id: 'mob-1', name: 'Carcomido Nuevo', baseType: 'arachnid', status: 'draft', thumbnailKey: null, updatedAt: '' })
        }
        if (u.endsWith('/api/mobs/mob-1/draft')) {
          return jsonResponse({ error: 'DRAFT_NOT_FOUND', message: 'Sin draft todavía.' }, 404)
        }
        throw new Error(`fetch inesperado: ${u}`)
      }),
    )

    shallowMount(MobEditor, { global: { plugins: [await routerAt('p1', 'mob-1')] } })
    await flushPromises()

    const draft = useDraftModelStore()
    expect(draft.model?.mobId).toBe('mob-1')
    expect(draft.model?.name).toBe('Carcomido Nuevo')
    expect(draft.model?.baseType).toBe('arachnid')
    expect(draft.model?.bones).toEqual([])
    expect(draft.model?.cuboids).toEqual([])
  })

  it('un mobId inexistente (MOB_NOT_FOUND) muestra un error explícito, nunca un editor roto', async () => {
    vi.stubGlobal('fetch', vi.fn<typeof fetch>(async () => jsonResponse({ error: 'MOB_NOT_FOUND', message: 'No existe.' }, 404)))

    const wrapper = shallowMount(MobEditor, { global: { plugins: [await routerAt('p1', 'no-existe')] } })
    await flushPromises()

    expect(wrapper.text()).toContain('Este mob no existe')
    expect(wrapper.findComponent({ name: 'ThreeViewport' }).exists()).toBe(false)
  })

  it('un fallo de red real (no un 404 esperado) muestra el mensaje real del error', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn<typeof fetch>(async (url) => {
        const u = String(url)
        if (u.match(/\/api\/mobs\/mob-1$/)) {
          return jsonResponse({ id: 'mob-1', name: 'Carcomido', baseType: 'humanoid', status: 'draft', thumbnailKey: null, updatedAt: '' })
        }
        return jsonResponse({ error: 'INTERNAL', message: 'Fallo real del servidor.' }, 500)
      }),
    )

    const wrapper = shallowMount(MobEditor, { global: { plugins: [await routerAt('p1', 'mob-1')] } })
    await flushPromises()

    expect(wrapper.text()).toContain('Fallo real del servidor.')
  })

  describe('Asistente IA (ticket 031)', () => {
    const draftModel = {
      mobId: 'mob-1',
      projectId: 'p1',
      name: 'Carcomido',
      baseType: 'humanoid' as const,
      units: 'minecraft_pixels' as const,
      bones: [],
      cuboids: [],
      texture: { width: 128, height: 128, storageKey: null },
      uv: { textureWidth: 128, textureHeight: 128, regions: [] },
      animations: [],
      exportSettings: { preferredFormatVersion: 'v5' as const },
      referenceImages: [],
    }

    async function mountLoaded(): Promise<ReturnType<typeof shallowMount>> {
      vi.stubGlobal(
        'fetch',
        vi.fn<typeof fetch>(async (url) => {
          const u = String(url)
          if (u.match(/\/api\/mobs\/mob-1$/)) {
            return jsonResponse({ id: 'mob-1', name: 'Carcomido', baseType: 'humanoid', status: 'draft', thumbnailKey: null, updatedAt: '' })
          }
          if (u.endsWith('/api/mobs/mob-1/draft')) {
            return jsonResponse({ mobId: 'mob-1', draftVersion: 2, model: draftModel, updatedAt: '' })
          }
          throw new Error(`fetch inesperado: ${u}`)
        }),
      )
      // Ticket 036 (pasada de fidelidad visual): "Reset cámara"/"Asistente
      // IA"/"Exportar" ahora son GButton (antes <button> HTML plano) --
      // bajo shallowMount, un componente hijo se stubea SIN su slot
      // (texto invisible, ver vue-test-utils `renderStubDefaultSlot`,
      // default `false`) -- `stubs: { GButton: false }` lo excluye del
      // shallow-stub para que su texto real siga siendo buscable,
      // dejando el resto (HierarchyPanel/ThreeViewport/AiEditPanel/etc.)
      // stubeado igual que antes.
      const wrapper = shallowMount(MobEditor, {
        global: { plugins: [await routerAt('p1', 'mob-1')], stubs: { GButton: false } },
      })
      await flushPromises()
      return wrapper
    }

    it('por default muestra HierarchyPanel + ThreeViewport, y el botón "Asistente IA" cambia a AiEditPanel', async () => {
      const wrapper = await mountLoaded()

      expect(wrapper.findComponent({ name: 'HierarchyPanel' }).exists()).toBe(true)
      expect(wrapper.findComponent({ name: 'AiEditPanel' }).exists()).toBe(false)
      expect(wrapper.findComponent({ name: 'ThreeViewport' }).exists()).toBe(true)

      const toggle = wrapper.findAll('button').find((b) => b.text() === 'Asistente IA')!
      await toggle.trigger('click')

      expect(wrapper.findComponent({ name: 'AiEditPanel' }).exists()).toBe(true)
      expect(wrapper.findComponent({ name: 'HierarchyPanel' }).exists()).toBe(false)
      expect(wrapper.findAll('button').find((b) => b.text() === 'Editor manual')).toBeDefined()
    })

    it('el botón "Exportar" navega a la pantalla de exportación del mob (ticket 032)', async () => {
      const wrapper = await mountLoaded()
      const router = wrapper.vm.$router

      await wrapper.findAll('button').find((b) => b.text() === 'Exportar')!.trigger('click')
      await flushPromises()

      expect(router.currentRoute.value.path).toBe('/projects/p1/mobs/mob-1/export')
    })

    it('preview-model-changed de AiEditPanel muestra GenerationPreviewViewport en vez de ThreeViewport (mismo singleton de canvas)', async () => {
      const wrapper = await mountLoaded()
      await wrapper.findAll('button').find((b) => b.text() === 'Asistente IA')!.trigger('click')

      const aiPanel = wrapper.findComponent({ name: 'AiEditPanel' })
      const previewModel = { ...draftModel, name: 'preview' }
      await aiPanel.vm.$emit('preview-model-changed', previewModel)

      expect(wrapper.findComponent({ name: 'ThreeViewport' }).exists()).toBe(false)
      expect(wrapper.findComponent({ name: 'GenerationPreviewViewport' }).exists()).toBe(true)

      await aiPanel.vm.$emit('preview-model-changed', null)

      expect(wrapper.findComponent({ name: 'ThreeViewport' }).exists()).toBe(true)
      expect(wrapper.findComponent({ name: 'GenerationPreviewViewport' }).exists()).toBe(false)
    })

    it('applied de AiEditPanel actualiza el draftModelStore real (el editor manual refleja el cambio aplicado)', async () => {
      const wrapper = await mountLoaded()
      await wrapper.findAll('button').find((b) => b.text() === 'Asistente IA')!.trigger('click')

      const aiPanel = wrapper.findComponent({ name: 'AiEditPanel' })
      const appliedModel = { ...draftModel, name: 'Carcomido editado' }
      await aiPanel.vm.$emit('applied', appliedModel)

      const draft = useDraftModelStore()
      expect(draft.model?.name).toBe('Carcomido editado')
    })
  })

  describe('Tab "Textura" (ticket 050, HU-41 -- mockup 07)', () => {
    const draftModel = {
      mobId: 'mob-1',
      projectId: 'p1',
      name: 'Carcomido',
      baseType: 'humanoid' as const,
      units: 'minecraft_pixels' as const,
      bones: [],
      cuboids: [],
      texture: { width: 128, height: 128, storageKey: null },
      uv: { textureWidth: 128, textureHeight: 128, regions: [] },
      animations: [],
      exportSettings: { preferredFormatVersion: 'v5' as const },
      referenceImages: [],
    }

    async function mountLoaded(): Promise<ReturnType<typeof shallowMount>> {
      vi.stubGlobal(
        'fetch',
        vi.fn<typeof fetch>(async (url) => {
          const u = String(url)
          if (u.match(/\/api\/mobs\/mob-1$/)) {
            return jsonResponse({ id: 'mob-1', name: 'Carcomido', baseType: 'humanoid', status: 'draft', thumbnailKey: null, updatedAt: '' })
          }
          if (u.endsWith('/api/mobs/mob-1/draft')) {
            return jsonResponse({ mobId: 'mob-1', draftVersion: 2, model: draftModel, updatedAt: '' })
          }
          throw new Error(`fetch inesperado: ${u}`)
        }),
      )
      const wrapper = shallowMount(MobEditor, { global: { plugins: [await routerAt('p1', 'mob-1')] } })
      await flushPromises()
      return wrapper
    }

    it('por default (tab Modelo) NO monta TextureCanvas', async () => {
      const wrapper = await mountLoaded()

      expect(wrapper.findComponent({ name: 'ThreeViewport' }).exists()).toBe(true)
      expect(wrapper.findComponent({ name: 'EditorToolbar' }).exists()).toBe(true)
      expect(wrapper.findComponent({ name: 'TextureCanvas' }).exists()).toBe(false)
    })

    it('cambiar a la tab Textura (evento de EditorHeader) monta TextureCanvas con el draft real y oculta el editor de modelo', async () => {
      const wrapper = await mountLoaded()

      await wrapper.findComponent({ name: 'EditorHeader' }).vm.$emit('update:activeTab', 'textura')

      const textureCanvas = wrapper.findComponent({ name: 'TextureCanvas' })
      expect(textureCanvas.exists()).toBe(true)
      expect(textureCanvas.props('model')).toMatchObject({ mobId: 'mob-1', name: 'Carcomido' })
      expect(wrapper.findComponent({ name: 'ThreeViewport' }).exists()).toBe(false)
      expect(wrapper.findComponent({ name: 'EditorToolbar' }).exists()).toBe(false)
      expect(wrapper.findComponent({ name: 'HierarchyPanel' }).exists()).toBe(false)
      expect(wrapper.findComponent({ name: 'InspectorPanel' }).exists()).toBe(false)
    })

    it('volver a la tab Modelo restaura ThreeViewport/EditorToolbar y desmonta TextureCanvas', async () => {
      const wrapper = await mountLoaded()
      const header = wrapper.findComponent({ name: 'EditorHeader' })

      await header.vm.$emit('update:activeTab', 'textura')
      await header.vm.$emit('update:activeTab', 'modelo')

      expect(wrapper.findComponent({ name: 'TextureCanvas' }).exists()).toBe(false)
      expect(wrapper.findComponent({ name: 'ThreeViewport' }).exists()).toBe(true)
      expect(wrapper.findComponent({ name: 'EditorToolbar' }).exists()).toBe(true)
    })
  })

  describe('Ticket 068: fila superior homologada (Reset cámara/Asistente IA/Exportar/Guardar) en las dos tabs', () => {
    const draftModel = {
      mobId: 'mob-1',
      projectId: 'p1',
      name: 'Carcomido',
      baseType: 'humanoid' as const,
      units: 'minecraft_pixels' as const,
      bones: [],
      cuboids: [],
      texture: { width: 8, height: 8, storageKey: null },
      uv: { textureWidth: 8, textureHeight: 8, regions: [] },
      animations: [],
      exportSettings: { preferredFormatVersion: 'v5' as const },
      referenceImages: [],
    }

    // A diferencia de los describe anteriores, acá NO se shallow-stubea
    // EditorToolbar/TextureCanvas -- la delegación de "Guardar" (vía
    // defineExpose) es justamente lo que se está probando, y un stub no
    // expone nada real.
    async function mountLoaded(): Promise<ReturnType<typeof shallowMount>> {
      vi.stubGlobal(
        'fetch',
        vi.fn<typeof fetch>(async (url) => {
          const u = String(url)
          if (u.match(/\/api\/mobs\/mob-1$/)) {
            return jsonResponse({ id: 'mob-1', name: 'Carcomido', baseType: 'humanoid', status: 'draft', thumbnailKey: null, updatedAt: '' })
          }
          if (u.endsWith('/api/mobs/mob-1/draft')) {
            return jsonResponse({ mobId: 'mob-1', draftVersion: 2, model: draftModel, updatedAt: '' })
          }
          throw new Error(`fetch inesperado: ${u}`)
        }),
      )
      const wrapper = shallowMount(MobEditor, {
        global: { plugins: [await routerAt('p1', 'mob-1')], stubs: { GButton: false, EditorToolbar: false, TextureCanvas: false } },
      })
      await flushPromises()
      return wrapper
    }

    async function switchToTextura(wrapper: ReturnType<typeof shallowMount>): Promise<void> {
      await wrapper.findComponent({ name: 'EditorHeader' }).vm.$emit('update:activeTab', 'textura')
      await flushPromises()
    }

    it('se muestran en las dos tabs -- antes desaparecían por completo en Textura (v-if="activeTab === \'modelo\'")', async () => {
      const wrapper = await mountLoaded()
      const topLabels = (): string[] => wrapper.findAll('button').map((b) => b.text()).filter(Boolean)
      expect(topLabels()).toEqual(expect.arrayContaining(['Reset cámara', 'Asistente IA', 'Exportar', 'Guardar']))

      await switchToTextura(wrapper)

      expect(topLabels()).toEqual(expect.arrayContaining(['Reset cámara', 'Generar con IA', 'Exportar', 'Guardar']))
    })

    it('en Textura, el botón compartido de IA (ahora "Generar con IA" en la fila superior) navega a la ruta real del generador -- reemplaza el botón que antes vivía dentro de TextureCanvas.vue', async () => {
      const wrapper = await mountLoaded()
      await switchToTextura(wrapper)
      const router = wrapper.vm.$router

      await wrapper.findAll('button').find((b) => b.text() === 'Generar con IA')!.trigger('click')
      await flushPromises()

      expect(router.currentRoute.value.path).toBe('/projects/p1/mobs/mob-1/texture/generate-ai')
    })

    it('Guardar (fila superior) delega en EditorToolbar.handleSave cuando la tab activa es Modelo', async () => {
      const wrapper = await mountLoaded()

      await wrapper.findAll('button').find((b) => b.text() === 'Guardar')!.trigger('click')
      await flushPromises()

      expect(saveRevision).toHaveBeenCalledWith('mob-1', expect.objectContaining({ mobId: 'mob-1' }))
    })

    it('Guardar (fila superior) delega en TextureCanvas.handleSave cuando la tab activa es Textura', async () => {
      const wrapper = await mountLoaded()
      await switchToTextura(wrapper)
      vi.mocked(saveRevision).mockClear()
      vi.spyOn(threeViewportService, 'captureThumbnail').mockResolvedValue(new Blob(['png'], { type: 'image/png' }))

      await wrapper.findAll('button').find((b) => b.text() === 'Guardar')!.trigger('click')
      await flushPromises()

      expect(saveRevision).toHaveBeenCalledWith('mob-1', expect.objectContaining({ mobId: 'mob-1' }))
    })
  })
})
