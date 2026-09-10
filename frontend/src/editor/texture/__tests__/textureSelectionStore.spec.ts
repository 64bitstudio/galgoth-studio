import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it } from 'vitest'
import { useTextureSelectionStore } from '../textureSelectionStore'

describe('useTextureSelectionStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('arranca sin ninguna cara seleccionada', () => {
    const store = useTextureSelectionStore()
    expect(store.selectedFace).toBeNull()
  })

  it('selectFace() guarda el {cuboidId, face} dado', () => {
    const store = useTextureSelectionStore()
    store.selectFace({ cuboidId: 'cube-1', face: 'north' })
    expect(store.selectedFace).toEqual({ cuboidId: 'cube-1', face: 'north' })
  })

  it('selectFace(null) deselecciona', () => {
    const store = useTextureSelectionStore()
    store.selectFace({ cuboidId: 'cube-1', face: 'north' })
    store.selectFace(null)
    expect(store.selectedFace).toBeNull()
  })

  it('es un singleton compartido -- dos llamadas a useTextureSelectionStore() devuelven la misma instancia reactiva', () => {
    const storeA = useTextureSelectionStore()
    const storeB = useTextureSelectionStore()
    storeA.selectFace({ cuboidId: 'cube-1', face: 'up' })
    expect(storeB.selectedFace).toEqual({ cuboidId: 'cube-1', face: 'up' })
  })
})
