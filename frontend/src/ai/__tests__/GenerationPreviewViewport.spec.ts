import { mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { MobProjectModel } from '../../domain/MobProjectModel'
import { emptyPreviewModel } from '../generationEvents'

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

const { threeViewportService } = await import('../../viewport/ThreeViewportService')
const { default: GenerationPreviewViewport } = await import('../GenerationPreviewViewport.vue')

describe('GenerationPreviewViewport.vue', () => {
  afterEach(() => {
    threeViewportService.detach()
  })

  it('al montar, adjunta el canvas compartido a su contenedor -- sin pasar por useDraftModelStore, AC #2', () => {
    const attachSpy = vi.spyOn(threeViewportService, 'attachTo')
    const model = emptyPreviewModel('mob-1', 'project-1', 'Carcomido', 'humanoid')

    mount(GenerationPreviewViewport, { props: { model } })

    expect(attachSpy).toHaveBeenCalled()
  })

  it('al cambiar el modelo, llama a setModel con el nuevo estado (sin selección -- de solo lectura)', async () => {
    const setModelSpy = vi.spyOn(threeViewportService, 'setModel')
    const first = emptyPreviewModel('mob-1', 'project-1', 'Carcomido', 'humanoid')
    const wrapper = mount(GenerationPreviewViewport, { props: { model: first } })
    setModelSpy.mockClear()

    const second: MobProjectModel = { ...first, name: 'Carcomido v2' }
    await wrapper.setProps({ model: second })

    expect(setModelSpy).toHaveBeenCalledWith(second, null)
  })

  it('al desmontar, libera el canvas compartido (queda disponible para la próxima pantalla)', () => {
    const detachSpy = vi.spyOn(threeViewportService, 'detach')
    const model = emptyPreviewModel('mob-1', 'project-1', 'Carcomido', 'humanoid')
    const wrapper = mount(GenerationPreviewViewport, { props: { model } })

    wrapper.unmount()

    expect(detachSpy).toHaveBeenCalled()
  })
})
