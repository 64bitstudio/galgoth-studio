import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import type { Mesh, MeshStandardMaterial } from 'three'
import { nextTick } from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { Cuboid, MobProjectModel, UvRegion } from '../../../domain/MobProjectModel'
import { FACE_HIGHLIGHT_NAME } from '../../../viewport/buildMobScene'
import { ALL_REGIONS_VALUE } from '../regionLabels'
import { useTextureEditorStore } from '../textureEditorStore'
import { useTextureSelectionStore } from '../textureSelectionStore'

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
  decodePngFileToAtlasBuffer: vi.fn(),
  PngDecodeError: class PngDecodeError extends Error {},
}))

const { threeViewportService } = await import('../../../viewport/ThreeViewportService')
const { default: TextureCanvas } = await import('../TextureCanvas.vue')
const { decodePngFileToAtlasBuffer } = await import('../pngImportDecode')
const mockDecode = vi.mocked(decodePngFileToAtlasBuffer)

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

function modelWith(opts: { width?: number; height?: number; cuboids?: Cuboid[]; regions?: UvRegion[] } = {}): MobProjectModel {
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
    texture: { width, height, storageKey: null },
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

describe('TextureCanvas.vue', () => {
  let wrapper: ReturnType<typeof mount> | null = null

  beforeEach(() => {
    setActivePinia(createPinia())
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = null
    threeViewportService.stopRenderLoop()
    vi.restoreAllMocks()
  })

  function mountCanvas(model: MobProjectModel): ReturnType<typeof mount> {
    wrapper = mount(TextureCanvas, { props: { model } })
    return wrapper
  }

  it('AC HU-24: al montar, carga el atlas del store con las dimensiones REALES del modelo -- nunca bloqueado (mismo criterio que 034, con o sin revisión guardada)', () => {
    mountCanvas(modelWith({ width: 16, height: 12 }))
    const store = useTextureEditorStore()

    expect(store.atlas?.width).toBe(16)
    expect(store.atlas?.height).toBe(12)
  })

  it('AC HU-24: el selector de región lista "Todas las caras" + una opción por región, etiquetada "${cuboid.name} (${face})"', () => {
    const region: UvRegion = { cuboidId: 'c1', face: 'north', rect: [0, 0, 4, 4], status: 'unpainted' }
    const model = modelWith({ cuboids: [cuboid('c1', 'Cabeza')], regions: [region] })
    const w = mountCanvas(model)

    const options = w.get('select[aria-label="Región UV a enfocar"]').findAll('option').map((o) => o.text())

    expect(options).toEqual(['Todas las caras', 'Cabeza (north)'])
  })

  it('AC HU-24: elegir una región la resalta (clase --selected) en el overlay -- "Todas las caras" no resalta ninguna', async () => {
    const region: UvRegion = { cuboidId: 'c1', face: 'north', rect: [0, 0, 4, 4], status: 'unpainted' }
    const model = modelWith({ cuboids: [cuboid('c1', 'Cabeza')], regions: [region] })
    const w = mountCanvas(model)

    expect(w.get('rect.texture-canvas__region').classes()).not.toContain('texture-canvas__region--selected')

    await w.get('select[aria-label="Región UV a enfocar"]').setValue('c1:north')

    expect(w.get('rect.texture-canvas__region').classes()).toContain('texture-canvas__region--selected')
  })

  it('AC HU-24: excluye regiones ORPHAN del selector (su cuboid ya no existe)', () => {
    const regions: UvRegion[] = [
      { cuboidId: 'c1', face: 'north', rect: [0, 0, 4, 4], status: 'unpainted' },
      { cuboidId: 'deleted', face: 'south', rect: [4, 0, 8, 4], status: 'orphan' },
    ]
    const model = modelWith({ cuboids: [cuboid('c1', 'Cabeza')], regions })
    const w = mountCanvas(model)

    const options = w.get('select[aria-label="Región UV a enfocar"]').findAll('option').map((o) => o.text())
    expect(options).toEqual(['Todas las caras', 'Cabeza (north)'])
  })

  it('AC HU-27: el pincel pinta el color exacto, sin antialiasing, y hace UNA sola llamada a recordPatch por todo el trazo', async () => {
    const w = mountCanvas(modelWith({ width: 8, height: 8 }))
    mockCanvasRect(8, 8) // escala 1:1
    const store = useTextureEditorStore()
    const recordSpy = vi.spyOn(store, 'recordPatch')

    await w.get('input[aria-label="Color activo"]').setValue('#ff0000')
    await w.get('input[aria-label="Tamaño de pincel en píxeles del atlas"]').setValue(1)
    const canvas = w.get('canvas').element as HTMLCanvasElement

    dispatchPointer(canvas, 'pointerdown', 2, 2)
    dispatchPointer(canvas, 'pointermove', 3, 2)
    dispatchPointer(canvas, 'pointermove', 4, 2)
    dispatchPointer(canvas, 'pointerup', 4, 2)

    expect(recordSpy).toHaveBeenCalledTimes(1)
    expect(pixelAt(store.atlas!.pixels, 8, 2, 2)).toEqual([255, 0, 0, 255])
    expect(pixelAt(store.atlas!.pixels, 8, 4, 2)).toEqual([255, 0, 0, 255])
    // Ningún píxel fuera del trazo se tocó -- sin sangrado/antialiasing.
    expect(pixelAt(store.atlas!.pixels, 8, 2, 5)).toEqual([0, 0, 0, 0])
  })

  it('AC HU-27: el Borrador reutiliza el mismo mecanismo de trazo con alfa 0 (transparente)', async () => {
    const w = mountCanvas(modelWith({ width: 8, height: 8 }))
    mockCanvasRect(8, 8)
    const store = useTextureEditorStore()
    // Pre-pinta un píxel rojo para tener algo que borrar.
    const before = store.readRegion({ x: 2, y: 2, width: 1, height: 1 })!
    store.recordPatch({ x: 2, y: 2, width: 1, height: 1 }, before, new Uint8ClampedArray([255, 0, 0, 255]))

    await w.get('button[aria-label="Borrador"]').trigger('click')
    await w.get('input[aria-label="Tamaño de pincel en píxeles del atlas"]').setValue(1)
    const canvas = w.get('canvas').element as HTMLCanvasElement
    dispatchPointer(canvas, 'pointerdown', 2, 2)
    dispatchPointer(canvas, 'pointerup', 2, 2)

    expect(pixelAt(store.atlas!.pixels, 8, 2, 2)).toEqual([0, 0, 0, 0])
  })

  it('AC HU-27: el tamaño de pincel se mide en píxeles del ATLAS, no de pantalla -- verificado con el canvas mostrado 10x más grande (CSS)', async () => {
    const w = mountCanvas(modelWith({ width: 8, height: 8 }))
    mockCanvasRect(80, 80) // el canvas se MUESTRA a 80x80 pero su resolución real es 8x8
    const store = useTextureEditorStore()

    await w.get('input[aria-label="Color activo"]').setValue('#00ff00')
    await w.get('input[aria-label="Tamaño de pincel en píxeles del atlas"]').setValue(3)
    const canvas = w.get('canvas').element as HTMLCanvasElement

    // Clic en (40,40) de pantalla -> con el factor de escala 10x, corresponde al píxel de atlas (4,4).
    dispatchPointer(canvas, 'pointerdown', 40, 40)
    dispatchPointer(canvas, 'pointerup', 40, 40)

    // sizePx=3 centrado en (4,4) del ATLAS -> cuadrado [3,5]x[3,5], nunca [3,5]x[3,5] en píxeles de pantalla (habría sido gigante).
    for (let y = 3; y <= 5; y += 1) {
      for (let x = 3; x <= 5; x += 1) {
        expect(pixelAt(store.atlas!.pixels, 8, x, y)[3]).toBe(255)
      }
    }
    expect(pixelAt(store.atlas!.pixels, 8, 0, 0)[3]).toBe(0)
    expect(pixelAt(store.atlas!.pixels, 8, 7, 7)[3]).toBe(0)
  })

  it('AC HU-27: la Cubeta hace flood-fill real dentro de sus límites de color, con UNA sola llamada a recordPatch', async () => {
    const w = mountCanvas(modelWith({ width: 6, height: 6 }))
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
    await w.get('input[aria-label="Color activo"]').setValue('#ff0000')
    const canvas = w.get('canvas').element as HTMLCanvasElement

    dispatchPointer(canvas, 'pointerdown', 1, 1)

    expect(recordSpy).toHaveBeenCalledTimes(1)
    expect(pixelAt(store.atlas!.pixels, 6, 1, 1).slice(0, 3)).toEqual([255, 0, 0])
    expect(pixelAt(store.atlas!.pixels, 6, 2, 2).slice(0, 3)).toEqual([255, 0, 0])
    // Fuera de la región azul original, el fill nunca se filtró.
    expect(pixelAt(store.atlas!.pixels, 6, 0, 0)[3]).toBe(0)
  })

  it('AC HU-27: el Eyedropper toma el color exacto del píxel clickeado, sin registrar ningún Command', async () => {
    const w = mountCanvas(modelWith({ width: 4, height: 4 }))
    mockCanvasRect(4, 4)
    const store = useTextureEditorStore()
    const before = store.readRegion({ x: 2, y: 2, width: 1, height: 1 })!
    store.recordPatch({ x: 2, y: 2, width: 1, height: 1 }, before, new Uint8ClampedArray([10, 20, 30, 255]))
    const recordSpy = vi.spyOn(store, 'recordPatch')

    await w.get('button[aria-label="Selector de color (eyedropper)"]').trigger('click')
    const canvas = w.get('canvas').element as HTMLCanvasElement
    dispatchPointer(canvas, 'pointerdown', 2, 2)
    await nextTick() // el <input type="color"> se actualiza vía v-model -- el DOM patch de Vue es asíncrono.

    expect((w.get('input[aria-label="Color activo"]').element as HTMLInputElement).value).toBe('#0a141e')
    expect(recordSpy).not.toHaveBeenCalled()
  })

  it('AC HU-27: el toggle de cuadrícula agrega/quita líneas SOLO en el overlay -- nunca cambia un byte del atlas', async () => {
    // Atlas de 16x16 (> GRID_STEP_PX=8) -- con 8x8 exacto no habría ninguna línea intermedia que dibujar.
    const w = mountCanvas(modelWith({ width: 16, height: 16 }))
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

  it('HU-26: tras un trazo, la textura 3D vinculada al mismo ThreeViewportService singleton refleja el cambio (mismo buffer, versión incrementada) -- sin instanciar un viewport nuevo', () => {
    const model = modelWith({ width: 8, height: 8, cuboids: [cuboid('c1', 'Cabeza')] })
    mountCanvas(model)
    mockCanvasRect(8, 8)
    const store = useTextureEditorStore()
    const canvas = wrapper!.get('canvas').element as HTMLCanvasElement

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
    const w = mountCanvas(modelWith({ width: 8, height: 8 }))
    mockCanvasRect(8, 8)
    const store = useTextureEditorStore()
    await w.get('input[aria-label="Color activo"]').setValue('#ff0000')
    const canvas = w.get('canvas').element as HTMLCanvasElement

    dispatchPointer(canvas, 'pointerdown', 2, 2)
    dispatchPointer(canvas, 'pointerup', 2, 2)
    expect(store.canUndo).toBe(true)

    store.undo()
    expect(pixelAt(store.atlas!.pixels, 8, 2, 2)).toEqual([0, 0, 0, 0])
  })

  it('ticket 049 (HU-25): clic en el preview 3D resuelve la cara vía pickCuboidFaceAt y resalta la región UV correspondiente en el editor 2D', async () => {
    const region: UvRegion = { cuboidId: 'c1', face: 'north', rect: [0, 0, 4, 4], status: 'unpainted' }
    const model = modelWith({ cuboids: [cuboid('c1', 'Cabeza')], regions: [region] })
    const w = mountCanvas(model)
    vi.spyOn(threeViewportService, 'pickCuboidFaceAt').mockReturnValue({ cuboidId: 'c1', face: 'north' })

    const preview = w.get('.texture-canvas__preview').element
    dispatchPointer(preview, 'pointerdown', 5, 5)
    preview.dispatchEvent(new MouseEvent('click', { clientX: 5, clientY: 5, bubbles: true }))
    await nextTick()

    expect((w.get('select[aria-label="Región UV a enfocar"]').element as HTMLSelectElement).value).toBe('c1:north')
    expect(w.get('rect.texture-canvas__region').classes()).toContain('texture-canvas__region--selected')
  })

  it('ticket 049 (HU-25): un clic que no resuelve ninguna cara (pickCuboidFaceAt -> null) deselecciona la región enfocada', async () => {
    const region: UvRegion = { cuboidId: 'c1', face: 'north', rect: [0, 0, 4, 4], status: 'unpainted' }
    const model = modelWith({ cuboids: [cuboid('c1', 'Cabeza')], regions: [region] })
    const w = mountCanvas(model)
    await w.get('select[aria-label="Región UV a enfocar"]').setValue('c1:north')
    vi.spyOn(threeViewportService, 'pickCuboidFaceAt').mockReturnValue(null)

    const preview = w.get('.texture-canvas__preview').element
    dispatchPointer(preview, 'pointerdown', 5, 5)
    preview.dispatchEvent(new MouseEvent('click', { clientX: 5, clientY: 5, bubbles: true }))
    await nextTick()

    expect((w.get('select[aria-label="Región UV a enfocar"]').element as HTMLSelectElement).value).toBe(ALL_REGIONS_VALUE)
  })

  it('ticket 049: un arrastre de órbita (drag > umbral) en el preview 3D nunca dispara la selección de cara -- mismo criterio click-vs-drag que ThreeViewport.vue', () => {
    const model = modelWith({ cuboids: [cuboid('c1', 'Cabeza')] })
    const w = mountCanvas(model)
    const pickSpy = vi.spyOn(threeViewportService, 'pickCuboidFaceAt')

    const preview = w.get('.texture-canvas__preview').element
    dispatchPointer(preview, 'pointerdown', 5, 5)
    preview.dispatchEvent(new MouseEvent('click', { clientX: 50, clientY: 50, bubbles: true }))

    expect(pickSpy).not.toHaveBeenCalled()
  })

  it('ticket 049 (HU-25/HU-26): elegir una región UV en el dropdown del editor 2D resalta la cara correspondiente en el preview 3D (FACE_HIGHLIGHT_NAME)', async () => {
    const region: UvRegion = { cuboidId: 'c1', face: 'north', rect: [0, 0, 4, 4], status: 'unpainted' }
    const model = modelWith({ cuboids: [cuboid('c1', 'Cabeza')], regions: [region] })
    const w = mountCanvas(model)

    await w.get('select[aria-label="Región UV a enfocar"]').setValue('c1:north')

    const mobGroup = threeViewportService.scene.children.find((c) => c.name === model.name)!
    const mesh = mobGroup.children.find((c) => c.userData.cuboidId === 'c1')!
    expect(mesh.children.some((c) => c.name === FACE_HIGHLIGHT_NAME)).toBe(true)
  })

  it('ticket 049: volver a "Todas las caras" quita el highlight de la cara en el preview 3D', async () => {
    const region: UvRegion = { cuboidId: 'c1', face: 'north', rect: [0, 0, 4, 4], status: 'unpainted' }
    const model = modelWith({ cuboids: [cuboid('c1', 'Cabeza')], regions: [region] })
    const w = mountCanvas(model)
    await w.get('select[aria-label="Región UV a enfocar"]').setValue('c1:north')

    await w.get('select[aria-label="Región UV a enfocar"]').setValue(ALL_REGIONS_VALUE)

    const mobGroup = threeViewportService.scene.children.find((c) => c.name === model.name)!
    const mesh = mobGroup.children.find((c) => c.userData.cuboidId === 'c1')!
    expect(mesh.children.some((c) => c.name === FACE_HIGHLIGHT_NAME)).toBe(false)
  })

  it('ticket 049: textureSelectionStore.ts es un store separado -- NO expone selectedCuboidId/select() del selectionStore.ts (017/031/036)', () => {
    mountCanvas(modelWith())
    const store = useTextureSelectionStore()

    expect(store.selectedFace).toBeNull()
    expect('selectedCuboidId' in store).toBe(false)
    expect('select' in store).toBe(false)
  })

  it('a11y: todos los controles de herramientas tienen aria-label, y el canvas del atlas tiene un nombre accesible', () => {
    const w = mountCanvas(modelWith({ width: 8, height: 8 }))

    for (const label of ['Pincel', 'Borrador', 'Cubeta', 'Selector de color (eyedropper)', 'Cuadrícula']) {
      expect(w.find(`button[aria-label="${label}"]`).exists()).toBe(true)
    }
    expect(w.get('canvas').attributes('aria-label')).toBeTruthy()
    // .get() ya lanza si no existe -- llegar hasta acá sin excepción es la aserción real.
    w.get('input[aria-label="Color activo"]')
    w.get('input[aria-label="Tamaño de pincel en píxeles del atlas"]')
    w.get('select[aria-label="Región UV a enfocar"]')
  })

  describe('ticket 048 -- import de PNG (región seleccionada y atlas completo)', () => {
    beforeEach(() => mockDecode.mockReset())

    it('AC B (atlas completo): con "Todas las caras" seleccionado (default), el destino del import es el atlas completo -- un solo TexturePatchCommand con rect = atlas completo', async () => {
      const w = mountCanvas(modelWith({ width: 4, height: 4 }))
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
      const w = mountCanvas(model)
      await w.get('select[aria-label="Región UV a enfocar"]').setValue('c1:north')
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
      const w = mountCanvas(model) // selector queda en "Todas las caras" -> destino = atlas completo
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
      const w = mountCanvas(modelWith({ width: 4, height: 4 }))
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

    it('a11y: el botón de importar tiene texto visible y el input de archivo tiene aria-label', () => {
      const w = mountCanvas(modelWith({ width: 4, height: 4 }))
      expect(w.find('input[aria-label="Archivo PNG a importar"]').exists()).toBe(true)
      expect(w.findAll('button').some((b) => b.text().includes('Importar PNG'))).toBe(true)
    })
  })
})
