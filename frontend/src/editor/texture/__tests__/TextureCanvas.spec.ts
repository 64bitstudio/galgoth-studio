import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import type { Mesh, MeshStandardMaterial } from 'three'
import { nextTick } from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { Cuboid, MobProjectModel, UvRegion } from '../../../domain/MobProjectModel'
import { FACE_HIGHLIGHT_NAME } from '../../../viewport/buildMobScene'
import { useTextureEditorStore } from '../textureEditorStore'
import { useTextureSelectionStore } from '../textureSelectionStore'

// Ticket 058 -- mismo criterio que `EditorToolbar.spec.ts`: se mockean los
// módulos de guardado (flush/saveRevision/thumbnail) en vez de stubear
// `fetch` -- `encodeAtlasToPngBlob` (dentro de `flushPaintedTexture`)
// requiere un `<canvas>` 2D real que jsdom no implementa.
vi.mock('../../draftPersistenceApi', () => ({ saveRevision: vi.fn() }))
vi.mock('../../thumbnailApi', () => ({ uploadThumbnail: vi.fn() }))
vi.mock('../textureFlush', () => ({ flushPaintedTexture: vi.fn(async (model) => model) }))

// Ver ThreeViewportService.spec.ts -- jsdom no tiene WebGL real. Este
// componente adjunta el canvas singleton compartido para el preview 3D
// (HU-26), así que necesita el mismo mock.
vi.mock('three', async (importOriginal) => {
  const actual = await importOriginal<typeof import('three')>()
  class FakeWebGLRenderer {
    domElement = document.createElement('canvas')
    setSize = vi.fn()
    render = vi.fn()
  }
  return { ...actual, WebGLRenderer: FakeWebGLRenderer }
})

// Ticket 048 -- el decode real de PNG depende de `createImageBitmap`/canvas
// 2D real, ninguno de los dos existe en jsdom (ver docstring de
// `pngImportDecode.ts`); se mockea acá para poder ejercitar el flujo de
// import end-to-end (selector de región -> TextureImportPanel -> store).
vi.mock('../pngImportDecode', () => ({
  decodePngBytesToAtlasBuffer: vi.fn(),
  PngDecodeError: class PngDecodeError extends Error {},
}))

// Ticket 066 -- ver docstring de `loadModelAtlas` en TextureCanvas.vue: la
// carga real de la textura persistida depende de `fetch`, mockeada acá al
// nivel del cliente HTTP (mismo criterio que el resto de los mocks de esta
// suite, nunca stubeando `fetch` directo).
vi.mock('../../../api/textureUploadApi', () => ({ downloadTexture: vi.fn() }))

const { threeViewportService } = await import('../../../viewport/ThreeViewportService')
const { default: TextureCanvas } = await import('../TextureCanvas.vue')
const { decodePngBytesToAtlasBuffer } = await import('../pngImportDecode')
const mockDecode = vi.mocked(decodePngBytesToAtlasBuffer)
const { downloadTexture } = await import('../../../api/textureUploadApi')
const mockDownloadTexture = vi.mocked(downloadTexture)

function solidPixels(width: number, height: number, color: [number, number, number, number]): Uint8ClampedArray {
  const pixels = new Uint8ClampedArray(width * height * 4)
  for (let i = 0; i < pixels.length; i += 4) {
    pixels.set(color, i)
  }
  return pixels
}

const FAKE_PNG_FILE = new File([new Uint8Array([1])], 'x.png', { type: 'image/png' })

async function selectImportFile(wrapper: ReturnType<typeof mount>, file: File): Promise<void> {
  const input = wrapper.get('input[aria-label="Archivo PNG a importar"]').element as HTMLInputElement
  Object.defineProperty(input, 'files', { value: [file], configurable: true })
  input.dispatchEvent(new Event('change'))
  await new Promise((resolve) => setTimeout(resolve, 0))
  await wrapper.vm.$nextTick()
}

const EMPTY_FACES = {
  north: { uv: [0, 0, 0, 0] as [number, number, number, number], texture: null },
  south: { uv: [0, 0, 0, 0] as [number, number, number, number], texture: null },
  east: { uv: [0, 0, 0, 0] as [number, number, number, number], texture: null },
  west: { uv: [0, 0, 0, 0] as [number, number, number, number], texture: null },
  up: { uv: [0, 0, 0, 0] as [number, number, number, number], texture: null },
  down: { uv: [0, 0, 0, 0] as [number, number, number, number], texture: null },
}

function cuboid(id: string, name: string): Cuboid {
  return { id, name, boneId: 'bone-1', from: [-1, -1, -1], to: [1, 1, 1], origin: [0, 0, 0], rotation: [0, 0, 0], faces: EMPTY_FACES }
}

function modelWith(
  opts: { width?: number; height?: number; cuboids?: Cuboid[]; regions?: UvRegion[]; storageKey?: string | null } = {},
): MobProjectModel {
  const width = opts.width ?? 8
  const height = opts.height ?? 8
  return {
    mobId: 'mob-1',
    projectId: 'project-1',
    name: 'mob-test',
    baseType: 'humanoid',
    units: 'minecraft_pixels',
    bones: [],
    cuboids: opts.cuboids ?? [],
    texture: { width, height, storageKey: opts.storageKey ?? null },
    uv: { textureWidth: width, textureHeight: height, regions: opts.regions ?? [], reservations: [] },
    animations: [],
    exportSettings: { preferredFormatVersion: 'v5' },
    referenceImages: [],
  }
}

/** jsdom nunca hace layout real -- `getBoundingClientRect()` devuelve siempre ceros salvo que se mockee. `width`/`height` acá simulan la escala CSS con la que el canvas se MUESTRA (puede ser mayor a su resolución intrínseca, ver docstring del componente). */
function mockCanvasRect(width: number, height: number): void {
  vi.spyOn(HTMLCanvasElement.prototype, 'getBoundingClientRect').mockReturnValue({
    x: 0,
    y: 0,
    top: 0,
    left: 0,
    right: width,
    bottom: height,
    width,
    height,
    toJSON: () => ({}),
  } as DOMRect)
}

function dispatchPointer(el: Element, type: string, clientX: number, clientY: number, pointerId = 1): void {
  el.dispatchEvent(new PointerEvent(type, { clientX, clientY, pointerId, bubbles: true }))
}

function pixelAt(pixels: Uint8ClampedArray, width: number, x: number, y: number): number[] {
  const i = (y * width + x) * 4
  return [pixels[i]!, pixels[i + 1]!, pixels[i + 2]!, pixels[i + 3]!]
}

/** Ticket 058: el selector de región es ahora `GSelect.vue` (sin `<select>` nativo) -- abre el trigger identificado por `aria-label` y clickea la opción cuyo texto exacto matchea. */
async function chooseGSelectOption(wrapper: ReturnType<typeof mount>, triggerAriaLabel: string, optionText: string): Promise<void> {
  await wrapper.get(`button[aria-label="${triggerAriaLabel}"]`).trigger('click')
  const option = wrapper.findAll('[role="option"]').find((o) => o.text() === optionText)
  if (!option) {
    throw new Error(`No se encontró la opción "${optionText}" en el GSelect "${triggerAriaLabel}".`)
  }
  await option.trigger('click')
}

/** Ticket 058: el color activo ahora se elige vía `TextureColorPicker.vue` -- abre su trigger y confirma un hex por el campo de texto (camino que acepta CUALQUIER color, no solo la paleta fija). */
async function setActiveColorHex(wrapper: ReturnType<typeof mount>, hex: string): Promise<void> {
  await wrapper.get('button[aria-label="Color activo"]').trigger('click')
  const hexInput = wrapper.get('input[aria-label="Código hexadecimal del color activo"]')
  await hexInput.setValue(hex)
  await hexInput.trigger('keydown', { key: 'Enter' })
}

describe('TextureCanvas.vue', () => {
  let wrapper: ReturnType<typeof mount<typeof TextureCanvas>> | null = null

  beforeEach(() => {
    setActivePinia(createPinia())
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = null
    threeViewportService.stopRenderLoop()
    vi.restoreAllMocks()
  })

  async function mountCanvas(model: MobProjectModel): Promise<ReturnType<typeof mount<typeof TextureCanvas>>> {
    wrapper = mount(TextureCanvas, { props: { model } })
    return wrapper
  }

  it('AC HU-24: al montar, carga el atlas del store con las dimensiones REALES del modelo -- nunca bloqueado (mismo criterio que 034, con o sin revisión guardada)', async () => {
    await mountCanvas(modelWith({ width: 16, height: 12 }))
    const store = useTextureEditorStore()

    expect(store.atlas?.width).toBe(16)
    expect(store.atlas?.height).toBe(12)
  })

  /**
   * Ticket 066 (hallazgo real, reportado por el PO): antes de este fix,
   * un mob con `texture.storageKey` ya asignado (textura real generada
   * por IA y aplicada, o pintada a mano y guardada) igual abría un atlas
   * VACÍO al montar -- nunca se leían los bytes ya persistidos.
   */
  describe('ticket 066: carga de la textura ya persistida al montar', () => {
    beforeEach(() => {
      mockDownloadTexture.mockReset()
      mockDecode.mockReset()
    })

    it('con storageKey, descarga y decodifica la textura real, y el atlas queda con ESOS píxeles (no vacío)', async () => {
      const pixels = solidPixels(4, 4, [10, 20, 30, 255])
      const pngBlob = new Blob(['fake-png'], { type: 'image/png' })
      mockDownloadTexture.mockResolvedValue(pngBlob)
      mockDecode.mockResolvedValue({ pixels, width: 4, height: 4 })

      await mountCanvas(modelWith({ width: 4, height: 4, storageKey: 'textures/abc.png' }))
      await flushPromises()
      const store = useTextureEditorStore()

      expect(mockDownloadTexture).toHaveBeenCalledWith('mob-1')
      expect(mockDecode).toHaveBeenCalledWith(pngBlob)
      expect(store.atlas?.pixels).toEqual(pixels)
    })

    it('sin storageKey (mob sin textura real todavía), NUNCA llama a downloadTexture -- mismo comportamiento de siempre', async () => {
      await mountCanvas(modelWith({ storageKey: null }))
      await flushPromises()

      expect(mockDownloadTexture).not.toHaveBeenCalled()
    })

    it('si la descarga/decodificación falla, muestra un error visible y BLOQUEA "Guardar" -- nunca abre un atlas vacío en silencio (riesgo real de sobrescribir la textura persistida)', async () => {
      mockDownloadTexture.mockRejectedValue(new Error('502 Bad Gateway'))

      const wrapper = await mountCanvas(modelWith({ storageKey: 'textures/abc.png' }))
      await flushPromises()

      const alert = wrapper.get('[role="alert"]')
      expect(alert.text()).toContain('No se pudo cargar la textura guardada')
      // Ticket 068: "Guardar" ya no es un botón de este componente (sube a
      // la fila superior compartida de MobEditor.vue) -- la condición de
      // bloqueo se verifica sobre `canSave`, expuesto vía defineExpose.
      expect(wrapper.vm.canSave).toBe(false)
    })
  })

  describe('ticket 069: reloadAtlas (tras aplicar una textura generada por IA desde el drawer compartido)', () => {
    beforeEach(() => {
      mockDownloadTexture.mockReset()
      mockDecode.mockReset()
    })

    it('vuelve a descargar/decodificar el atlas del `model` actual -- misma lógica que la carga inicial, sin duplicarla', async () => {
      // Sin storageKey, la carga inicial nunca llama a downloadTexture -- nada que armar para ese primer mount.
      const model = modelWith({ width: 4, height: 4, storageKey: null })
      const wrapper = await mountCanvas(model)
      await flushPromises()
      expect(mockDownloadTexture).not.toHaveBeenCalled()

      const pixels = solidPixels(4, 4, [1, 2, 3, 255])
      const pngBlob = new Blob(['fake-png'], { type: 'image/png' })
      mockDownloadTexture.mockResolvedValue(pngBlob)
      mockDecode.mockResolvedValue({ pixels, width: 4, height: 4 })
      model.texture.storageKey = 'textures/nueva-ia.png' // MobEditor.vue reemplaza `draft.model` con uno recién obtenido -- acá se muta el mismo objeto por simplicidad, la prop es la misma referencia.

      await wrapper.vm.reloadAtlas()

      expect(mockDownloadTexture).toHaveBeenCalledWith('mob-1')
      const store = useTextureEditorStore()
      expect(store.atlas?.pixels).toEqual(pixels)
    })
  })

  it('AC HU-24: el selector de región lista "Todas las caras" + una opción por región, etiquetada "${cuboid.name} (${face})"', async () => {
    const region: UvRegion = { cuboidId: 'c1', face: 'north', rect: [0, 0, 4, 4], status: 'unpainted' }
    const model = modelWith({ cuboids: [cuboid('c1', 'Cabeza')], regions: [region] })
    const w = await mountCanvas(model)

    await w.get('button[aria-label="Región UV a enfocar"]').trigger('click')
    const options = w.findAll('[role="option"]').map((o) => o.text())

    expect(options).toEqual(['Todas las caras', 'Cabeza (north)'])
  })

  it('AC HU-24: elegir una región la resalta (clase --selected) en el overlay -- "Todas las caras" no resalta ninguna', async () => {
    const region: UvRegion = { cuboidId: 'c1', face: 'north', rect: [0, 0, 4, 4], status: 'unpainted' }
    const model = modelWith({ cuboids: [cuboid('c1', 'Cabeza')], regions: [region] })
    const w = await mountCanvas(model)

    expect(w.get('rect.texture-canvas__region').classes()).not.toContain('texture-canvas__region--selected')

    await chooseGSelectOption(w, 'Región UV a enfocar', 'Cabeza (north)')

    expect(w.get('rect.texture-canvas__region').classes()).toContain('texture-canvas__region--selected')
  })

  it('AC HU-24: excluye regiones ORPHAN del selector (su cuboid ya no existe)', async () => {
    const regions: UvRegion[] = [
      { cuboidId: 'c1', face: 'north', rect: [0, 0, 4, 4], status: 'unpainted' },
      { cuboidId: 'deleted', face: 'south', rect: [4, 0, 8, 4], status: 'orphan' },
    ]
    const model = modelWith({ cuboids: [cuboid('c1', 'Cabeza')], regions })
    const w = await mountCanvas(model)

    await w.get('button[aria-label="Región UV a enfocar"]').trigger('click')
    const options = w.findAll('[role="option"]').map((o) => o.text())
    expect(options).toEqual(['Todas las caras', 'Cabeza (north)'])
  })

  it('AC HU-27: el pincel pinta el color exacto, sin antialiasing, y hace UNA sola llamada a recordPatch por todo el trazo', async () => {
    const w = await mountCanvas(modelWith({ width: 8, height: 8 }))
    mockCanvasRect(8, 8) // escala 1:1
    const store = useTextureEditorStore()
    const recordSpy = vi.spyOn(store, 'recordPatch')

    await setActiveColorHex(w, '#ff0000')
    const canvas = w.get('canvas.texture-canvas__bitmap').element as HTMLCanvasElement

    dispatchPointer(canvas, 'pointerdown', 2, 2)
    dispatchPointer(canvas, 'pointermove', 3, 2)
    dispatchPointer(canvas, 'pointermove', 4, 2)
    dispatchPointer(canvas, 'pointerup', 4, 2)

    expect(recordSpy).toHaveBeenCalledTimes(1)
    expect(pixelAt(store.atlas!.pixels, 8, 2, 2)).toEqual([255, 0, 0, 255])
    expect(pixelAt(store.atlas!.pixels, 8, 4, 2)).toEqual([255, 0, 0, 255])
  })

  it('AC HU-27: el Borrador reutiliza el mismo mecanismo de trazo con alfa 0 (transparente)', async () => {
    const w = await mountCanvas(modelWith({ width: 8, height: 8 }))
    mockCanvasRect(8, 8)
    const store = useTextureEditorStore()
    // Pre-pinta un píxel rojo para tener algo que borrar.
    const before = store.readRegion({ x: 2, y: 2, width: 1, height: 1 })!
    store.recordPatch({ x: 2, y: 2, width: 1, height: 1 }, before, new Uint8ClampedArray([255, 0, 0, 255]))

    await w.get('button[aria-label="Borrador"]').trigger('click')
    const canvas = w.get('canvas.texture-canvas__bitmap').element as HTMLCanvasElement
    dispatchPointer(canvas, 'pointerdown', 2, 2)
    dispatchPointer(canvas, 'pointerup', 2, 2)

    expect(pixelAt(store.atlas!.pixels, 8, 2, 2)).toEqual([0, 0, 0, 0])
  })

  it('AC HU-27: el tamaño de pincel se mide en píxeles del ATLAS, no de pantalla -- verificado con el canvas mostrado 10x más grande (CSS)', async () => {
    const w = await mountCanvas(modelWith({ width: 8, height: 8 }))
    mockCanvasRect(80, 80) // el canvas se MUESTRA a 80x80 pero su resolución real es 8x8
    const store = useTextureEditorStore()

    await setActiveColorHex(w, '#00ff00')
    await chooseGSelectOption(w, 'Tamaño de pincel en píxeles del atlas', '8px')
    const canvas = w.get('canvas.texture-canvas__bitmap').element as HTMLCanvasElement

    // Clic en (40,40) de pantalla -> con el factor de escala 10x, corresponde al píxel de atlas (4,4).
    dispatchPointer(canvas, 'pointerdown', 40, 40)
    dispatchPointer(canvas, 'pointerup', 40, 40)

    // sizePx=8 centrado en (4,4) del ATLAS -> el propio (4,4) queda pintado.
    expect(pixelAt(store.atlas!.pixels, 8, 4, 4)[3]).toBe(255)
    expect(pixelAt(store.atlas!.pixels, 8, 0, 0)[3]).toBe(0)
    expect(pixelAt(store.atlas!.pixels, 8, 7, 7)[3]).toBe(255) // brush 8px centrado casi cubre el atlas de 8x8 entero
  })

  it('AC HU-27: la Cubeta hace flood-fill real dentro de sus límites de color, con UNA sola llamada a recordPatch', async () => {
    const w = await mountCanvas(modelWith({ width: 6, height: 6 }))
    mockCanvasRect(6, 6)
    const store = useTextureEditorStore()
    // Pre-pinta una región azul 2x2 en (1,1)-(2,2) para tener algo que rellenar.
    const before = store.readRegion({ x: 1, y: 1, width: 2, height: 2 })!
    const blue = new Uint8ClampedArray(before.length)
    for (let i = 0; i < blue.length; i += 4) {
      blue.set([0, 0, 255, 255], i)
    }
    store.recordPatch({ x: 1, y: 1, width: 2, height: 2 }, before, blue)

    const recordSpy = vi.spyOn(store, 'recordPatch')
    await w.get('button[aria-label="Cubeta"]').trigger('click')
    await setActiveColorHex(w, '#ff0000')
    const canvas = w.get('canvas.texture-canvas__bitmap').element as HTMLCanvasElement

    dispatchPointer(canvas, 'pointerdown', 1, 1)

    expect(recordSpy).toHaveBeenCalledTimes(1)
    expect(pixelAt(store.atlas!.pixels, 6, 1, 1).slice(0, 3)).toEqual([255, 0, 0])
    expect(pixelAt(store.atlas!.pixels, 6, 2, 2).slice(0, 3)).toEqual([255, 0, 0])
    // Fuera de la región azul original, el fill nunca se filtró.
    expect(pixelAt(store.atlas!.pixels, 6, 0, 0)[3]).toBe(0)
  })

  it('AC HU-27: el Eyedropper toma el color exacto del píxel clickeado, sin registrar ningún Command, y muestra un toast de confirmación visible (ticket 058)', async () => {
    const w = await mountCanvas(modelWith({ width: 4, height: 4 }))
    mockCanvasRect(4, 4)
    const store = useTextureEditorStore()
    const before = store.readRegion({ x: 2, y: 2, width: 1, height: 1 })!
    store.recordPatch({ x: 2, y: 2, width: 1, height: 1 }, before, new Uint8ClampedArray([10, 20, 30, 255]))
    const recordSpy = vi.spyOn(store, 'recordPatch')

    await w.get('button[aria-label="Selector de color (eyedropper)"]').trigger('click')
    const canvas = w.get('canvas.texture-canvas__bitmap').element as HTMLCanvasElement
    dispatchPointer(canvas, 'pointerdown', 2, 2)
    await nextTick()

    expect(w.get('.texture-color-picker__swatch-main').attributes('style')).toContain('background: rgb(10, 20, 30)')
    expect(recordSpy).not.toHaveBeenCalled()
    // AC ticket 058: confirmación VISIBLE del color capturado -- nunca un cambio silencioso.
    expect(w.get('output[aria-live="polite"]').text()).toContain('#0a141e')
  })

  it('AC HU-27: el toggle de cuadrícula agrega/quita líneas SOLO en el overlay -- nunca cambia un byte del atlas', async () => {
    // Atlas de 16x16 (> GRID_STEP_PX=8) -- con 8x8 exacto no habría ninguna línea intermedia que dibujar.
    const w = await mountCanvas(modelWith({ width: 16, height: 16 }))
    const store = useTextureEditorStore()
    const before = store.atlas!.pixels.slice()

    expect(w.find('.texture-canvas__grid').exists()).toBe(false)
    await w.get('button[aria-label="Cuadrícula"]').trigger('click')

    expect(w.findAll('.texture-canvas__grid line').length).toBeGreaterThan(0)
    expect(store.atlas!.pixels).toEqual(before)

    await w.get('button[aria-label="Cuadrícula"]').trigger('click')
    expect(w.find('.texture-canvas__grid').exists()).toBe(false)
    expect(store.atlas!.pixels).toEqual(before)
  })

  it('HU-26: tras un trazo, la textura 3D vinculada al mismo ThreeViewportService singleton refleja el cambio (mismo buffer, versión incrementada) -- sin instanciar un viewport nuevo', async () => {
    const model = modelWith({ width: 8, height: 8, cuboids: [cuboid('c1', 'Cabeza')] })
    const w = await mountCanvas(model)
    mockCanvasRect(8, 8)
    const store = useTextureEditorStore()
    const canvas = w.get('canvas.texture-canvas__bitmap').element as HTMLCanvasElement

    const mobGroup = threeViewportService.scene.children.find((c) => c.name === model.name)!
    const mesh = mobGroup.children.find((c) => c.userData.cuboidId === 'c1') as Mesh
    // Ticket 049: `mesh.material` es un array de 6 slots (misma instancia repetida).
    const material = (mesh.material as MeshStandardMaterial[])[0]!
    const textureBefore = material.map!
    const versionBefore = textureBefore.version

    dispatchPointer(canvas, 'pointerdown', 1, 1)
    dispatchPointer(canvas, 'pointerup', 1, 1)

    expect(material.map).toBe(textureBefore) // mismo objeto Texture -- no se reconstruyó la escena por cada trazo
    expect((material.map as unknown as { image: { data: Uint8ClampedArray } }).image.data).toBe(store.atlas!.pixels)
    expect(material.map!.version).toBeGreaterThan(versionBefore)
  })

  it('AC: cada herramienta produce sus cambios como TexturePatchCommand -- Undo revierte tanto un trazo de pincel como un fill', async () => {
    const w = await mountCanvas(modelWith({ width: 8, height: 8 }))
    mockCanvasRect(8, 8)
    const store = useTextureEditorStore()
    await setActiveColorHex(w, '#ff0000')
    const canvas = w.get('canvas.texture-canvas__bitmap').element as HTMLCanvasElement

    dispatchPointer(canvas, 'pointerdown', 2, 2)
    dispatchPointer(canvas, 'pointerup', 2, 2)
    expect(store.canUndo).toBe(true)

    store.undo()
    expect(pixelAt(store.atlas!.pixels, 8, 2, 2)).toEqual([0, 0, 0, 0])
  })

  it('ticket 049 (HU-25): clic en el preview 3D resuelve la cara vía pickCuboidFaceAt y resalta la región UV correspondiente en el editor 2D', async () => {
    const region: UvRegion = { cuboidId: 'c1', face: 'north', rect: [0, 0, 4, 4], status: 'unpainted' }
    const model = modelWith({ cuboids: [cuboid('c1', 'Cabeza')], regions: [region] })
    const w = await mountCanvas(model)
    vi.spyOn(threeViewportService, 'pickCuboidFaceAt').mockReturnValue({ cuboidId: 'c1', face: 'north' })

    const preview = w.get('.texture-canvas__preview').element
    dispatchPointer(preview, 'pointerdown', 5, 5)
    preview.dispatchEvent(new MouseEvent('click', { clientX: 5, clientY: 5, bubbles: true }))
    await nextTick()

    await w.get('button[aria-label="Región UV a enfocar"]').trigger('click')
    expect(w.findAll('[role="option"]').find((o) => o.classes().includes('g-select__option--active'))?.text()).toBe('Cabeza (north)')
    expect(w.get('rect.texture-canvas__region').classes()).toContain('texture-canvas__region--selected')
  })

  it('ticket 049 (HU-25): un clic que no resuelve ninguna cara (pickCuboidFaceAt -> null) deselecciona la región enfocada', async () => {
    const region: UvRegion = { cuboidId: 'c1', face: 'north', rect: [0, 0, 4, 4], status: 'unpainted' }
    const model = modelWith({ cuboids: [cuboid('c1', 'Cabeza')], regions: [region] })
    const w = await mountCanvas(model)
    await chooseGSelectOption(w, 'Región UV a enfocar', 'Cabeza (north)')
    vi.spyOn(threeViewportService, 'pickCuboidFaceAt').mockReturnValue(null)

    const preview = w.get('.texture-canvas__preview').element
    dispatchPointer(preview, 'pointerdown', 5, 5)
    preview.dispatchEvent(new MouseEvent('click', { clientX: 5, clientY: 5, bubbles: true }))
    await nextTick()

    expect(w.get('button[aria-label="Región UV a enfocar"]').text()).toBe('Todas las caras')
  })

  it('ticket 049: un arrastre de órbita (drag > umbral) en el preview 3D nunca dispara la selección de cara -- mismo criterio click-vs-drag que ThreeViewport.vue', async () => {
    const model = modelWith({ cuboids: [cuboid('c1', 'Cabeza')] })
    const w = await mountCanvas(model)
    const pickSpy = vi.spyOn(threeViewportService, 'pickCuboidFaceAt')

    const preview = w.get('.texture-canvas__preview').element
    dispatchPointer(preview, 'pointerdown', 5, 5)
    preview.dispatchEvent(new MouseEvent('click', { clientX: 50, clientY: 50, bubbles: true }))

    expect(pickSpy).not.toHaveBeenCalled()
  })

  it('ticket 049 (HU-25/HU-26): elegir una región UV en el dropdown del editor 2D resalta la cara correspondiente en el preview 3D (FACE_HIGHLIGHT_NAME)', async () => {
    const region: UvRegion = { cuboidId: 'c1', face: 'north', rect: [0, 0, 4, 4], status: 'unpainted' }
    const model = modelWith({ cuboids: [cuboid('c1', 'Cabeza')], regions: [region] })
    const w = await mountCanvas(model)

    await chooseGSelectOption(w, 'Región UV a enfocar', 'Cabeza (north)')

    const mobGroup = threeViewportService.scene.children.find((c) => c.name === model.name)!
    const mesh = mobGroup.children.find((c) => c.userData.cuboidId === 'c1')!
    expect(mesh.children.some((c) => c.name === FACE_HIGHLIGHT_NAME)).toBe(true)
  })

  it('ticket 049: volver a "Todas las caras" quita el highlight de la cara en el preview 3D', async () => {
    const region: UvRegion = { cuboidId: 'c1', face: 'north', rect: [0, 0, 4, 4], status: 'unpainted' }
    const model = modelWith({ cuboids: [cuboid('c1', 'Cabeza')], regions: [region] })
    const w = await mountCanvas(model)
    await chooseGSelectOption(w, 'Región UV a enfocar', 'Cabeza (north)')

    await chooseGSelectOption(w, 'Región UV a enfocar', 'Todas las caras')

    const mobGroup = threeViewportService.scene.children.find((c) => c.name === model.name)!
    const mesh = mobGroup.children.find((c) => c.userData.cuboidId === 'c1')!
    expect(mesh.children.some((c) => c.name === FACE_HIGHLIGHT_NAME)).toBe(false)
  })

  it('ticket 049: textureSelectionStore.ts es un store separado -- NO expone selectedCuboidId/select() del selectionStore.ts (017/031/036)', async () => {
    await mountCanvas(modelWith())
    const store = useTextureSelectionStore()

    expect(store.selectedFace).toBeNull()
    expect('selectedCuboidId' in store).toBe(false)
    expect('select' in store).toBe(false)
  })

  it('a11y: todos los controles de herramientas tienen aria-label, y el canvas del atlas tiene un nombre accesible', async () => {
    const w = await mountCanvas(modelWith({ width: 8, height: 8 }))

    for (const label of ['Pincel', 'Borrador', 'Cubeta', 'Selector de color (eyedropper)', 'Cuadrícula']) {
      expect(w.find(`button[aria-label="${label}"]`).exists()).toBe(true)
    }
    expect(w.get('canvas.texture-canvas__bitmap').attributes('aria-label')).toBeTruthy()
    // .get() ya lanza si no existe -- llegar hasta acá sin excepción es la aserción real.
    w.get('button[aria-label="Color activo"]')
    w.get('button[aria-label="Tamaño de pincel en píxeles del atlas"]')
    w.get('button[aria-label="Región UV a enfocar"]')
  })

  it('AC ticket 058: ningún <select> nativo aparece en el DOM de esta pantalla', async () => {
    const region: UvRegion = { cuboidId: 'c1', face: 'north', rect: [0, 0, 4, 4], status: 'unpainted' }
    const model = modelWith({ cuboids: [cuboid('c1', 'Cabeza')], regions: [region] })
    const w = await mountCanvas(model)

    // Abre todos los dropdowns de la pantalla para asegurar que ni siquiera
    // su contenido desplegado inyecta un <select> nativo en algún momento.
    await w.get('button[aria-label="Región UV a enfocar"]').trigger('click')
    await w.get('button[aria-label="Tamaño de pincel en píxeles del atlas"]').trigger('click')
    await w.get('button[aria-label="Nivel de zoom del lienzo"]').trigger('click')
    await w.get('button[aria-label="Color activo"]').trigger('click')

    expect(w.find('select').exists()).toBe(false)
  })

  describe('ticket 058: layout en 3 zonas (toolbar horizontal / lienzo dominante / preview 3D secundario)', () => {
    it('nunca renderiza la columna lateral izquierda del diseño anterior (ticket 047/050)', async () => {
      const w = await mountCanvas(modelWith())
      expect(w.find('.texture-canvas__tools').exists()).toBe(false)
    })

    it('muestra las 3 zonas: toolbar arriba, lienzo dominante, preview 3D secundario', async () => {
      const w = await mountCanvas(modelWith())
      expect(w.find('.texture-canvas__toolbar').exists()).toBe(true)
      expect(w.find('.texture-canvas__canvas-panel').exists()).toBe(true)
      expect(w.find('.texture-canvas__preview-panel').exists()).toBe(true)
    })
  })

  describe('ticket 058: zoom y pan reales', () => {
    it('el zoom por defecto es 100% y afecta el tamaño del stage, nunca el resto de la pantalla', async () => {
      const w = await mountCanvas(modelWith({ width: 8, height: 8 }))
      const stage = w.get('.texture-canvas__stage')
      const widthAt100 = Number.parseInt((stage.attributes('style') ?? '').match(/width:\s*(\d+)px/)?.[1] ?? '0', 10)

      await w.get('button[aria-label="Acercar zoom"]').trigger('click')

      const widthAfter = Number.parseInt((w.get('.texture-canvas__stage').attributes('style') ?? '').match(/width:\s*(\d+)px/)?.[1] ?? '0', 10)
      expect(widthAfter).toBeGreaterThan(widthAt100)
      // El resto del layout (toolbar/preview) no tiene ningún tamaño ligado al zoom.
      expect(w.get('.texture-canvas__toolbar').attributes('style')).toBeUndefined()
    })

    it('elegir un preset de zoom del dropdown fija exactamente ese porcentaje', async () => {
      const w = await mountCanvas(modelWith({ width: 8, height: 8 }))
      await chooseGSelectOption(w, 'Nivel de zoom del lienzo', '400%')

      expect(w.get('.texture-canvas__statusbar').text()).toContain('400%')
    })

    it('el atajo de teclado "0" restablece el zoom a 100%', async () => {
      const w = await mountCanvas(modelWith({ width: 8, height: 8 }))
      await chooseGSelectOption(w, 'Nivel de zoom del lienzo', '400%')

      window.dispatchEvent(new KeyboardEvent('keydown', { key: '0' }))
      await nextTick()

      expect(w.get('.texture-canvas__statusbar').text()).toContain('100%')
    })

    it('el atajo de teclado "+" acerca el zoom', async () => {
      const w = await mountCanvas(modelWith({ width: 8, height: 8 }))

      window.dispatchEvent(new KeyboardEvent('keydown', { key: '+' }))
      await nextTick()

      expect(w.get('.texture-canvas__statusbar').text()).toContain('150%')
    })

    it('el zoom permite llegar a niveles altos de ampliación (hasta 1600%)', async () => {
      const w = await mountCanvas(modelWith({ width: 8, height: 8 }))
      await chooseGSelectOption(w, 'Nivel de zoom del lienzo', '1600%')

      expect(w.get('.texture-canvas__statusbar').text()).toContain('1600%')
    })

    it('Ctrl+rueda hace zoom centrado en el cursor (nunca un scroll nativo sin modificador)', async () => {
      const w = await mountCanvas(modelWith({ width: 8, height: 8 }))
      const viewport = w.get('.texture-canvas__viewport').element

      viewport.dispatchEvent(new WheelEvent('wheel', { deltaY: -100, ctrlKey: true, clientX: 10, clientY: 10, bubbles: true, cancelable: true }))
      await nextTick()

      expect(w.get('.texture-canvas__statusbar').text()).toContain('112%')
    })

    it('la resolución intrínseca del canvas nunca cambia con el zoom (pixel-perfect: 1 celda del atlas = 1 pixel real, nunca escalado/interpolado)', async () => {
      const w = await mountCanvas(modelWith({ width: 8, height: 8 }))
      const canvas = w.get('canvas.texture-canvas__bitmap').element as HTMLCanvasElement
      expect(canvas.width).toBe(8)
      expect(canvas.height).toBe(8)

      await chooseGSelectOption(w, 'Nivel de zoom del lienzo', '1600%')

      // El zoom solo cambia el tamaño VISIBLE (CSS del stage) -- la
      // resolución intrínseca del bitmap sigue siendo exactamente la del
      // atlas, así que cada pixel real del atlas se agranda por CSS
      // (`image-rendering: pixelated`, ya declarado en el scoped style de
      // este componente) sin ningún suavizado/interpolación.
      expect(canvas.width).toBe(8)
      expect(canvas.height).toBe(8)
    })
  })

  describe('ticket 068: botón de mano (mover el lienzo con un click, sin depender solo de la barra espaciadora)', () => {
    it('con la barra espaciadora mantenida (comportamiento ya existente), arrastrar sobre el viewport hace scroll -- nunca pinta', async () => {
      const w = await mountCanvas(modelWith({ width: 8, height: 8 }))
      const viewport = w.get('.texture-canvas__viewport').element as HTMLDivElement
      Object.defineProperty(viewport, 'setPointerCapture', { value: vi.fn() })
      window.dispatchEvent(new KeyboardEvent('keydown', { code: 'Space' }))
      await nextTick()
      expect(w.get('.texture-canvas__viewport').classes()).toContain('texture-canvas__viewport--pan-ready')

      dispatchPointer(viewport, 'pointerdown', 100, 100)
      dispatchPointer(viewport, 'pointermove', 60, 100)

      expect(viewport.scrollLeft).toBe(40) // se movió por pan -- no se registró ningún trazo de pintado
    })

    it('el botón de mano activa el modo pan con un click (sin necesitar la barra espaciadora), y arrastrar sobre el lienzo mueve el scroll en vez de pintar', async () => {
      const w = await mountCanvas(modelWith({ width: 8, height: 8 }))
      const viewport = w.get('.texture-canvas__viewport').element as HTMLDivElement
      Object.defineProperty(viewport, 'setPointerCapture', { value: vi.fn() })

      await w.get('button[aria-label="Mover lienzo (mano)"]').trigger('click')
      expect(w.get('button[aria-label="Mover lienzo (mano)"]').classes()).toContain('icon-button--active')
      expect(w.get('.texture-canvas__viewport').classes()).toContain('texture-canvas__viewport--pan-ready')

      dispatchPointer(viewport, 'pointerdown', 100, 100)
      dispatchPointer(viewport, 'pointermove', 60, 100)

      expect(viewport.scrollLeft).toBe(40)

      // Mientras la mano está activa, el canvas de pintado tampoco debe
      // registrar ningún trazo -- mismo criterio que la barra espaciadora.
      mockCanvasRect(8, 8)
      const canvas = w.get('canvas.texture-canvas__bitmap').element as HTMLCanvasElement
      const beforePixels = useTextureEditorStore().atlas!.pixels.slice()
      dispatchPointer(canvas, 'pointerdown', 2, 2)
      dispatchPointer(canvas, 'pointerup', 2, 2)
      expect(useTextureEditorStore().atlas!.pixels).toEqual(beforePixels) // sin cambios -- no pintó
    })

    it('clickear el botón de mano de nuevo desactiva el modo pan', async () => {
      const w = await mountCanvas(modelWith({ width: 8, height: 8 }))
      await w.get('button[aria-label="Mover lienzo (mano)"]').trigger('click')
      await w.get('button[aria-label="Mover lienzo (mano)"]').trigger('click')

      expect(w.get('button[aria-label="Mover lienzo (mano)"]').classes()).not.toContain('icon-button--active')
      expect(w.get('.texture-canvas__viewport').classes()).not.toContain('texture-canvas__viewport--pan-ready')
    })
  })

  describe('deshacer/rehacer (hallazgo real: el ticket 068 documentó este botón como hecho, pero nunca se cableó -- textureEditorStore.undo()/redo()/canUndo/canRedo ya existían desde el 046, sin ningún botón/atajo)', () => {
    it('arrancan deshabilitados (sin ningún patch todavía)', async () => {
      const w = await mountCanvas(modelWith({ width: 8, height: 8 }))

      expect(w.get('button[aria-label="Deshacer"]').attributes('disabled')).toBeDefined()
      expect(w.get('button[aria-label="Rehacer"]').attributes('disabled')).toBeDefined()
    })

    it('tras un patch, "Deshacer" se habilita y clickearlo revierte el atlas al estado anterior', async () => {
      const w = await mountCanvas(modelWith({ width: 8, height: 8 }))
      const store = useTextureEditorStore()
      const before = store.readRegion({ x: 2, y: 2, width: 1, height: 1 })!
      store.recordPatch({ x: 2, y: 2, width: 1, height: 1 }, before, new Uint8ClampedArray([255, 0, 0, 255]))
      await nextTick()

      expect(w.get('button[aria-label="Deshacer"]').attributes('disabled')).toBeUndefined()
      expect(pixelAt(store.atlas!.pixels, 8, 2, 2)).toEqual([255, 0, 0, 255])

      await w.get('button[aria-label="Deshacer"]').trigger('click')

      expect(pixelAt(store.atlas!.pixels, 8, 2, 2)).toEqual(Array.from(before))
    })

    it('tras deshacer, "Rehacer" se habilita y clickearlo vuelve a aplicar el patch', async () => {
      const w = await mountCanvas(modelWith({ width: 8, height: 8 }))
      const store = useTextureEditorStore()
      const before = store.readRegion({ x: 2, y: 2, width: 1, height: 1 })!
      store.recordPatch({ x: 2, y: 2, width: 1, height: 1 }, before, new Uint8ClampedArray([255, 0, 0, 255]))
      await nextTick()
      await w.get('button[aria-label="Deshacer"]').trigger('click')

      expect(w.get('button[aria-label="Rehacer"]').attributes('disabled')).toBeUndefined()
      await w.get('button[aria-label="Rehacer"]').trigger('click')

      expect(pixelAt(store.atlas!.pixels, 8, 2, 2)).toEqual([255, 0, 0, 255])
    })

    it('Ctrl/Cmd+Z deshace y Ctrl/Cmd+Shift+Z rehace, mismo atajo que EditorToolbar.vue (Modelo)', async () => {
      await mountCanvas(modelWith({ width: 8, height: 8 }))
      const store = useTextureEditorStore()
      const before = store.readRegion({ x: 2, y: 2, width: 1, height: 1 })!
      store.recordPatch({ x: 2, y: 2, width: 1, height: 1 }, before, new Uint8ClampedArray([255, 0, 0, 255]))

      window.dispatchEvent(new KeyboardEvent('keydown', { key: 'z', ctrlKey: true }))
      expect(pixelAt(store.atlas!.pixels, 8, 2, 2)).toEqual(Array.from(before))

      window.dispatchEvent(new KeyboardEvent('keydown', { key: 'z', ctrlKey: true, shiftKey: true }))
      expect(pixelAt(store.atlas!.pixels, 8, 2, 2)).toEqual([255, 0, 0, 255])
    })

    it('Deshacer/Rehacer sincronizan el <canvas> visible y el preview 3D, no solo el store -- hallazgo real de la verificación en vivo: el store cambiaba pero la pantalla se quedaba mostrando el estado viejo', async () => {
      const w = await mountCanvas(modelWith({ width: 8, height: 8 }))
      const store = useTextureEditorStore()
      const before = store.readRegion({ x: 2, y: 2, width: 1, height: 1 })!
      store.recordPatch({ x: 2, y: 2, width: 1, height: 1 }, before, new Uint8ClampedArray([255, 0, 0, 255]))
      await nextTick()

      // `markDirty()` viaja SIEMPRE junto con `syncDataTexture()`/`redraw()`
      // en el resto del componente (ver `finishStroke`/fill) -- si
      // `saveState` pasa a 'dirty' acá, confirma que Deshacer/Rehacer pasan
      // por el mismo camino real, no solo por `textureEditorStore.undo()`
      // aislado (que por sí solo NUNCA vuelve a pintar el <canvas>/preview).
      await w.get('button[aria-label="Deshacer"]').trigger('click')
      expect(w.vm.saveState).toBe('dirty')

      await w.get('button[aria-label="Rehacer"]').trigger('click')
      expect(w.vm.saveState).toBe('dirty')
    })

    it('Ctrl/Cmd+Z con el foco en un campo de texto real (ej. el textarea del Asistente IA) no deshace nada', async () => {
      await mountCanvas(modelWith({ width: 8, height: 8 }))
      const store = useTextureEditorStore()
      const before = store.readRegion({ x: 2, y: 2, width: 1, height: 1 })!
      store.recordPatch({ x: 2, y: 2, width: 1, height: 1 }, before, new Uint8ClampedArray([255, 0, 0, 255]))
      const input = document.createElement('input')
      document.body.appendChild(input)
      input.focus()

      input.dispatchEvent(new KeyboardEvent('keydown', { key: 'z', ctrlKey: true, bubbles: true }))

      expect(pixelAt(store.atlas!.pixels, 8, 2, 2)).toEqual([255, 0, 0, 255])
      document.body.removeChild(input)
    })
  })

  describe('ticket 068: separador arrastrable entre el lienzo y el preview 3D', () => {
    it('arranca en el ancho por defecto (320px) y el separador está presente', async () => {
      const w = await mountCanvas(modelWith({ width: 8, height: 8 }))
      expect(w.get('.texture-canvas__preview-panel').attributes('style')).toContain('width: 320px')
      expect(w.find('.texture-canvas__splitter').exists()).toBe(true)
    })

    it('arrastrar el separador hacia la izquierda agranda el panel de preview', async () => {
      const w = await mountCanvas(modelWith({ width: 8, height: 8 }))
      const splitter = w.get('.texture-canvas__splitter').element as HTMLDivElement
      Object.defineProperty(splitter, 'setPointerCapture', { value: vi.fn() })
      Object.defineProperty(splitter, 'hasPointerCapture', { value: vi.fn().mockReturnValue(true) })
      Object.defineProperty(splitter, 'releasePointerCapture', { value: vi.fn() })

      dispatchPointer(splitter, 'pointerdown', 500, 100)
      dispatchPointer(splitter, 'pointermove', 440, 100) // 60px hacia la izquierda -> +60px de preview
      dispatchPointer(splitter, 'pointerup', 440, 100)
      await nextTick()

      expect(w.get('.texture-canvas__preview-panel').attributes('style')).toContain('width: 380px')
    })

    it('el ancho del preview nunca baja de 200px ni sube de 560px, sin importar cuánto se arrastre', async () => {
      const w = await mountCanvas(modelWith({ width: 8, height: 8 }))
      const splitter = w.get('.texture-canvas__splitter').element as HTMLDivElement
      Object.defineProperty(splitter, 'setPointerCapture', { value: vi.fn() })

      dispatchPointer(splitter, 'pointerdown', 500, 100)
      dispatchPointer(splitter, 'pointermove', 2000, 100) // arrastre extremo hacia la derecha -> achicaría de más
      await nextTick()
      expect(w.get('.texture-canvas__preview-panel').attributes('style')).toContain('width: 200px')

      dispatchPointer(splitter, 'pointermove', -2000, 100) // arrastre extremo hacia la izquierda -> agrandaría de más
      await nextTick()
      expect(w.get('.texture-canvas__preview-panel').attributes('style')).toContain('width: 560px')
    })

    it('el separador es operable por teclado (patrón WAI-ARIA "Separator (Focusable)") -- flecha izquierda agranda el preview, derecha lo achica, con los mismos límites', async () => {
      const w = await mountCanvas(modelWith({ width: 8, height: 8 }))
      const splitter = w.get('.texture-canvas__splitter')
      expect(splitter.attributes('role')).toBe('separator')
      expect(splitter.attributes('aria-valuenow')).toBe('320')
      expect(splitter.attributes('aria-valuemin')).toBe('200')
      expect(splitter.attributes('aria-valuemax')).toBe('560')

      await splitter.trigger('keydown', { key: 'ArrowLeft' })
      expect(w.get('.texture-canvas__preview-panel').attributes('style')).toContain('width: 340px')
      expect(splitter.attributes('aria-valuenow')).toBe('340')

      await splitter.trigger('keydown', { key: 'ArrowRight' })
      await splitter.trigger('keydown', { key: 'ArrowRight' })
      expect(w.get('.texture-canvas__preview-panel').attributes('style')).toContain('width: 300px')
    })
  })

  describe('ticket 058: guardado con indicador de 4 estados (reutiliza el flush de 056)', () => {
    it('arranca en estado "Guardado" al cargar el atlas', async () => {
      const w = await mountCanvas(modelWith({ width: 8, height: 8 }))
      expect(w.get('.texture-save-status').text()).toBe('Guardado')
    })

    it('pintar un trazo marca el estado como "Cambios sin guardar"', async () => {
      const w = await mountCanvas(modelWith({ width: 8, height: 8 }))
      mockCanvasRect(8, 8)
      const canvas = w.get('canvas.texture-canvas__bitmap').element as HTMLCanvasElement

      dispatchPointer(canvas, 'pointerdown', 2, 2)
      dispatchPointer(canvas, 'pointerup', 2, 2)
      await nextTick()

      expect(w.get('.texture-save-status').text()).toBe('Cambios sin guardar')
    })

    it('Guardar exitoso (invocado vía handleSave expuesto -- ticket 068: el botón sube a MobEditor.vue) termina en "Guardado", reutilizando flushPaintedTexture + saveRevision', async () => {
      const { saveRevision } = await import('../../draftPersistenceApi')
      vi.mocked(saveRevision).mockResolvedValue({ created: true, revisionNumber: 1, reason: null })
      const { uploadThumbnail } = await import('../../thumbnailApi')
      vi.mocked(uploadThumbnail).mockResolvedValue(undefined)
      // Ticket 068: a diferencia de un `trigger('click')` (fire-and-forget
      // desde el punto de vista del test), acá se espera la promesa de
      // `handleSave()` COMPLETA -- si `captureThumbnail` quedara sin
      // mockear, `canvas.toBlob` (no implementado en jsdom) la dejaría
      // pendiente para siempre y el test colgaría hasta el timeout.
      vi.spyOn(threeViewportService, 'captureThumbnail').mockResolvedValue(new Blob(['png'], { type: 'image/png' }))

      const w = await mountCanvas(modelWith({ width: 8, height: 8 }))
      mockCanvasRect(8, 8)
      const canvas = w.get('canvas.texture-canvas__bitmap').element as HTMLCanvasElement
      dispatchPointer(canvas, 'pointerdown', 2, 2)
      dispatchPointer(canvas, 'pointerup', 2, 2)
      await nextTick()
      expect(w.get('.texture-save-status').text()).toBe('Cambios sin guardar')

      await w.vm.handleSave()
      await flushPromises()

      expect(saveRevision).toHaveBeenCalled()
      expect(w.get('.texture-save-status').text()).toBe('Guardado')
    })

    it('un fallo real durante Guardar deja el estado en "Error al guardar"', async () => {
      const { saveRevision } = await import('../../draftPersistenceApi')
      vi.mocked(saveRevision).mockRejectedValue(new Error('El draft no pasa la validación.'))

      const w = await mountCanvas(modelWith({ width: 8, height: 8 }))
      mockCanvasRect(8, 8)
      const canvas = w.get('canvas.texture-canvas__bitmap').element as HTMLCanvasElement
      dispatchPointer(canvas, 'pointerdown', 2, 2)
      dispatchPointer(canvas, 'pointerup', 2, 2)
      await nextTick()

      await w.vm.handleSave()
      await flushPromises()

      expect(w.get('.texture-save-status').text()).toBe('Error al guardar')
    })
  })

  describe('ticket 048 -- import de PNG (región seleccionada y atlas completo)', () => {
    beforeEach(() => mockDecode.mockReset())

    it('AC B (atlas completo): con "Todas las caras" seleccionado (default), el destino del import es el atlas completo -- un solo TexturePatchCommand con rect = atlas completo', async () => {
      const w = await mountCanvas(modelWith({ width: 4, height: 4 }))
      mockDecode.mockResolvedValue({ pixels: solidPixels(4, 4, [9, 9, 9, 255]), width: 4, height: 4 })
      const store = useTextureEditorStore()
      const recordSpy = vi.spyOn(store, 'recordPatch')

      await selectImportFile(w, FAKE_PNG_FILE)
      const confirmButton = w.findAll('button').find((b) => b.text() === 'Confirmar import')!
      await confirmButton.trigger('click')

      expect(recordSpy).toHaveBeenCalledTimes(1)
      expect(recordSpy).toHaveBeenCalledWith({ x: 0, y: 0, width: 4, height: 4 }, expect.any(Uint8ClampedArray), expect.any(Uint8ClampedArray))
      expect(pixelAt(store.atlas!.pixels, 4, 0, 0)).toEqual([9, 9, 9, 255])
      expect(pixelAt(store.atlas!.pixels, 4, 3, 3)).toEqual([9, 9, 9, 255])
    })

    it('AC A (región seleccionada): al elegir una región específica, el import queda ACOTADO a esa región -- el resto del atlas no cambia', async () => {
      const region: UvRegion = { cuboidId: 'c1', face: 'north', rect: [1, 1, 3, 3], status: 'unpainted' }
      const model = modelWith({ width: 6, height: 6, cuboids: [cuboid('c1', 'Cabeza')], regions: [region] })
      const w = await mountCanvas(model)
      await chooseGSelectOption(w, 'Región UV a enfocar', 'Cabeza (north)')
      mockDecode.mockResolvedValue({ pixels: solidPixels(2, 2, [200, 0, 0, 255]), width: 2, height: 2 })
      const store = useTextureEditorStore()
      const recordSpy = vi.spyOn(store, 'recordPatch')

      await selectImportFile(w, FAKE_PNG_FILE)
      const confirmButton = w.findAll('button').find((b) => b.text() === 'Confirmar import')!
      await confirmButton.trigger('click')

      expect(recordSpy).toHaveBeenCalledTimes(1)
      expect(recordSpy).toHaveBeenCalledWith({ x: 1, y: 1, width: 2, height: 2 }, expect.any(Uint8ClampedArray), expect.any(Uint8ClampedArray))
      expect(pixelAt(store.atlas!.pixels, 6, 1, 1)).toEqual([200, 0, 0, 255])
      expect(pixelAt(store.atlas!.pixels, 6, 2, 2)).toEqual([200, 0, 0, 255])
      // Fuera de la región: intacto.
      expect(pixelAt(store.atlas!.pixels, 6, 0, 0)).toEqual([0, 0, 0, 0])
      expect(pixelAt(store.atlas!.pixels, 6, 5, 5)).toEqual([0, 0, 0, 0])
    })

    it('AC B (dimensiones distintas + atlas congelado, ticket 042): con una región PAINTED existente, importar un PNG de otro tamaño hace crop/pad hacia las dimensiones VIGENTES del atlas -- nunca las cambia', async () => {
      const region: UvRegion = { cuboidId: 'c1', face: 'north', rect: [0, 0, 4, 4], status: 'painted' }
      const model = modelWith({ width: 4, height: 4, cuboids: [cuboid('c1', 'Cabeza')], regions: [region] })
      const w = await mountCanvas(model) // selector queda en "Todas las caras" -> destino = atlas completo
      mockDecode.mockResolvedValue({ pixels: solidPixels(10, 10, [5, 5, 5, 255]), width: 10, height: 10 }) // más grande que el atlas congelado
      const store = useTextureEditorStore()

      await selectImportFile(w, FAKE_PNG_FILE)
      expect(w.text()).toContain('recortará')
      const confirmButton = w.findAll('button').find((b) => b.text() === 'Confirmar import')!
      await confirmButton.trigger('click')

      // El atlas SIGUE siendo 4x4 -- el import nunca lo creció ni lo redujo.
      expect(store.atlas!.width).toBe(4)
      expect(store.atlas!.height).toBe(4)
      expect(pixelAt(store.atlas!.pixels, 4, 0, 0)).toEqual([5, 5, 5, 255])
    })

    it('sin confirmación explícita, el atlas/región permanece sin cambios (A o B)', async () => {
      const w = await mountCanvas(modelWith({ width: 4, height: 4 }))
      mockDecode.mockResolvedValue({ pixels: solidPixels(4, 4, [1, 1, 1, 1]), width: 4, height: 4 })
      const store = useTextureEditorStore()
      const before = store.atlas!.pixels.slice()

      await selectImportFile(w, FAKE_PNG_FILE)
      // No se hace click en Confirmar -- el atlas debe seguir intacto.
      expect(store.atlas!.pixels).toEqual(before)

      const cancelButton = w.findAll('button').find((b) => b.text() === 'Cancelar')!
      await cancelButton.trigger('click')
      expect(store.atlas!.pixels).toEqual(before)
    })

    it('a11y: el botón de importar tiene texto visible y el input de archivo tiene aria-label', async () => {
      const w = await mountCanvas(modelWith({ width: 4, height: 4 }))
      expect(w.find('input[aria-label="Archivo PNG a importar"]').exists()).toBe(true)
      expect(w.findAll('button').some((b) => b.text().includes('Importar PNG'))).toBe(true)
    })
  })
})
