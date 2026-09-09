import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it } from 'vitest'
import type { Bone, Cuboid, MobProjectModel } from '../../domain/MobProjectModel'
import HierarchyBoneNode from '../HierarchyBoneNode.vue'
import { buildHierarchyTree } from '../hierarchyTree'
import { useDraftModelStore } from '../draftModelStore'
import { useSelectionStore } from '../selectionStore'

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

  // Ticket 036 (pasada de fidelidad visual): la edición de pivote se movió
  // de este componente a InspectorPanel.vue -- las pruebas de esa
  // funcionalidad (misma lógica, `draft.setPivot`) viven ahora en
  // InspectorField.spec.ts / InspectorPanel.spec.ts, no acá.

  it('expandido por defecto -- muestra sus cuboids hijos sin necesidad de click', () => {
    const { wrapper } = mountRootNode([bone('root', null)], [cuboid('c1', 'root')])
    expect(wrapper.find('.hierarchy-node__row--cuboid').exists()).toBe(true)
  })

  it('un click en el chevron colapsa el nodo (oculta sus cuboids hijos)', async () => {
    const { wrapper } = mountRootNode([bone('root', null)], [cuboid('c1', 'root')])

    await wrapper.find('[aria-label="Contraer"]').trigger('click')

    expect(wrapper.find('.hierarchy-node__row--cuboid').exists()).toBe(false)
    expect(wrapper.find('[aria-label="Expandir"]').exists()).toBe(true)
  })

  it('un bone sin hijos no muestra chevron (nada para expandir/contraer)', () => {
    const { wrapper } = mountRootNode([bone('root', null)], [])
    expect(wrapper.find('[aria-label="Contraer"]').exists()).toBe(false)
    expect(wrapper.find('[aria-label="Expandir"]').exists()).toBe(false)
  })

  it('un click en un cuboid lo selecciona en el store compartido', async () => {
    const { wrapper } = mountRootNode([bone('root', null)], [cuboid('c1', 'root')])
    const selection = useSelectionStore()

    await wrapper.find('.hierarchy-node__row--cuboid').trigger('click')

    expect(selection.selectedCuboidId).toBe('c1')
  })

  it('el botón de eliminar muestra la advertencia de cascada con los conteos reales antes de borrar', async () => {
    const child = bone('child', 'root')
    const { wrapper } = mountRootNode([bone('root', null), child], [cuboid('c1', 'child'), cuboid('c2', 'child')])

    expect(wrapper.find('.hierarchy-node__delete-warning').exists()).toBe(false)

    await wrapper.find('[aria-label="Eliminar bone"]').trigger('click')

    const warning = wrapper.find('.hierarchy-node__delete-warning')
    expect(warning.exists()).toBe(true)
    expect(warning.text()).toContain('1 bone(s)')
    expect(warning.text()).toContain('2 cuboid(s)')
  })

  it('Cancelar cierra la advertencia sin borrar nada', async () => {
    const { draft, wrapper } = mountRootNode([bone('root', null)], [cuboid('c1', 'root')])

    await wrapper.find('[aria-label="Eliminar bone"]').trigger('click')
    await wrapper.findAll('button').find((b) => b.text() === 'Cancelar')!.trigger('click')

    expect(wrapper.find('.hierarchy-node__delete-warning').exists()).toBe(false)
    expect(draft.model!.bones).toHaveLength(1) // sin cambios
  })

  it('Confirmar borra el bone (y su cascada) vía el draft store', async () => {
    const { draft, wrapper } = mountRootNode([bone('root', null)], [cuboid('c1', 'root')])

    await wrapper.find('[aria-label="Eliminar bone"]').trigger('click')
    await wrapper.findAll('button').find((b) => b.text() === 'Confirmar')!.trigger('click')

    expect(draft.model!.bones).toHaveLength(0)
    expect(draft.model!.cuboids).toHaveLength(0)
    expect(wrapper.find('.hierarchy-node__delete-warning').exists()).toBe(false)
  })
})
