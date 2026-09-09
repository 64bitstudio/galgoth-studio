import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { Bone, Cuboid, MobProjectModel } from '../../domain/MobProjectModel'
import { useDraftModelStore } from '../draftModelStore'
import { useSelectionStore } from '../selectionStore'

// EditorToolbar.vue importa ThreeViewportService.ts, que construye el
// singleton (new WebGLRenderer(...)) al cargar el módulo -- mismo motivo
// que en ThreeViewport.spec.ts, jsdom no tiene WebGL real.
vi.mock('three', async (importOriginal) => {
  const actual = await importOriginal<typeof import('three')>()
  class FakeWebGLRenderer {
    domElement = document.createElement('canvas')
    setSize = vi.fn()
    render = vi.fn()
  }
  return { ...actual, WebGLRenderer: FakeWebGLRenderer }
})

vi.mock('../draftPersistenceApi', () => ({ saveRevision: vi.fn() }))
vi.mock('../thumbnailApi', () => ({ uploadThumbnail: vi.fn() }))

const { default: EditorToolbar } = await import('../EditorToolbar.vue')
const { threeViewportService } = await import('../../viewport/ThreeViewportService')
const { saveRevision } = await import('../draftPersistenceApi')
const { uploadThumbnail } = await import('../thumbnailApi')

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

function findButton(wrapper: ReturnType<typeof mount>, label: string) {
  const button = wrapper.findAll('button').find((b) => b.text() === label)
  if (!button) {
    throw new Error(`No se encontró un botón con texto '${label}'`)
  }
  return button
}

describe('EditorToolbar.vue', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('los botones Move/Scale/Rotate cambian el modo del gizmo compartido y marcan el botón activo', async () => {
    useDraftModelStore().load(modelWith([bone('b', null)], []))
    const setModeSpy = vi.spyOn(threeViewportService, 'setTransformMode')
    const wrapper = mount(EditorToolbar)

    await findButton(wrapper, 'Scale').trigger('click')
    expect(setModeSpy).toHaveBeenCalledWith('scale')
    expect(findButton(wrapper, 'Scale').classes()).toContain('editor-toolbar__button--active')

    await findButton(wrapper, 'Rotate').trigger('click')
    expect(setModeSpy).toHaveBeenCalledWith('rotate')
    expect(findButton(wrapper, 'Rotate').classes()).toContain('editor-toolbar__button--active')
    expect(findButton(wrapper, 'Scale').classes()).not.toContain('editor-toolbar__button--active')
  })

  it('Add cuboid usa el bone del cuboid seleccionado como destino y selecciona el nuevo cuboid', async () => {
    const draft = useDraftModelStore()
    draft.load(modelWith([bone('b1', null), bone('b2', null)], [cuboid('c1', 'b2')]))
    const selection = useSelectionStore()
    selection.select('c1')
    const wrapper = mount(EditorToolbar)

    await findButton(wrapper, 'Add cuboid').trigger('click')

    const created = draft.model!.cuboids.find((c) => c.id !== 'c1')
    expect(created?.boneId).toBe('b2') // bone del cuboid seleccionado, no el primero del modelo
    expect(selection.selectedCuboidId).toBe(created?.id)
  })

  it('Add cuboid sin selección usa el primer bone del modelo como destino', async () => {
    const draft = useDraftModelStore()
    draft.load(modelWith([bone('unico', null)], []))
    const wrapper = mount(EditorToolbar)

    await findButton(wrapper, 'Add cuboid').trigger('click')

    expect(draft.model!.cuboids).toHaveLength(1)
    expect(draft.model!.cuboids[0]!.boneId).toBe('unico')
  })

  it('Add cuboid sin ningún bone en el modelo no hace nada (sin bone destino posible)', async () => {
    const draft = useDraftModelStore()
    draft.load(modelWith([], []))
    const wrapper = mount(EditorToolbar)

    await findButton(wrapper, 'Add cuboid').trigger('click')

    expect(draft.model!.cuboids).toHaveLength(0)
  })

  it('Add bone crea un bone hijo del bone de contexto', async () => {
    const draft = useDraftModelStore()
    draft.load(modelWith([bone('padre', null)], []))
    const selection = useSelectionStore()
    const wrapper = mount(EditorToolbar)
    selection.select(null)

    await findButton(wrapper, 'Add bone').trigger('click')

    expect(draft.model!.bones).toHaveLength(2)
    const created = draft.model!.bones.find((b) => b.id !== 'padre')
    expect(created?.parentId).toBe('padre')
  })

  it('Duplicate y Delete están deshabilitados sin selección, y no hacen nada si se fuerza el click', async () => {
    const draft = useDraftModelStore()
    draft.load(modelWith([bone('b', null)], [cuboid('c1', 'b')]))
    const wrapper = mount(EditorToolbar)

    const duplicateBtn = findButton(wrapper, 'Duplicate')
    const deleteBtn = findButton(wrapper, 'Delete')
    expect(duplicateBtn.attributes('disabled')).toBeDefined()
    expect(deleteBtn.attributes('disabled')).toBeDefined()

    await duplicateBtn.trigger('click')
    await deleteBtn.trigger('click')

    expect(draft.model!.cuboids).toHaveLength(1) // nada cambió
  })

  it('Duplicate con selección crea una copia y la selecciona', async () => {
    const draft = useDraftModelStore()
    draft.load(modelWith([bone('b', null)], [cuboid('c1', 'b')]))
    const selection = useSelectionStore()
    selection.select('c1')
    const wrapper = mount(EditorToolbar)

    await findButton(wrapper, 'Duplicate').trigger('click')

    expect(draft.model!.cuboids).toHaveLength(2)
    expect(selection.selectedCuboidId).not.toBe('c1')
    expect(selection.selectedCuboidId).not.toBeNull()
  })

  it('Delete con selección elimina el cuboid y limpia la selección', async () => {
    const draft = useDraftModelStore()
    draft.load(modelWith([bone('b', null)], [cuboid('c1', 'b')]))
    const selection = useSelectionStore()
    selection.select('c1')
    const wrapper = mount(EditorToolbar)

    await findButton(wrapper, 'Delete').trigger('click')

    expect(draft.model!.cuboids).toHaveLength(0)
    expect(selection.selectedCuboidId).toBeNull()
  })

  it('muestra draft.lastError cuando una operación es rechazada', async () => {
    const draft = useDraftModelStore()
    draft.load(modelWith([bone('b', null)], [cuboid('c1', 'b')]))
    const selection = useSelectionStore()
    selection.select('c1')
    const wrapper = mount(EditorToolbar)

    draft.deleteBoneCascade('no-existe') // rechazo real -> lastError
    await wrapper.vm.$nextTick()

    expect(wrapper.text()).toContain(draft.lastError)
  })

  describe('Undo/Redo (ticket 019)', () => {
    it('Undo/Redo están deshabilitados sin historial, y se habilitan tras un cambio', async () => {
      const draft = useDraftModelStore()
      draft.load(modelWith([bone('b', null)], [cuboid('c1', 'b')]))
      const selection = useSelectionStore()
      selection.select('c1')
      const wrapper = mount(EditorToolbar)

      expect(findButton(wrapper, 'Undo').attributes('disabled')).toBeDefined()
      expect(findButton(wrapper, 'Redo').attributes('disabled')).toBeDefined()

      await findButton(wrapper, 'Duplicate').trigger('click')

      expect(findButton(wrapper, 'Undo').attributes('disabled')).toBeUndefined()
      expect(findButton(wrapper, 'Redo').attributes('disabled')).toBeDefined()
    })

    it('el botón Undo deshace el último Command, y Redo lo rehace', async () => {
      const draft = useDraftModelStore()
      draft.load(modelWith([bone('b', null)], [cuboid('c1', 'b')]))
      const selection = useSelectionStore()
      selection.select('c1')
      const wrapper = mount(EditorToolbar)

      await findButton(wrapper, 'Delete').trigger('click')
      expect(draft.model!.cuboids).toHaveLength(0)

      await findButton(wrapper, 'Undo').trigger('click')
      expect(draft.model!.cuboids).toHaveLength(1)

      await findButton(wrapper, 'Redo').trigger('click')
      expect(draft.model!.cuboids).toHaveLength(0)
    })

    it('Cmd/Ctrl+Z deshace y Cmd/Ctrl+Shift+Z rehace desde el teclado', async () => {
      const draft = useDraftModelStore()
      draft.load(modelWith([bone('b', null)], [cuboid('c1', 'b')]))
      const selection = useSelectionStore()
      selection.select('c1')
      mount(EditorToolbar)

      draft.deleteCuboid('c1')
      expect(draft.model!.cuboids).toHaveLength(0)

      window.dispatchEvent(new KeyboardEvent('keydown', { key: 'z', ctrlKey: true }))
      expect(draft.model!.cuboids).toHaveLength(1)

      window.dispatchEvent(new KeyboardEvent('keydown', { key: 'z', ctrlKey: true, shiftKey: true }))
      expect(draft.model!.cuboids).toHaveLength(0)
    })

    it('Cmd/Ctrl+Z NO actúa si el foco está en un input (evita pelear con el undo nativo del campo)', async () => {
      const draft = useDraftModelStore()
      draft.load(modelWith([bone('b', null)], [cuboid('c1', 'b')]))
      const selection = useSelectionStore()
      selection.select('c1')
      mount(EditorToolbar)
      draft.deleteCuboid('c1')

      const input = document.createElement('input')
      document.body.appendChild(input)
      input.dispatchEvent(new KeyboardEvent('keydown', { key: 'z', ctrlKey: true, bubbles: true }))
      input.remove()

      expect(draft.model!.cuboids).toHaveLength(0) // sin cambios -- el atajo no actuó
    })
  })

  describe('Guardar (ticket 023)', () => {
    it('Guardar exitoso comitea la revisión y luego captura+sube el thumbnail', async () => {
      const draft = useDraftModelStore()
      draft.load(modelWith([bone('b', null)], [cuboid('c1', 'b')]))
      vi.mocked(saveRevision).mockResolvedValue({ created: true, revisionNumber: 4, reason: null })
      const png = new Blob(['fake-png'], { type: 'image/png' })
      const captureSpy = vi.spyOn(threeViewportService, 'captureThumbnail').mockResolvedValue(png)
      vi.mocked(uploadThumbnail).mockResolvedValue(undefined)
      const wrapper = mount(EditorToolbar)

      await findButton(wrapper, 'Guardar').trigger('click')
      await flushPromises()

      expect(saveRevision).toHaveBeenCalledWith('test-mob', draft.model)
      expect(captureSpy).toHaveBeenCalled()
      expect(uploadThumbnail).toHaveBeenCalledWith('test-mob', png)
      expect(wrapper.text()).toContain('Guardado (revisión 4).')
    })

    it('un Guardar sin cambios muestra el motivo del backend y NO intenta subir thumbnail', async () => {
      const draft = useDraftModelStore()
      draft.load(modelWith([bone('b', null)], [cuboid('c1', 'b')]))
      vi.mocked(saveRevision).mockResolvedValue({ created: false, revisionNumber: 2, reason: 'Sin cambios.' })
      const captureSpy = vi.spyOn(threeViewportService, 'captureThumbnail').mockResolvedValue(new Blob(['png'], { type: 'image/png' }))
      const wrapper = mount(EditorToolbar)

      await findButton(wrapper, 'Guardar').trigger('click')
      await flushPromises()

      expect(wrapper.text()).toContain('Sin cambios.')
      // Ambigüedad deliberada: aunque no hubo revisión nueva, el AC del
      // ticket no exige omitir el thumbnail en este caso -- se documenta
      // el comportamiento real (sí se intenta, igual que en un save con
      // cambios) en vez de asumir un requisito no confirmado.
      expect(captureSpy).toHaveBeenCalled()
    })

    it('si saveRevision falla, se muestra el error y NUNCA se intenta el thumbnail', async () => {
      const draft = useDraftModelStore()
      draft.load(modelWith([bone('b', null)], [cuboid('c1', 'b')]))
      vi.mocked(saveRevision).mockRejectedValue(new Error('El draft no pasa la validación.'))
      const captureSpy = vi.spyOn(threeViewportService, 'captureThumbnail')
      const wrapper = mount(EditorToolbar)

      await findButton(wrapper, 'Guardar').trigger('click')
      await flushPromises()

      expect(wrapper.text()).toContain('El draft no pasa la validación.')
      expect(captureSpy).not.toHaveBeenCalled()
    })

    it('si el thumbnail falla, el Guardar ya completado sigue mostrando su mensaje de éxito (fallo silencioso, solo console.warn)', async () => {
      const draft = useDraftModelStore()
      draft.load(modelWith([bone('b', null)], [cuboid('c1', 'b')]))
      vi.mocked(saveRevision).mockResolvedValue({ created: true, revisionNumber: 1, reason: null })
      vi.spyOn(threeViewportService, 'captureThumbnail').mockRejectedValue(new Error('boom'))
      const warnSpy = vi.spyOn(console, 'warn').mockImplementation(() => {})
      const wrapper = mount(EditorToolbar)

      await findButton(wrapper, 'Guardar').trigger('click')
      await flushPromises()

      expect(wrapper.text()).toContain('Guardado (revisión 1).')
      expect(warnSpy).toHaveBeenCalled()
    })

    it('el botón Guardar está deshabilitado mientras no hay modelo cargado', () => {
      const wrapper = mount(EditorToolbar)
      expect(findButton(wrapper, 'Guardar').attributes('disabled')).toBeDefined()
    })
  })
})
