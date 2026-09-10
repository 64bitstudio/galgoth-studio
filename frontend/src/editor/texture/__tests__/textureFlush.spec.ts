import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { MobProjectModel } from '../../../domain/MobProjectModel'
import { uploadTexture } from '../../../api/textureUploadApi'
import { encodeAtlasToPngBlob } from '../textureAtlasEncode'
import { flushPaintedTexture } from '../textureFlush'
import { useTextureEditorStore } from '../textureEditorStore'

vi.mock('../../../api/textureUploadApi', () => ({ uploadTexture: vi.fn() }))
vi.mock('../textureAtlasEncode', () => ({ encodeAtlasToPngBlob: vi.fn() }))

function baseModel(): MobProjectModel {
  return {
    mobId: 'mob-1',
    projectId: 'project-1',
    name: 'Test',
    baseType: 'humanoid',
    units: 'minecraft_pixels',
    bones: [],
    cuboids: [],
    texture: { width: 16, height: 16, storageKey: null },
    uv: { textureWidth: 16, textureHeight: 16, regions: [], reservations: [] },
    animations: [],
    exportSettings: { preferredFormatVersion: 'v5' },
    referenceImages: [],
  }
}

describe('flushPaintedTexture', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('sin ningún atlas cargado (tab Textura nunca abierto) es un no-op: devuelve el mismo model, nunca llama al backend', async () => {
    const model = baseModel()

    const result = await flushPaintedTexture(model)

    expect(result).toBe(model)
    expect(encodeAtlasToPngBlob).not.toHaveBeenCalled()
    expect(uploadTexture).not.toHaveBeenCalled()
  })

  it('con un atlas cargado, codifica a PNG, sube vía PUT /texture, y devuelve el model con el storageKey OFICIAL del backend', async () => {
    const model = baseModel()
    const textureEditorStore = useTextureEditorStore()
    textureEditorStore.loadAtlas(16, 16)
    const png = new Blob(['fake-png'], { type: 'image/png' })
    vi.mocked(encodeAtlasToPngBlob).mockResolvedValue(png)
    vi.mocked(uploadTexture).mockResolvedValue({ storageKey: 'textures/oficial.png' })

    const result = await flushPaintedTexture(model)

    expect(encodeAtlasToPngBlob).toHaveBeenCalledWith(textureEditorStore.atlas)
    expect(uploadTexture).toHaveBeenCalledWith('mob-1', png)
    expect(result.texture.storageKey).toBe('textures/oficial.png')
    // El resto del model (bones/cuboids/uv/etc.) se preserva intacto -- solo cambia texture.storageKey.
    expect(result).toEqual({ ...model, texture: { ...model.texture, storageKey: 'textures/oficial.png' } })
  })

  it('si el PUT /texture falla, propaga el error y nunca devuelve un model con storageKey inventado', async () => {
    const model = baseModel()
    useTextureEditorStore().loadAtlas(16, 16)
    vi.mocked(encodeAtlasToPngBlob).mockResolvedValue(new Blob(['png'], { type: 'image/png' }))
    vi.mocked(uploadTexture).mockRejectedValue(new Error('El archivo no se pudo decodificar como un PNG válido.'))

    await expect(flushPaintedTexture(model)).rejects.toThrow('El archivo no se pudo decodificar como un PNG válido.')
  })
})
