import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it } from 'vitest'
import { useSelectionStore } from '../../selectionStore'
import { useTextureSelectionStore } from '../textureSelectionStore'

/**
 * Test cruzado explícito, AC del ticket 049 (HU-25, Diseño técnico §14):
 * `textureSelectionStore.ts` (selección de CARA, tab Textura) es un store
 * nuevo y SEPARADO de `selectionStore.ts` (selección de CUBOID completo,
 * tab Modelo, tickets 017/031/036) -- ninguno de los dos modifica el
 * contrato del otro, y mutar uno nunca afecta al otro.
 */
describe('independencia entre textureSelectionStore y selectionStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('seleccionar una cara en textureSelectionStore no afecta selectionStore.selectedCuboidId', () => {
    const cuboidSelection = useSelectionStore()
    const faceSelection = useTextureSelectionStore()

    cuboidSelection.select('cube-1')
    faceSelection.selectFace({ cuboidId: 'cube-2', face: 'north' })

    expect(cuboidSelection.selectedCuboidId).toBe('cube-1')
    expect(faceSelection.selectedFace).toEqual({ cuboidId: 'cube-2', face: 'north' })
  })

  it('seleccionar un cuboid en selectionStore no afecta textureSelectionStore.selectedFace', () => {
    const cuboidSelection = useSelectionStore()
    const faceSelection = useTextureSelectionStore()

    faceSelection.selectFace({ cuboidId: 'cube-2', face: 'up' })
    cuboidSelection.select('cube-1')

    expect(faceSelection.selectedFace).toEqual({ cuboidId: 'cube-2', face: 'up' })
    expect(cuboidSelection.selectedCuboidId).toBe('cube-1')
  })

  it('selectionStore.ts mantiene EXACTAMENTE su contrato previo (solo selectedCuboidId/select) -- no se le agregó nada para soportar face picking', () => {
    const cuboidSelection = useSelectionStore()
    expect(Object.keys(cuboidSelection).filter((key) => !key.startsWith('$') && !key.startsWith('_'))).toEqual(
      expect.arrayContaining(['selectedCuboidId', 'select']),
    )
    expect('selectedFace' in cuboidSelection).toBe(false)
    expect('selectFace' in cuboidSelection).toBe(false)
  })
})
