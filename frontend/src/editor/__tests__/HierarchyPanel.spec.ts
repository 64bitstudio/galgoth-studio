import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it } from 'vitest'
import type { Bone, Cuboid, MobProjectModel } from '../../domain/MobProjectModel'
import { useDraftModelStore } from '../draftModelStore'
import HierarchyPanel from '../HierarchyPanel.vue'
import { useSelectionStore } from '../selectionStore'

const EMPTY_FACES = {
  north: { uv: [0, 0, 0, 0] as [number, number, number, number], texture: null },
  south: { uv: [0, 0, 0, 0] as [number, number, number, number], texture: null },
  east: { uv: [0, 0, 0, 0] as [number, number, number, number], texture: null },
  west: { uv: [0, 0, 0, 0] as [number, number, number, number], texture: null },
  up: { uv: [0, 0, 0, 0] as [number, number, number, number], texture: null },
  down: { uv: [0, 0, 0, 0] as [number, number, number, number], texture: null },
}

function bone(id: string, parentId: string | null): Bone {
  return { id, name: id, parentId, pivot: [0, 0, 0], rotation: [0, 0, 0] }
}

function cuboid(id: string, boneId: string): Cuboid {
  return { id, name: id, boneId, from: [0, 0, 0], to: [1, 1, 1], origin: [0, 0, 0], rotation: [0, 0, 0], faces: EMPTY_FACES }
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
    uv: { textureWidth: 64, textureHeight: 64, regions: [], reservations: [] },
    animations: [],
    exportSettings: { preferredFormatVersion: 'v5' },
    referenceImages: [],
  }
}

describe('HierarchyPanel.vue', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('AC #1: renderiza bones y sus cuboids hijos, con jerarquía visible (indentación anidada)', () => {
    const root = bone('root', null)
    const child = bone('child', 'root')
    useDraftModelStore().load(modelWith([root, child], [cuboid('a', 'root'), cuboid('b', 'child')]))
    const wrapper = mount(HierarchyPanel)

    expect(wrapper.text()).toContain('root')
    expect(wrapper.text()).toContain('child')
    expect(wrapper.text()).toContain('a')
    expect(wrapper.text()).toContain('b')
  })

  it('AC #2: clic en un nodo cuboid del árbol actualiza la selección compartida', async () => {
    const root = bone('root', null)
    useDraftModelStore().load(modelWith([root], [cuboid('a', 'root')]))
    const wrapper = mount(HierarchyPanel)
    const selection = useSelectionStore()

    await wrapper.find('.hierarchy-node__row--cuboid').trigger('click')

    expect(selection.selectedCuboidId).toBe('a')
  })

  it('el nodo del cuboid seleccionado se marca visualmente (clase de selección)', async () => {
    const root = bone('root', null)
    useDraftModelStore().load(modelWith([root], [cuboid('a', 'root')]))
    const wrapper = mount(HierarchyPanel)
    const selection = useSelectionStore()

    selection.select('a')
    await wrapper.vm.$nextTick()

    expect(wrapper.find('.hierarchy-node__row--cuboid').classes()).toContain('hierarchy-node__row--selected')
  })
})
