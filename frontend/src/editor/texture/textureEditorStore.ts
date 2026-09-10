/**
 * Ticket 046 -- pila de Undo/Redo del editor de textura, basada en
 * `TexturePatchCommand` (Diseño técnico §9 de
 * `docs/definiciones/galgoth-studio-fase3-textura.md`).
 *
 * 100% independiente de `draftModelStore.ts` (pila de Undo/Redo de
 * GEOMETRÍA, tickets 018/019): un Ctrl+Z en el tab Textura nunca debe
 * tocar el draft de Modelo y viceversa -- decisión de "pila independiente
 * por tab" ya cerrada, este ticket solo define el mecanismo interno de
 * cada Command. Este store NO importa nada de `draftModelStore.ts`.
 *
 * Decisión de diseño -- de dónde sale el bitmap sobre el que se aplican
 * los patches: el editor de textura real (canvas, herramientas de
 * pintado) todavía NO existe -- es el ticket 047. Pero aplicar/deshacer
 * un patch requiere algo concreto sobre lo que escribir los píxeles, así
 * que este store mantiene el ATLAS COMPLETO en memoria como estado
 * (`atlas`: ancho, alto y un único buffer RGBA `Uint8ClampedArray`,
 * mismo layout que `ImageData.data`) -- el ticket 047 le pasará el atlas
 * real vía `loadAtlas()` al montar el canvas, y llamará a `recordPatch()`
 * después de cada trazo/fill/paste ya aplicado a su propia copia visible
 * (o leerá `readRegion()` antes de pintar para capturar `beforePixels`).
 * Lo que el AC de este ticket blinda es que el TAMAÑO de cada Command
 * (`beforePixels`/`afterPixels`) sea proporcional al `rect`, NUNCA al
 * atlas completo -- mantener el atlas completo como estado actual (una
 * sola copia, no una por Command) es necesario y no viola ese AC.
 *
 * `atlas` y las pilas usan `shallowRef`, no `ref`: son buffers binarios
 * grandes, y la reactividad profunda de Vue (Proxy por índice) sobre un
 * `Uint8ClampedArray` de miles de posiciones no aporta nada aquí -- nadie
 * necesita reaccionar a la escritura de UN píxel individual, solo saber
 * que "el atlas cambió" (recarga/undo/redo), por eso se llama
 * `triggerRef` explícitamente después de mutar el buffer en el lugar.
 */
import { defineStore } from 'pinia'
import { computed, shallowRef, triggerRef } from 'vue'
import type { TexturePatchCommand, TextureRect } from './TexturePatchCommand'

export interface TextureAtlas {
  width: number
  height: number
  /** Buffer RGBA (4 bytes/píxel), longitud `width * height * 4`, mismo layout que `ImageData.data`. */
  pixels: Uint8ClampedArray
}

function createBlankAtlas(width: number, height: number): TextureAtlas {
  return { width, height, pixels: new Uint8ClampedArray(width * height * 4) }
}

/** Escribe `patch` (buffer RGBA de `rect.width * rect.height * 4`) dentro de `atlas.pixels`, fila por fila -- O(área de `rect`), nunca O(atlas completo). */
function writeRect(atlas: TextureAtlas, rect: TextureRect, patch: Uint8ClampedArray): void {
  const rowBytes = rect.width * 4
  for (let row = 0; row < rect.height; row += 1) {
    const srcStart = row * rowBytes
    const destStart = ((rect.y + row) * atlas.width + rect.x) * 4
    atlas.pixels.set(patch.subarray(srcStart, srcStart + rowBytes), destStart)
  }
}

/** Lee `rect` desde `atlas.pixels` a un buffer RGBA nuevo de `rect.width * rect.height * 4` -- O(área de `rect`). */
function readRect(atlas: TextureAtlas, rect: TextureRect): Uint8ClampedArray {
  const rowBytes = rect.width * 4
  const out = new Uint8ClampedArray(rect.width * rect.height * 4)
  for (let row = 0; row < rect.height; row += 1) {
    const srcStart = ((rect.y + row) * atlas.width + rect.x) * 4
    out.set(atlas.pixels.subarray(srcStart, srcStart + rowBytes), row * rowBytes)
  }
  return out
}

export const useTextureEditorStore = defineStore('textureEditor', () => {
  const atlas = shallowRef<TextureAtlas | null>(null)
  const undoStack = shallowRef<TexturePatchCommand[]>([])
  const redoStack = shallowRef<TexturePatchCommand[]>([])

  const canUndo = computed(() => undoStack.value.length > 0)
  const canRedo = computed(() => redoStack.value.length > 0)

  /**
   * Carga (o reemplaza) el atlas vigente. Cargar un atlas es el INICIO
   * de una historia de edición, no un paso dentro de una ya existente --
   * ninguna pila sobrevive a un `loadAtlas()` (mismo criterio que
   * `draftModelStore.load()`).
   */
  function loadAtlas(width: number, height: number, pixels?: Uint8ClampedArray): void {
    atlas.value = pixels ? { width, height, pixels } : createBlankAtlas(width, height)
    undoStack.value = []
    redoStack.value = []
  }

  /** Copia de los píxeles vigentes de `rect` -- para que el caller capture `beforePixels` antes de pintar (el canvas real llega en el ticket 047). `null` si no hay atlas cargado. */
  function readRegion(rect: TextureRect): Uint8ClampedArray | null {
    return atlas.value ? readRect(atlas.value, rect) : null
  }

  /**
   * Registra un trazo/fill/paste ya resuelto: aplica `afterPixels` sobre
   * el atlas vigente (para que quede consistente con lo que el caller ya
   * pintó) y empuja el Command a la pila de Undo. Limpia la pila de Redo
   * (mismo comportamiento estándar que cualquier Command stack, incluido
   * `draftModelStore`) -- una acción nueva descarta la rama de redo
   * pendiente.
   */
  function recordPatch(rect: TextureRect, beforePixels: Uint8ClampedArray, afterPixels: Uint8ClampedArray): void {
    if (!atlas.value) {
      return
    }
    writeRect(atlas.value, rect, afterPixels)
    triggerRef(atlas)
    undoStack.value = [...undoStack.value, { rect, beforePixels, afterPixels }]
    redoStack.value = []
  }

  /** Deshace el último `TexturePatchCommand`: aplica `beforePixels` sobre `rect` -- O(área de `rect`). */
  function undo(): void {
    if (!atlas.value || undoStack.value.length === 0) {
      return
    }
    const command = undoStack.value.at(-1)!
    undoStack.value = undoStack.value.slice(0, -1)
    writeRect(atlas.value, command.rect, command.beforePixels)
    triggerRef(atlas)
    redoStack.value = [...redoStack.value, command]
  }

  /** Rehace el último `TexturePatchCommand` deshecho: aplica `afterPixels` sobre `rect` -- O(área de `rect`). */
  function redo(): void {
    if (!atlas.value || redoStack.value.length === 0) {
      return
    }
    const command = redoStack.value.at(-1)!
    redoStack.value = redoStack.value.slice(0, -1)
    writeRect(atlas.value, command.rect, command.afterPixels)
    triggerRef(atlas)
    undoStack.value = [...undoStack.value, command]
  }

  return {
    atlas,
    canUndo,
    canRedo,
    loadAtlas,
    readRegion,
    recordPatch,
    undo,
    redo,
  }
})
