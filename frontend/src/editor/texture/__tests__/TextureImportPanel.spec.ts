import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import TextureImportPanel from '../TextureImportPanel.vue'
import type { TextureRect } from '../TexturePatchCommand'
import { useTextureEditorStore } from '../textureEditorStore'

vi.mock('../pngImportDecode', () => ({
  decodePngBytesToAtlasBuffer: vi.fn(),
  PngDecodeError: class PngDecodeError extends Error {},
}))
const { decodePngBytesToAtlasBuffer } = await import('../pngImportDecode')
const mockDecode = vi.mocked(decodePngBytesToAtlasBuffer)

function solidPixels(width: number, height: number, color: [number, number, number, number]): Uint8ClampedArray {
  const pixels = new Uint8ClampedArray(width * height * 4)
  for (let i = 0; i < pixels.length; i += 4) {
    pixels.set(color, i)
  }
  return pixels
}

async function selectFile(wrapper: ReturnType<typeof mount>, file: File): Promise<void> {
  const input = wrapper.get('input[aria-label="Archivo PNG a importar"]').element as HTMLInputElement
  Object.defineProperty(input, 'files', { value: [file], configurable: true })
  input.dispatchEvent(new Event('change'))
  await new Promise((resolve) => setTimeout(resolve, 0)) // deja resolver la promesa async del handler
  await wrapper.vm.$nextTick()
}

const FAKE_FILE = new File([new Uint8Array([1])], 'x.png', { type: 'image/png' })

describe('TextureImportPanel.vue', () => {
  let wrapper: ReturnType<typeof mount> | null = null

  beforeEach(() => {
    setActivePinia(createPinia())
    mockDecode.mockReset()
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = null
  })

  function mountPanel(targetRect: TextureRect = { x: 0, y: 0, width: 4, height: 4 }, targetLabel = 'el atlas completo'): ReturnType<typeof mount> {
    const store = useTextureEditorStore()
    store.loadAtlas(8, 8)
    wrapper = mount(TextureImportPanel, { props: { targetLabel, resolveTarget: () => ({ rect: targetRect, label: targetLabel }) } })
    return wrapper
  }

  it('AC B (dimensiones exactas): al importar un PNG del mismo tamaño que el destino, se muestra confirmación/diff ANTES de reemplazar -- nada se aplica todavía', async () => {
    const w = mountPanel()
    mockDecode.mockResolvedValue({ pixels: solidPixels(4, 4, [255, 0, 0, 255]), width: 4, height: 4 })
    const store = useTextureEditorStore()
    const recordSpy = vi.spyOn(store, 'recordPatch')

    await selectFile(w, FAKE_FILE)

    expect(w.text()).toContain('coincide exactamente')
    expect(recordSpy).not.toHaveBeenCalled()
  })

  it('confirmar aplica EXACTAMENTE un TexturePatchCommand con rect = el resuelto al momento del import, y emite "imported"', async () => {
    const w = mountPanel({ x: 1, y: 1, width: 2, height: 2 }, 'la región "Cabeza (north)"')
    mockDecode.mockResolvedValue({ pixels: solidPixels(2, 2, [10, 20, 30, 255]), width: 2, height: 2 })
    const store = useTextureEditorStore()
    const recordSpy = vi.spyOn(store, 'recordPatch')

    await selectFile(w, FAKE_FILE)
    const confirmButton = w.findAll('button').find((b) => b.text() === 'Confirmar import')!
    await confirmButton.trigger('click')

    expect(recordSpy).toHaveBeenCalledTimes(1)
    expect(recordSpy).toHaveBeenCalledWith({ x: 1, y: 1, width: 2, height: 2 }, expect.any(Uint8ClampedArray), expect.any(Uint8ClampedArray))
    expect(w.emitted('imported')).toHaveLength(1)
    // El panel de confirmación se cierra tras aplicar.
    expect(w.text()).not.toContain('Confirmar import')
  })

  it('cancelar descarta el import pendiente sin llamar a recordPatch ni emitir "imported"', async () => {
    const w = mountPanel()
    mockDecode.mockResolvedValue({ pixels: solidPixels(4, 4, [1, 2, 3, 4]), width: 4, height: 4 })
    const store = useTextureEditorStore()
    const recordSpy = vi.spyOn(store, 'recordPatch')

    await selectFile(w, FAKE_FILE)
    const cancelButton = w.findAll('button').find((b) => b.text() === 'Cancelar')!
    await cancelButton.trigger('click')

    expect(recordSpy).not.toHaveBeenCalled()
    expect(w.emitted('imported')).toBeUndefined()
    expect(w.text()).not.toContain('Confirmar import')
  })

  it('AC B (dimensiones distintas, más grande): ofrece describir un CROP', async () => {
    const w = mountPanel({ x: 0, y: 0, width: 4, height: 4 }, 'el atlas completo')
    mockDecode.mockResolvedValue({ pixels: solidPixels(8, 8, [0, 0, 0, 255]), width: 8, height: 8 })

    await selectFile(w, FAKE_FILE)

    expect(w.text()).toContain('recortará')
  })

  it('AC B (dimensiones distintas, más chica): ofrece describir un PAD (margen transparente)', async () => {
    const w = mountPanel({ x: 0, y: 0, width: 8, height: 8 }, 'el atlas completo')
    mockDecode.mockResolvedValue({ pixels: solidPixels(2, 2, [0, 0, 0, 255]), width: 2, height: 2 })

    await selectFile(w, FAKE_FILE)

    expect(w.text()).toContain('margen transparente')
  })

  it('AC: nunca se ofrece ni ejecuta un camino de ESCALADO -- ningún BOTÓN/CONTROL interactivo de este panel propone escalar/resize, en ningún caso (crop, pad, o exacto)', async () => {
    for (const decoded of [
      { pixels: solidPixels(8, 8, [0, 0, 0, 255]), width: 8, height: 8 }, // crop
      { pixels: solidPixels(2, 2, [0, 0, 0, 255]), width: 2, height: 2 }, // pad
      { pixels: solidPixels(4, 4, [0, 0, 0, 255]), width: 4, height: 4 }, // exacto
    ]) {
      const w = mountPanel({ x: 0, y: 0, width: 4, height: 4 })
      mockDecode.mockResolvedValue(decoded)
      await selectFile(w, FAKE_FILE)

      const buttons = w.findAll('button').map((b) => b.text())
      for (const label of buttons) {
        expect(label).not.toMatch(/escalar|escalado|resize|scale/i)
      }
      // Ningún <select>/<input type=range> u otro control de "factor de escala".
      expect(w.findAll('select')).toHaveLength(0)
      expect(w.find('input[type="range"]').exists()).toBe(false)
      w.unmount()
    }
  })

  it('toggle Antes/Después alterna aria-selected entre las dos pestañas', async () => {
    const w = mountPanel()
    mockDecode.mockResolvedValue({ pixels: solidPixels(4, 4, [1, 2, 3, 4]), width: 4, height: 4 })
    await selectFile(w, FAKE_FILE)

    const tabs = w.findAll('button[role="tab"]')
    expect(tabs.map((t) => t.attributes('aria-selected'))).toEqual(['false', 'true']) // Después activo por defecto

    await tabs[0]!.trigger('click')
    expect(tabs.map((t) => t.attributes('aria-selected'))).toEqual(['true', 'false'])
  })

  it('un error de decodificación se muestra como texto, y nunca deja un pending a medio confirmar', async () => {
    const { PngDecodeError } = await import('../pngImportDecode')
    mockDecode.mockRejectedValue(new PngDecodeError('El archivo no se pudo decodificar como una imagen válida.'))
    const w = mountPanel()

    await selectFile(w, FAKE_FILE)

    expect(w.text()).toContain('no se pudo decodificar')
    expect(w.text()).not.toContain('Confirmar import')
  })

  it('a11y: el botón que dispara el selector de archivo y el input tienen nombre accesible', () => {
    const w = mountPanel()
    expect(w.find('input[aria-label="Archivo PNG a importar"]').exists()).toBe(true)
    expect(w.findAll('button').some((b) => b.text().includes('Importar PNG'))).toBe(true)
  })
})
