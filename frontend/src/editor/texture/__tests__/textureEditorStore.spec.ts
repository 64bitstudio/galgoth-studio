import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it } from 'vitest'
import { useTextureEditorStore } from '../textureEditorStore'
import type { TextureRect } from '../TexturePatchCommand'

/** Buffer RGBA de `width*height` píxeles, todos con el mismo color -- útil para armar before/after deterministas. */
function solidPixels(width: number, height: number, [r, g, b, a]: [number, number, number, number]): Uint8ClampedArray {
  const pixels = new Uint8ClampedArray(width * height * 4)
  for (let i = 0; i < width * height; i += 1) {
    pixels.set([r, g, b, a], i * 4)
  }
  return pixels
}

const SMALL_RECT: TextureRect = { x: 2, y: 3, width: 4, height: 4 }

describe('useTextureEditorStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('loadAtlas() inicializa un atlas en blanco y limpia ambas pilas', () => {
    const store = useTextureEditorStore()

    store.loadAtlas(16, 16)

    expect(store.atlas?.width).toBe(16)
    expect(store.atlas?.height).toBe(16)
    expect(store.atlas?.pixels).toHaveLength(16 * 16 * 4)
    expect(store.canUndo).toBe(false)
    expect(store.canRedo).toBe(false)
  })

  it('un trazo completo (una sola llamada a recordPatch) es EXACTAMENTE una unidad de Undo', () => {
    const store = useTextureEditorStore()
    store.loadAtlas(16, 16)
    const before = store.readRegion(SMALL_RECT)!
    const after = solidPixels(SMALL_RECT.width, SMALL_RECT.height, [255, 0, 0, 255])

    store.recordPatch(SMALL_RECT, before, after)

    expect(store.canUndo).toBe(true)
    expect(store.canRedo).toBe(false)

    store.undo()

    // Un solo undo() ya revierte el trazo completo -- no queda ningún
    // paso intermedio pendiente, como pasaría si el trazo hubiera
    // generado un Command por cada pointermove.
    expect(store.canUndo).toBe(false)
    expect(store.canRedo).toBe(true)
  })

  it('el tamaño de beforePixels/afterPixels es proporcional al área del rect, nunca al área total del atlas', () => {
    const store = useTextureEditorStore()
    const ATLAS_SIZE = 1024 // atlas grande a propósito
    store.loadAtlas(ATLAS_SIZE, ATLAS_SIZE)
    const rect: TextureRect = { x: 500, y: 500, width: 4, height: 4 }
    const before = store.readRegion(rect)!
    const after = solidPixels(rect.width, rect.height, [10, 20, 30, 255])

    store.recordPatch(rect, before, after)

    const expectedBytes = rect.width * rect.height * 4 // 64 bytes
    const fullAtlasBytes = ATLAS_SIZE * ATLAS_SIZE * 4 // ~4MB

    expect(before).toHaveLength(expectedBytes)
    expect(after).toHaveLength(expectedBytes)
    expect(expectedBytes).toBeLessThan(fullAtlasBytes)
  })

  it('undo aplica beforePixels sobre rect; redo aplica afterPixels', () => {
    const store = useTextureEditorStore()
    store.loadAtlas(16, 16)
    const before = solidPixels(SMALL_RECT.width, SMALL_RECT.height, [0, 0, 0, 0])
    const after = solidPixels(SMALL_RECT.width, SMALL_RECT.height, [200, 100, 50, 255])

    store.recordPatch(SMALL_RECT, before, after)
    expect(store.readRegion(SMALL_RECT)).toEqual(after)

    store.undo()
    expect(store.readRegion(SMALL_RECT)).toEqual(before)

    store.redo()
    expect(store.readRegion(SMALL_RECT)).toEqual(after)
  })

  it('recordPatch después de un undo descarta la rama de redo pendiente (mismo comportamiento estándar que cualquier Command stack)', () => {
    const store = useTextureEditorStore()
    store.loadAtlas(16, 16)
    const before = store.readRegion(SMALL_RECT)!
    const after = solidPixels(SMALL_RECT.width, SMALL_RECT.height, [1, 2, 3, 255])
    store.recordPatch(SMALL_RECT, before, after)
    store.undo() // hay una rama de redo pendiente ahora

    const otherRect: TextureRect = { x: 8, y: 8, width: 2, height: 2 }
    const otherAfter = solidPixels(otherRect.width, otherRect.height, [9, 9, 9, 255])
    store.recordPatch(otherRect, store.readRegion(otherRect)!, otherAfter)

    expect(store.canRedo).toBe(false)
  })

  it('undo()/redo()/recordPatch() no hacen nada si no hay atlas cargado (sin lanzar)', () => {
    const store = useTextureEditorStore()

    expect(() => store.undo()).not.toThrow()
    expect(() => store.redo()).not.toThrow()
    expect(() => store.recordPatch(SMALL_RECT, new Uint8ClampedArray(0), new Uint8ClampedArray(0))).not.toThrow()
    expect(store.readRegion(SMALL_RECT)).toBeNull()
  })

  it('undo()/redo() no hacen nada si la pila correspondiente está vacía (sin lanzar)', () => {
    const store = useTextureEditorStore()
    store.loadAtlas(16, 16)

    expect(() => store.undo()).not.toThrow()
    expect(() => store.redo()).not.toThrow()
    expect(store.canUndo).toBe(false)
    expect(store.canRedo).toBe(false)
  })
})
