/**
 * Selección compartida del editor de modelo manual (ticket 017) --
 * fuente única de verdad para sincronizar el panel de jerarquía y el
 * viewport 3D en ambas direcciones (clic en el árbol resalta en el
 * viewport, clic en el viewport resalta en el árbol). Primer store
 * Pinia real del proyecto (Pinia ya estaba registrado desde el
 * bootstrap, sin uso hasta este ticket).
 *
 * Alcance de este ciclo: solo cuboids son seleccionables (coincide con
 * la capacidad de outline del viewport, ticket 016) -- los bones son
 * nodos organizativos del árbol, no un objetivo de selección todavía.
 */
import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useSelectionStore = defineStore('selection', () => {
  const selectedCuboidId = ref<string | null>(null)

  function select(cuboidId: string | null): void {
    selectedCuboidId.value = cuboidId
  }

  return { selectedCuboidId, select }
})
