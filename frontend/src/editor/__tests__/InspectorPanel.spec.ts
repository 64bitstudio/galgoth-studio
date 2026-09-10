import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it } from 'vitest'
import type { Bone, Cuboid, MobProjectModel } from '../../domain/MobProjectModel'
import { useDraftModelStore } from '../draftModelStore'
import { useSelectionStore } from '../selectionStore'
import InspectorPanel from '../InspectorPanel.vue'

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
  return {
    id,
    name: id,
    boneId,
    from: [-2, -2, -2],
    to: [2, 2, 2],
    origin: [10, 20, 30],
    rotation: [0, 45, 0],
    faces: EMPTY_FACES,
  }
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

function mountWithSelection(cuboidId: string | null) {
  const draft = useDraftModelStore()
  draft.load(modelWith([bone('root', null, [1, 2, 3])], [cuboid('armRight', 'root')]))
  const selection = useSelectionStore()
  selection.select(cuboidId)
  const wrapper = mount(InspectorPanel)
  return { draft, wrapper }
}

function axisInput(wrapper: ReturnType<typeof mount>, fieldLabel: string, axis: 'X' | 'Y' | 'Z') {
  return wrapper.find(`[aria-label="${fieldLabel} ${axis}"]`)
}

describe('InspectorPanel.vue', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('sin selección muestra un estado vacío intencional (nunca un panel en blanco)', () => {
    const { wrapper } = mountWithSelection(null)
    expect(wrapper.text()).toContain('Selecciona un elemento')
  })

  it('con un cuboid seleccionado muestra su nombre y los 4 grupos reales del mockup', () => {
    const { wrapper } = mountWithSelection('armRight')
    expect(wrapper.text()).toContain('armRight')
    expect(wrapper.text()).toContain('Posición')
    expect(wrapper.text()).toContain('Tamaño')
    expect(wrapper.text()).toContain('Rotación')
    expect(wrapper.text()).toContain('Pivot')
  })

  it('Posición edita el eje Y y llama a moveSelectedCuboid con el delta correcto (mismo método que ya usa el gizmo)', async () => {
    const { draft, wrapper } = mountWithSelection('armRight')

    const input = axisInput(wrapper, 'Posición', 'Y')
    await input.setValue('25') // origin.y era 20 -> delta esperado [0, 5, 0]
    await input.trigger('change')

    const updated = draft.model!.cuboids[0]!
    expect(updated.origin).toEqual([10, 25, 30])
    // from/to se mueven junto con origin (mismo comportamiento de moveCuboid) -- tamaño sin cambios.
    expect(updated.to[1] - updated.from[1]).toBe(4)
  })

  it('Tamaño edita el eje X y llama a resizeSelectedCuboid con el factor de escala correcto', async () => {
    const { draft, wrapper } = mountWithSelection('armRight')

    // Tamaño X actual = to.x(2) - from.x(-2) = 4. Pide 8 -> escala 2.
    const input = axisInput(wrapper, 'Tamaño', 'X')
    await input.setValue('8')
    await input.trigger('change')

    const updated = draft.model!.cuboids[0]!
    expect(updated.to[0] - updated.from[0]).toBe(8)
    // Y/Z sin cambios (resizeCuboid escala eje por eje).
    expect(updated.to[1] - updated.from[1]).toBe(4)
  })

  it('Rotación edita el eje Y y llama a rotateSelectedCuboid con el delta correcto', async () => {
    const { draft, wrapper } = mountWithSelection('armRight')

    // rotation.y actual = 45. Pide 90 -> delta esperado [0, 45, 0].
    const input = axisInput(wrapper, 'Rotación', 'Y')
    await input.setValue('90')
    await input.trigger('change')

    expect(draft.model!.cuboids[0]!.rotation).toEqual([0, 90, 0])
  })

  it('Pivot edita el bone dueño del cuboid seleccionado (set absoluto, mismo método que el editor de jerarquía movido acá)', async () => {
    const { draft, wrapper } = mountWithSelection('armRight')

    const input = axisInput(wrapper, 'Pivot', 'Z')
    await input.setValue('99')
    await input.trigger('change')

    expect(draft.model!.bones[0]!.pivot).toEqual([1, 2, 99])
  })

  it('un valor no numérico no llama a ningún método del store (guard silencioso, sin lanzar)', async () => {
    const { draft, wrapper } = mountWithSelection('armRight')

    const input = axisInput(wrapper, 'Posición', 'X')
    const element = input.element as HTMLInputElement
    Object.defineProperty(element, 'value', { value: 'no-es-un-numero', configurable: true })
    await input.trigger('change')

    expect(draft.model!.cuboids[0]!.origin).toEqual([10, 20, 30]) // sin cambios
  })
})
