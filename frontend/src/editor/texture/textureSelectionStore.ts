/**
 * Ticket 049 (HU-25/HU-26, Diseño técnico §14) -- selección de CARA de un
 * cuboid, compartida entre el viewport 3D del tab Textura
 * (`TextureCanvas.vue`, `pickCuboidFaceAt`) y el editor 2D de UV
 * (overlay de región, dropdown "Región UV a enfocar") para la selección
 * cruzada bidireccional: clic en una cara del viewport 3D resalta la
 * región UV correspondiente en el editor 2D, y elegir una región UV en el
 * editor 2D resalta la cara correspondiente en el preview 3D.
 *
 * Store nuevo y SEPARADO de `selectionStore.ts` (selección de CUBOID
 * completo, tickets 017/031/036, tab Modelo) -- deliberado: ese store y
 * sus consumidores (jerarquía + `ThreeViewport.vue`) no cambian en
 * absoluto con este ticket. Este store vive exclusivamente en el tab
 * Textura (`TextureCanvas.vue`).
 */
import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { CuboidFaceRef } from '../../viewport/textureUvMapping'

export const useTextureSelectionStore = defineStore('textureSelection', () => {
  const selectedFace = ref<CuboidFaceRef | null>(null)

  function selectFace(selection: CuboidFaceRef | null): void {
    selectedFace.value = selection
  }

  return { selectedFace, selectFace }
})
