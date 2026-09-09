import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { MobProjectModel } from '../../domain/MobProjectModel'
import { useSelectionStore } from '../../editor/selectionStore'

// Ver nota en ThreeViewportService.spec.ts -- jsdom no tiene WebGL real.
vi.mock('three', async (importOriginal) => {
  const actual = await importOriginal<typeof import('three')>()
  class FakeWebGLRenderer {
    domElement = document.createElement('canvas')
    setSize = vi.fn()
    render = vi.fn()
  }
  return { ...actual, WebGLRenderer: FakeWebGLRenderer }
})

const { threeViewportService } = await import('../ThreeViewportService')
const { default: ThreeViewport } = await import('../ThreeViewport.vue')

function emptyModel(name: string): MobProjectModel {
  return {
    mobId: 'test-mob',
    projectId: 'test-project',
    name,
    baseType: 'humanoid',
    units: 'minecraft_pixels',
    bones: [],
    cuboids: [],
    texture: { width: 64, height: 64, storageKey: null },
    uv: { textureWidth: 64, textureHeight: 64, regions: [] },
    animations: [],
    exportSettings: { preferredFormatVersion: 'v5' },
    referenceImages: [],
  }
}

describe('ThreeViewport.vue', () => {
  beforeEach(() => {
    // ThreeViewport.vue usa useSelectionStore() (ticket 017) -- necesita
    // una Pinia activa incluso montado fuera de una app real.
    setActivePinia(createPinia())
  })

  afterEach(() => {
    threeViewportService.stopRenderLoop()
    vi.restoreAllMocks()
  })

  it('al montarse, adjunta el canvas compartido dentro de su contenedor y carga el modelo', () => {
    const wrapper = mount(ThreeViewport, { props: { model: emptyModel('mob-uno') } })

    expect(wrapper.element.contains(threeViewportService.canvas)).toBe(true)
    expect(threeViewportService.scene.children.some((c) => c.name === 'mob-uno')).toBe(true)
  })

  it('al desmontarse, libera (detach) el canvas compartido', () => {
    const wrapper = mount(ThreeViewport, { props: { model: emptyModel('mob-uno') } })
    const canvas = threeViewportService.canvas

    wrapper.unmount()

    expect(canvas.parentElement).toBeNull()
  })

  it('al cambiar el prop model, actualiza el mob en escena sin duplicarlo', async () => {
    const wrapper = mount(ThreeViewport, { props: { model: emptyModel('mob-uno') } })

    await wrapper.setProps({ model: emptyModel('mob-dos') })

    const mobGroups = threeViewportService.scene.children.filter(
      (c) => c.name === 'mob-uno' || c.name === 'mob-dos',
    )
    expect(mobGroups).toHaveLength(1)
    expect(mobGroups[0]!.name).toBe('mob-dos')
  })

  // VTU's trigger() no puede asignar clientX/clientY a un MouseEvent
  // sintético (son getters de solo lectura en jsdom) -- se despacha el
  // evento nativo directo sobre el elemento para controlar la posición.
  function dispatch(element: Element, type: string, clientX: number, clientY: number): void {
    element.dispatchEvent(new MouseEvent(type, { clientX, clientY, bubbles: true }))
  }

  it('ticket 017 AC #3: un click (sin arrastre) en el canvas selecciona el cuboid bajo el cursor en el store compartido', () => {
    const wrapper = mount(ThreeViewport, { props: { model: emptyModel('mob-uno') } })
    const selection = useSelectionStore()
    vi.spyOn(threeViewportService, 'pickCuboidIdAt').mockReturnValue('cube-1')

    dispatch(wrapper.element, 'pointerdown', 100, 100)
    dispatch(wrapper.element, 'click', 100, 100)

    expect(selection.selectedCuboidId).toBe('cube-1')
  })

  it('un click en vacío (pickCuboidIdAt devuelve null) deselecciona', () => {
    const wrapper = mount(ThreeViewport, { props: { model: emptyModel('mob-uno') } })
    const selection = useSelectionStore()
    selection.select('cube-previo')
    vi.spyOn(threeViewportService, 'pickCuboidIdAt').mockReturnValue(null)

    dispatch(wrapper.element, 'pointerdown', 100, 100)
    dispatch(wrapper.element, 'click', 100, 100)

    expect(selection.selectedCuboidId).toBeNull()
  })

  it('un arrastre de órbita (pointerdown lejos del click) NO dispara selección', () => {
    const wrapper = mount(ThreeViewport, { props: { model: emptyModel('mob-uno') } })
    const selection = useSelectionStore()
    const pickSpy = vi.spyOn(threeViewportService, 'pickCuboidIdAt').mockReturnValue('cube-1')

    dispatch(wrapper.element, 'pointerdown', 0, 0)
    dispatch(wrapper.element, 'click', 200, 200) // se movió >5px -> fue un drag de órbita

    expect(pickSpy).not.toHaveBeenCalled()
    expect(selection.selectedCuboidId).toBeNull()
  })
})
