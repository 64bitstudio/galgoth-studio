import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it } from 'vitest'
import { useSelectionStore } from '../selectionStore'

describe('useSelectionStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('arranca sin ninguna selección', () => {
    const store = useSelectionStore()
    expect(store.selectedCuboidId).toBeNull()
  })

  it('select() guarda el cuboidId dado', () => {
    const store = useSelectionStore()
    store.select('cube-1')
    expect(store.selectedCuboidId).toBe('cube-1')
  })

  it('select(null) deselecciona', () => {
    const store = useSelectionStore()
    store.select('cube-1')
    store.select(null)
    expect(store.selectedCuboidId).toBeNull()
  })

  it('es un singleton compartido -- dos llamadas a useSelectionStore() devuelven la misma instancia reactiva', () => {
    const storeA = useSelectionStore()
    const storeB = useSelectionStore()
    storeA.select('cube-1')
    expect(storeB.selectedCuboidId).toBe('cube-1')
  })
})
