import { mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { MobProjectModel } from '../../domain/MobProjectModel'

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
})
