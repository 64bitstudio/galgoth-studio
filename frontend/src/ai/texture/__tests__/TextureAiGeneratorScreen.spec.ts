import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../../../api/ApiError'

const getMob = vi.fn()
vi.mock('../../../projects/mobsApi', () => ({
  getMob: (...args: unknown[]) => getMob(...args),
}))

const getDraft = vi.fn()
vi.mock('../../../editor/draftPersistenceApi', () => ({
  getDraft: (...args: unknown[]) => getDraft(...args),
}))

const listReferenceImages = vi.fn()
const uploadReferenceImage = vi.fn()
vi.mock('../../../api/referenceImagesApi', () => ({
  listReferenceImages: (...args: unknown[]) => listReferenceImages(...args),
  uploadReferenceImage: (...args: unknown[]) => uploadReferenceImage(...args),
  referenceImageUrl: (relativeUrl: string) => `http://localhost:8080${relativeUrl}`,
  MAX_REFERENCE_IMAGE_BYTES: 10 * 1024 * 1024,
  SUPPORTED_REFERENCE_IMAGE_TYPES: ['image/png', 'image/jpeg'],
}))

const startTextureGeneration = vi.fn()
const getTextureResult = vi.fn()
const applyTexture = vi.fn()
vi.mock('../../../api/textureGenerationApi', () => ({
  startTextureGeneration: (...args: unknown[]) => startTextureGeneration(...args),
  getTextureResult: (...args: unknown[]) => getTextureResult(...args),
  applyTexture: (...args: unknown[]) => applyTexture(...args),
}))

vi.mock('../../../api/generationApi', () => ({
  eventsUrl: (jobId: string) => `http://localhost:8080/api/jobs/${jobId}/events`,
}))

const decodeTexturePreviewPatch = vi.fn()
vi.mock('../texturePatchDecode', () => ({
  decodeTexturePreviewPatch: (...args: unknown[]) => decodeTexturePreviewPatch(...args),
}))

const { default: TextureAiGeneratorScreen } = await import('../TextureAiGeneratorScreen.vue')

class FakeEventSource {
  static instances: FakeEventSource[] = []
  url: string
  close = vi.fn()
  private listeners: Record<string, ((e: MessageEvent) => void)[]> = {}

  constructor(url: string) {
    this.url = url
    FakeEventSource.instances.push(this)
  }

  addEventListener(type: string, handler: (e: MessageEvent) => void): void {
    ;(this.listeners[type] ??= []).push(handler)
  }

  emit(type: string, data: unknown): void {
    const event = { data: JSON.stringify(data) } as MessageEvent
    for (const handler of this.listeners[type] ?? []) {
      handler(event)
    }
  }
}

function progressEvent(overrides: Partial<Record<string, unknown>> = {}) {
  return { seq: 1, stage: 'analizando_paleta', message: 'Analizando paleta…', progressPct: 5, payload: null, ...overrides }
}

const mobSummary = { id: 'mob-1', name: 'Carcomido', baseType: 'humanoid' as const, status: 'draft' as const, thumbnailKey: null, updatedAt: '' }

const draftView = {
  mobId: 'mob-1',
  draftVersion: 1,
  updatedAt: '',
  model: {
    mobId: 'mob-1',
    projectId: 'project-1',
    name: 'Carcomido',
    baseType: 'humanoid' as const,
    units: 'minecraft_pixels',
    bones: [
      { id: 'b1', name: 'head', parentId: null, pivot: [0, 0, 0], rotation: [0, 0, 0] },
      { id: 'b2', name: 'empty_bone', parentId: null, pivot: [0, 0, 0], rotation: [0, 0, 0] },
    ],
    cuboids: [{ id: 'c1', name: 'head_box', boneId: 'b1', from: [0, 0, 0], to: [1, 1, 1], origin: [0, 0, 0], rotation: [0, 0, 0], faces: {} }],
    texture: { width: 64, height: 64, storageKey: null },
    uv: { textureWidth: 64, textureHeight: 64, regions: [], reservations: [] },
    animations: [],
    exportSettings: { preferredFormatVersion: 'v5' },
    referenceImages: [],
  },
}

const references = [{ id: 'r1', url: '/api/mobs/mob-1/references/r1', width: 100, height: 100, contentType: 'image/png', createdAt: '' }]

async function routerAt(): Promise<Router> {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/projects/:projectId/mobs/:mobId/texture/generate-ai', component: TextureAiGeneratorScreen },
      { path: '/projects/:projectId/mobs/:mobId/edit', component: { template: '<div>editor</div>' } },
      { path: '/projects/:projectId', component: { template: '<div>project</div>' } },
    ],
  })
  await router.push('/projects/project-1/mobs/mob-1/texture/generate-ai')
  return router
}

function buttons(wrapper: ReturnType<typeof mount>) {
  return wrapper.findAll('button')
}

function buttonWithText(wrapper: ReturnType<typeof mount>, text: string) {
  return buttons(wrapper).find((b) => b.text() === text)
}

/** Ticket 067 -- Estilo/Detalle son ahora `role="radio"` personalizados, nunca `<input type="radio">`/`<input type="range">` nativos. */
function styleCards(wrapper: ReturnType<typeof mount>) {
  return wrapper.findAll('.texture-ai-generator__style-card')
}

function detailOptions(wrapper: ReturnType<typeof mount>) {
  return wrapper.findAll('.texture-ai-generator__segmented-opt')
}

/** Ticket 067 -- "Parte a generar" es ahora un `GSelect` (058), nunca `<select>` nativo. */
async function openBoneSelect(wrapper: ReturnType<typeof mount>): Promise<void> {
  await wrapper.get('button.g-select__trigger').trigger('click')
}

async function pickBoneOption(wrapper: ReturnType<typeof mount>, label: string): Promise<void> {
  await openBoneSelect(wrapper)
  const option = wrapper.findAll('.g-select__option').find((o) => o.text() === label)!
  await option.trigger('click')
}

const FAKE_REFERENCE_FILE = new File([new Uint8Array([1, 2, 3])], 'nueva-referencia.png', { type: 'image/png' })

async function selectReferenceFile(wrapper: ReturnType<typeof mount>, file: File): Promise<void> {
  const input = wrapper.get('input[aria-label="Elegir nueva imagen de referencia"]').element as HTMLInputElement
  Object.defineProperty(input, 'files', { value: [file], configurable: true })
  input.dispatchEvent(new Event('change'))
  await flushPromises()
}

const fakeCtx = { drawImage: vi.fn(), clearRect: vi.fn() }

describe('TextureAiGeneratorScreen.vue', () => {
  beforeEach(() => {
    getMob.mockReset().mockResolvedValue(mobSummary)
    getDraft.mockReset().mockResolvedValue(draftView)
    listReferenceImages.mockReset().mockResolvedValue(references)
    uploadReferenceImage.mockReset()
    startTextureGeneration.mockReset().mockResolvedValue({ jobId: 'job-1' })
    getTextureResult.mockReset()
    applyTexture.mockReset()
    decodeTexturePreviewPatch.mockReset().mockResolvedValue({ close: vi.fn() })
    FakeEventSource.instances = []
    vi.stubGlobal('EventSource', FakeEventSource)
    vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue(fakeCtx as unknown as CanvasRenderingContext2D)
    fakeCtx.drawImage.mockClear()
    fakeCtx.clearRect.mockClear()
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  it('al cargar, muestra la referencia, las 4 opciones de Estilo, el control de Detalle y "Parte a generar" con los bones con geometría', async () => {
    const router = await routerAt()
    const wrapper = mount(TextureAiGeneratorScreen, { global: { plugins: [router] } })
    await flushPromises()

    expect(listReferenceImages).toHaveBeenCalledWith('mob-1')
    const img = wrapper.find('img[alt="Referencia del mob"]')
    expect(img.exists()).toBe(true)
    expect(img.attributes('src')).toContain('/api/mobs/mob-1/references/r1')

    const cards = styleCards(wrapper)
    expect(cards).toHaveLength(4)
    expect(wrapper.text()).toContain('Fiel a la referencia')
    expect(wrapper.text()).toContain('Minecraft Vanilla')
    expect(wrapper.text()).toContain('Pixel Art')
    expect(wrapper.text()).toContain('Realista')
    expect(cards[0]!.attributes('aria-checked')).toBe('true') // 'faithful' es el default

    const detailOpts = detailOptions(wrapper)
    expect(detailOpts.map((o) => o.text())).toEqual(['Bajo', 'Medio', 'Alto'])
    expect(detailOpts[1]!.attributes('aria-checked')).toBe('true') // índice 1 ('medium') es el default

    await openBoneSelect(wrapper)
    const options = wrapper.findAll('.g-select__option').map((o) => o.text())
    expect(options).toEqual(['Modelo completo', 'head']) // empty_bone (sin cuboids) queda afuera
  })

  it('mob inexistente muestra el mensaje de error y no intenta cargar el resto', async () => {
    getMob.mockRejectedValue(new ApiError('no existe', 404, 'MOB_NOT_FOUND'))
    const router = await routerAt()
    const wrapper = mount(TextureAiGeneratorScreen, { global: { plugins: [router] } })
    await flushPromises()

    expect(wrapper.text()).toContain('Este mob no existe.')
  })

  it('sin ninguna imagen de referencia, muestra el aviso y "Generar con IA" queda deshabilitado', async () => {
    listReferenceImages.mockResolvedValue([])
    const router = await routerAt()
    const wrapper = mount(TextureAiGeneratorScreen, { global: { plugins: [router] } })
    await flushPromises()

    expect(wrapper.text()).toContain('no se puede generar textura por IA')
    const button = buttonWithText(wrapper, 'Generar con IA')!
    expect(button.attributes('disabled')).toBeDefined()
  })

  it('al confirmar, arranca la generación con el estilo/detalle/parte elegidos y abre el stream de eventos', async () => {
    const router = await routerAt()
    const wrapper = mount(TextureAiGeneratorScreen, { global: { plugins: [router] } })
    await flushPromises()

    await styleCards(wrapper)[2]!.trigger('click') // Pixel Art
    await detailOptions(wrapper)[2]!.trigger('click') // Alto
    await pickBoneOption(wrapper, 'head')

    await buttonWithText(wrapper, 'Generar con IA')!.trigger('click')
    await flushPromises()

    expect(startTextureGeneration).toHaveBeenCalledWith('mob-1', { style: 'pixel_art', detailLevel: 'high', boneId: 'b1' })
    expect(FakeEventSource.instances).toHaveLength(1)
    expect(FakeEventSource.instances[0]!.url).toBe('http://localhost:8080/api/jobs/job-1/events')
  })

  it('un evento de progreso actualiza el mensaje/etapa activa, y preview_texture_patch dibuja el parche sobre el canvas', async () => {
    const router = await routerAt()
    const wrapper = mount(TextureAiGeneratorScreen, { global: { plugins: [router] } })
    await flushPromises()
    await buttonWithText(wrapper, 'Generar con IA')!.trigger('click')
    await flushPromises()

    FakeEventSource.instances[0]!.emit(
      'progress',
      progressEvent({
        seq: 2,
        stage: 'componiendo_atlas',
        message: 'Componiendo atlas: head…',
        progressPct: 60,
        payload: { type: 'preview_texture_patch', rect: { x: 4, y: 8, width: 16, height: 16 }, encoding: 'base64', data: 'AAA=' },
      }),
    )
    await flushPromises()

    expect(wrapper.text()).toContain('Componiendo atlas: head…')
    expect(wrapper.find('progress').attributes('value')).toBe('60')
    expect(decodeTexturePreviewPatch).toHaveBeenCalled()
    expect(fakeCtx.drawImage).toHaveBeenCalledWith(expect.anything(), 4, 8)
  })

  it('al completar, cierra el stream, pide el resultado y muestra el diff Antes/Después con toggle', async () => {
    getTextureResult.mockResolvedValue({
      jobId: 'job-1',
      mobId: 'mob-1',
      wholeModel: true,
      touchedBoneIds: ['b1'],
      touchedFaces: [],
      hasHandPaintedOverwrite: false,
      beforeAtlasPngBase64: 'QkVGT1JF',
      afterAtlasPngBase64: 'QUZURVI=',
    })
    const router = await routerAt()
    const wrapper = mount(TextureAiGeneratorScreen, { global: { plugins: [router] } })
    await flushPromises()
    await buttonWithText(wrapper, 'Generar con IA')!.trigger('click')
    await flushPromises()

    FakeEventSource.instances[0]!.emit('progress', progressEvent({ seq: 2, stage: 'completado', progressPct: 100 }))
    await flushPromises()

    expect(FakeEventSource.instances[0]!.close).toHaveBeenCalled()
    expect(getTextureResult).toHaveBeenCalledWith('job-1')
    let img = wrapper.find('img.texture-ai-generator__result-image')
    expect(img.attributes('src')).toContain('QUZURVI=') // after, por default

    await buttonWithText(wrapper, 'Antes')!.trigger('click')
    img = wrapper.find('img.texture-ai-generator__result-image')
    expect(img.attributes('src')).toContain('QkVGT1JF')
  })

  it('hasHandPaintedOverwrite=true muestra la advertencia reforzada de HU-37 AC #2', async () => {
    getTextureResult.mockResolvedValue({
      jobId: 'job-1',
      mobId: 'mob-1',
      wholeModel: false,
      touchedBoneIds: ['b1'],
      touchedFaces: [],
      hasHandPaintedOverwrite: true,
      beforeAtlasPngBase64: 'AAA=',
      afterAtlasPngBase64: 'BBB=',
    })
    const router = await routerAt()
    const wrapper = mount(TextureAiGeneratorScreen, { global: { plugins: [router] } })
    await flushPromises()
    await buttonWithText(wrapper, 'Generar con IA')!.trigger('click')
    await flushPromises()
    FakeEventSource.instances[0]!.emit('progress', progressEvent({ seq: 2, stage: 'completado', progressPct: 100 }))
    await flushPromises()

    expect(wrapper.text()).toContain('sobrescribirá contenido pintado A MANO')
  })

  it('un fallo del pipeline (evento "fallido") muestra el error y ofrece Reintentar', async () => {
    const router = await routerAt()
    const wrapper = mount(TextureAiGeneratorScreen, { global: { plugins: [router] } })
    await flushPromises()
    await buttonWithText(wrapper, 'Generar con IA')!.trigger('click')
    await flushPromises()

    FakeEventSource.instances[0]!.emit('progress', progressEvent({ seq: 2, stage: 'fallido', message: 'Fallo del proveedor de imágenes.' }))
    await flushPromises()

    expect(wrapper.text()).toContain('Fallo del proveedor de imágenes.')
    expect(buttonWithText(wrapper, 'Reintentar')).toBeDefined()
  })

  it('Rechazar vuelve al formulario sin llamar a applyTexture', async () => {
    getTextureResult.mockResolvedValue({
      jobId: 'job-1',
      mobId: 'mob-1',
      wholeModel: true,
      touchedBoneIds: [],
      touchedFaces: [],
      hasHandPaintedOverwrite: false,
      beforeAtlasPngBase64: 'AAA=',
      afterAtlasPngBase64: 'BBB=',
    })
    const router = await routerAt()
    const wrapper = mount(TextureAiGeneratorScreen, { global: { plugins: [router] } })
    await flushPromises()
    await buttonWithText(wrapper, 'Generar con IA')!.trigger('click')
    await flushPromises()
    FakeEventSource.instances[0]!.emit('progress', progressEvent({ seq: 2, stage: 'completado' }))
    await flushPromises()

    await buttonWithText(wrapper, 'Rechazar')!.trigger('click')

    expect(applyTexture).not.toHaveBeenCalled()
    expect(buttonWithText(wrapper, 'Generar con IA')).toBeDefined()
  })

  it('Aplicar exitoso llama a applyTexture y navega de vuelta al editor del mob', async () => {
    getTextureResult.mockResolvedValue({
      jobId: 'job-1',
      mobId: 'mob-1',
      wholeModel: true,
      touchedBoneIds: [],
      touchedFaces: [],
      hasHandPaintedOverwrite: false,
      beforeAtlasPngBase64: 'AAA=',
      afterAtlasPngBase64: 'BBB=',
    })
    applyTexture.mockResolvedValue({ revisionNumber: 4, draftVersion: 6 })
    const router = await routerAt()
    const wrapper = mount(TextureAiGeneratorScreen, { global: { plugins: [router] } })
    await flushPromises()
    await buttonWithText(wrapper, 'Generar con IA')!.trigger('click')
    await flushPromises()
    FakeEventSource.instances[0]!.emit('progress', progressEvent({ seq: 2, stage: 'completado' }))
    await flushPromises()

    await buttonWithText(wrapper, 'Aplicar')!.trigger('click')
    await flushPromises()

    expect(applyTexture).toHaveBeenCalledWith('job-1')
    expect(router.currentRoute.value.fullPath).toBe('/projects/project-1/mobs/mob-1/edit')
  })

  it('Aplicar con conflicto 409 STALE_TEXTURE_BASE muestra el error y ofrece regenerar', async () => {
    getTextureResult.mockResolvedValue({
      jobId: 'job-1',
      mobId: 'mob-1',
      wholeModel: true,
      touchedBoneIds: [],
      touchedFaces: [],
      hasHandPaintedOverwrite: false,
      beforeAtlasPngBase64: 'AAA=',
      afterAtlasPngBase64: 'BBB=',
    })
    applyTexture.mockRejectedValue(new ApiError('El draft avanzó desde que se generó esta propuesta.', 409, 'STALE_TEXTURE_BASE'))
    const router = await routerAt()
    const wrapper = mount(TextureAiGeneratorScreen, { global: { plugins: [router] } })
    await flushPromises()
    await buttonWithText(wrapper, 'Generar con IA')!.trigger('click')
    await flushPromises()
    FakeEventSource.instances[0]!.emit('progress', progressEvent({ seq: 2, stage: 'completado' }))
    await flushPromises()

    await buttonWithText(wrapper, 'Aplicar')!.trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('El draft avanzó desde que se generó esta propuesta.')
    expect(buttonWithText(wrapper, 'Regenerar contra el estado actual')).toBeDefined()
  })

  describe('ticket 067: rediseño sin componentes nativos (VoBo del PO, preview https://claude.ai/code/artifact/9efe4c8d-2776-4f94-be58-6caa4fb78ebc)', () => {
    it('el radiogroup de Estilo se navega con flechas, con wrap-around', async () => {
      const router = await routerAt()
      const wrapper = mount(TextureAiGeneratorScreen, { global: { plugins: [router] } })
      await flushPromises()

      await styleCards(wrapper)[0]!.trigger('keydown', { key: 'ArrowRight' })
      expect(styleCards(wrapper)[1]!.attributes('aria-checked')).toBe('true') // 'minecraft_vanilla'

      await styleCards(wrapper)[3]!.trigger('keydown', { key: 'ArrowRight' })
      expect(styleCards(wrapper)[0]!.attributes('aria-checked')).toBe('true') // wrap-around de vuelta a 'faithful'

      await styleCards(wrapper)[0]!.trigger('keydown', { key: 'ArrowLeft' })
      expect(styleCards(wrapper)[3]!.attributes('aria-checked')).toBe('true') // wrap-around hacia atrás a 'realistic'
    })

    it('el segmented de Detalle se navega con flechas, con wrap-around', async () => {
      const router = await routerAt()
      const wrapper = mount(TextureAiGeneratorScreen, { global: { plugins: [router] } })
      await flushPromises()

      await detailOptions(wrapper)[2]!.trigger('keydown', { key: 'ArrowRight' })
      expect(detailOptions(wrapper)[0]!.attributes('aria-checked')).toBe('true') // wrap-around a 'Bajo'
    })

    it('reemplazar la imagen de referencia: sube el archivo y actualiza la imagen mostrada', async () => {
      uploadReferenceImage.mockResolvedValue({
        id: 'r2',
        url: '/api/mobs/mob-1/references/r2',
        width: 200,
        height: 200,
        contentType: 'image/png',
        createdAt: '',
      })
      const router = await routerAt()
      const wrapper = mount(TextureAiGeneratorScreen, { global: { plugins: [router] } })
      await flushPromises()

      await selectReferenceFile(wrapper, FAKE_REFERENCE_FILE)

      expect(uploadReferenceImage).toHaveBeenCalledWith('mob-1', FAKE_REFERENCE_FILE)
      const img = wrapper.find('img[alt="Referencia del mob"]')
      expect(img.attributes('src')).toContain('/api/mobs/mob-1/references/r2')
    })

    it('reemplazar la imagen de referencia: formato no soportado muestra error y no sube nada', async () => {
      const router = await routerAt()
      const wrapper = mount(TextureAiGeneratorScreen, { global: { plugins: [router] } })
      await flushPromises()

      const gifFile = new File([new Uint8Array([1])], 'x.gif', { type: 'image/gif' })
      await selectReferenceFile(wrapper, gifFile)

      expect(uploadReferenceImage).not.toHaveBeenCalled()
      expect(wrapper.text()).toContain("Formato no soportado: 'image/gif' -- solo se aceptan PNG o JPEG.")
    })

    it('reemplazar la imagen de referencia: archivo demasiado pesado muestra error y no sube nada', async () => {
      const router = await routerAt()
      const wrapper = mount(TextureAiGeneratorScreen, { global: { plugins: [router] } })
      await flushPromises()

      const oversizedFile = new File([new Uint8Array([1])], 'grande.png', { type: 'image/png' })
      Object.defineProperty(oversizedFile, 'size', { value: 11 * 1024 * 1024 })
      await selectReferenceFile(wrapper, oversizedFile)

      expect(uploadReferenceImage).not.toHaveBeenCalled()
      expect(wrapper.text()).toContain('el máximo soportado es 10MB')
    })

    it('reemplazar la imagen de referencia: un error del servidor se muestra sin romper el formulario', async () => {
      uploadReferenceImage.mockRejectedValue(new ApiError('No se pudo subir la imagen.', 500, 'INTERNAL_ERROR'))
      const router = await routerAt()
      const wrapper = mount(TextureAiGeneratorScreen, { global: { plugins: [router] } })
      await flushPromises()

      await selectReferenceFile(wrapper, FAKE_REFERENCE_FILE)

      expect(wrapper.text()).toContain('No se pudo subir la imagen.')
    })
  })
})
