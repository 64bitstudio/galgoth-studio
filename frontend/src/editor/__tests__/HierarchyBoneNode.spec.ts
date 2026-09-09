import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it } from 'vitest'
import type { Bone, Cuboid, MobProjectModel } from '../../domain/MobProjectModel'
import HierarchyBoneNode from '../HierarchyBoneNode.vue'
import { buildHierarchyTree } from '../hierarchyTree'
import { useDraftModelStore } from '../draftModelStore'

const EMPTY_FACES = {
  north: { uv: [0, 0, 0, 0] as [number, number, number, number], texture: null },
  south: { uv: [0, 0, 0, 0] as [number, number, number, number], texture: null },
  east: { uv: [0, 0, 0, 0] as [number, number, number, number], texture: null },
  west: { uv: [0, 0, 0, 0] as [number, number, number, number], texture: null },
  up: { uv: [0, 0, 0, 0] as [number, number, number, number], texture: null },
  down: { uv: [0, 0, 0, 0] as [number, number, number, number], texture: null },
}

function bone(id: string, parentId: string | null, pivot: [number, number, number] = [0, 0, 0]): Bone {
  return { id, name: id, parentId, pivot, rotation: [0, 0, 0] }
}

function cuboid(id: string, boneId: string): Cuboid {
  return { id, name: id, boneId, from: [-1, -1, -1], to: [1, 1, 1], origin: [0, 0, 0], rotation: [0, 0, 0], faces: EMPTY_FACES }
}

function modelWith(bones: Bone[], cuboids: Cuboid[]): MobProjectModel {
  return {
    mobId: 'test-mob',
    projectId: 'test-project',
    name: 'Test',
    baseType: 'humanoid',
    units: 'minecraft_pixels',
    bones,
    cuboids,
    texture: { width: 64, height: 64, storageKey: null },
    uv: { textureWidth: 64, textureHeight: 64, regions: [] },
    animations: [],
    exportSettings: { preferredFormatVersion: 'v5' },
    referenceImages: [],
  }
}

/** Monta el nodo raíz del árbol de jerarquía real (buildHierarchyTree), como lo hace HierarchyPanel.vue. */
function mountRootNode(bones: Bone[], cuboids: Cuboid[]) {
  const draft = useDraftModelStore()
  draft.load(modelWith(bones, cuboids))
  const [rootNode] = buildHierarchyTree(draft.model!)
  const wrapper = mount(HierarchyBoneNode, { props: { node: rootNode! } })
  return { draft, wrapper }
}

describe('HierarchyBoneNode.vue', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('el editor de pivote está oculto por defecto y aparece al hacer click en "pivot"', async () => {
    const { wrapper } = mountRootNode([bone('root', null, [1, 2, 3])], [])

    expect(wrapper.find('.hierarchy-bone-node__pivot-editor').exists()).toBe(false)

    await wrapper.find('.hierarchy-bone-node__icon-button').trigger('click')

    const editor = wrapper.find('.hierarchy-bone-node__pivot-editor')
    expect(editor.exists()).toBe(true)
    const inputs = editor.findAll('input')
    expect(inputs.map((i) => (i.element as HTMLInputElement).value)).toEqual(['1', '2', '3'])
  })

  it('commitPivot actualiza solo el eje editado y conserva los otros dos', async () => {
    const { draft, wrapper } = mountRootNode([bone('root', null, [1, 2, 3])], [])
    await wrapper.find('.hierarchy-bone-node__icon-button').trigger('click')

    const yInput = wrapper.find('[aria-label="pivot y"]')
    await yInput.setValue('40')
    await yInput.trigger('change')

    expect(draft.model!.bones[0]!.pivot).toEqual([1, 40, 3])
  })

  it('commitPivot ignora un valor no numérico (no llama al store, no lanza)', async () => {
    // <input type="number"> sanitiza texto no numérico a '' antes de que
    // el handler lo vea (Number('') = 0, no dispara el guard) -- se
    // fuerza un valor no numérico saltándose esa sanitización del DOM
    // para poder ejercitar el guard `Number.isNaN` del propio componente.
    const { draft, wrapper } = mountRootNode([bone('root', null, [1, 2, 3])], [])
    await wrapper.find('.hierarchy-bone-node__icon-button').trigger('click')

    const xInput = wrapper.find('[aria-label="pivot x"]')
    const element = xInput.element as HTMLInputElement
    Object.defineProperty(element, 'value', { value: 'no-es-un-numero', configurable: true })
    await xInput.trigger('change')

    expect(draft.model!.bones[0]!.pivot).toEqual([1, 2, 3]) // sin cambios
  })

  it('el botón ✕ muestra la advertencia de cascada con los conteos reales antes de borrar', async () => {
    const child = bone('child', 'root')
    const { wrapper } = mountRootNode([bone('root', null), child], [cuboid('c1', 'child'), cuboid('c2', 'child')])

    expect(wrapper.find('.hierarchy-bone-node__delete-warning').exists()).toBe(false)

    await wrapper.findAll('.hierarchy-bone-node__icon-button')[1]!.trigger('click')

    const warning = wrapper.find('.hierarchy-bone-node__delete-warning')
    expect(warning.exists()).toBe(true)
    expect(warning.text()).toContain('1 bone(s)')
    expect(warning.text()).toContain('2 cuboid(s)')
  })

  it('Cancelar cierra la advertencia sin borrar nada', async () => {
    const { draft, wrapper } = mountRootNode([bone('root', null)], [cuboid('c1', 'root')])

    await wrapper.findAll('.hierarchy-bone-node__icon-button')[1]!.trigger('click')
    await wrapper.findAll('button').find((b) => b.text() === 'Cancelar')!.trigger('click')

    expect(wrapper.find('.hierarchy-bone-node__delete-warning').exists()).toBe(false)
    expect(draft.model!.bones).toHaveLength(1) // sin cambios
  })

  it('Confirmar borra el bone (y su cascada) vía el draft store', async () => {
    const { draft, wrapper } = mountRootNode([bone('root', null)], [cuboid('c1', 'root')])

    await wrapper.findAll('.hierarchy-bone-node__icon-button')[1]!.trigger('click')
    await wrapper.findAll('button').find((b) => b.text() === 'Confirmar')!.trigger('click')

    expect(draft.model!.bones).toHaveLength(0)
    expect(draft.model!.cuboids).toHaveLength(0)
    expect(wrapper.find('.hierarchy-bone-node__delete-warning').exists()).toBe(false)
  })
})
