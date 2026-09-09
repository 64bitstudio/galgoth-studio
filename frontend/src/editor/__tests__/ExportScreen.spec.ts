import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../../api/ApiError'

const getMob = vi.fn()
vi.mock('../../projects/mobsApi', () => ({
  getMob: (...args: unknown[]) => getMob(...args),
}))

const getDraft = vi.fn()
const saveRevision = vi.fn()
vi.mock('../draftPersistenceApi', () => ({
  getDraft: (...args: unknown[]) => getDraft(...args),
  saveRevision: (...args: unknown[]) => saveRevision(...args),
}))

const getExportStatus = vi.fn()
const downloadBbmodel = vi.fn()
vi.mock('../mobExportApi', () => ({
  getExportStatus: (...args: unknown[]) => getExportStatus(...args),
  downloadBbmodel: (...args: unknown[]) => downloadBbmodel(...args),
}))

const { default: ExportScreen } = await import('../ExportScreen.vue')

async function routerAt(projectId: string, mobId: string): Promise<Router> {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/projects/:projectId/mobs/:mobId/export', component: ExportScreen }],
  })
  await router.push(`/projects/${projectId}/mobs/${mobId}/export`)
  return router
}

const mobSummary = { id: 'mob-1', name: 'Carcomido', baseType: 'humanoid' as const, status: 'draft' as const, thumbnailKey: null, updatedAt: '' }
const emptyModel = { mobId: 'mob-1' } as never

function buttons(wrapper: ReturnType<typeof mount>) {
  return wrapper.findAll('button')
}

function buttonWithText(wrapper: ReturnType<typeof mount>, text: string) {
  return buttons(wrapper).find((b) => b.text() === text)
}

beforeAll(() => {
  if (!URL.createObjectURL) {
    URL.createObjectURL = vi.fn(() => 'blob:fake')
  }
  if (!URL.revokeObjectURL) {
    URL.revokeObjectURL = vi.fn()
  }
})

describe('ExportScreen.vue', () => {
  beforeEach(() => {
    getMob.mockReset().mockResolvedValue(mobSummary)
    getDraft.mockReset()
    saveRevision.mockReset()
    getExportStatus.mockReset()
    downloadBbmodel.mockReset()
  })

  afterEach(() => {
    vi.clearAllMocks()
  })

  it('un mob sin draft ni revisión no ofrece ninguna acción de exportar', async () => {
    getExportStatus.mockResolvedValue({
      mobId: 'mob-1', mobName: 'Carcomido', hasSavedRevision: false, hasUnsavedChanges: false, fmmCompatible: null, fmmIssues: [],
    })
    const wrapper = mount(ExportScreen, { global: { plugins: [await routerAt('p1', 'mob-1')] } })
    await flushPromises()

    expect(wrapper.text()).toContain('todavía no tiene ningún contenido')
    expect(buttonWithText(wrapper, 'Exportar .bbmodel')).toBeUndefined()
  })

  it('un mob con draft pero sin ninguna revisión guardada solo ofrece Guardar y exportar', async () => {
    getExportStatus.mockResolvedValue({
      mobId: 'mob-1', mobName: 'Carcomido', hasSavedRevision: false, hasUnsavedChanges: true, fmmCompatible: null, fmmIssues: [],
    })
    getDraft.mockResolvedValue({ mobId: 'mob-1', draftVersion: 1, model: emptyModel, updatedAt: '' })
    const wrapper = mount(ExportScreen, { global: { plugins: [await routerAt('p1', 'mob-1')] } })
    await flushPromises()

    expect(buttonWithText(wrapper, 'Guardar y exportar')).toBeDefined()
    expect(buttonWithText(wrapper, 'Exportar última versión guardada')).toBeUndefined()
  })

  it('sin cambios sin guardar muestra un único botón Exportar .bbmodel y el panel FMM real', async () => {
    getExportStatus.mockResolvedValue({
      mobId: 'mob-1', mobName: 'Carcomido', hasSavedRevision: true, hasUnsavedChanges: false, fmmCompatible: true, fmmIssues: [],
    })
    const wrapper = mount(ExportScreen, { global: { plugins: [await routerAt('p1', 'mob-1')] } })
    await flushPromises()

    expect(wrapper.text()).toContain('FreeMinecraftModels')
    expect(wrapper.text()).toContain('Modelo listo para usar en tu servidor')
    expect(buttonWithText(wrapper, 'Exportar .bbmodel')).toBeDefined()
    expect(buttonWithText(wrapper, 'Guardar y exportar')).toBeUndefined()
  })

  it('con cambios sin guardar y una revisión previa ofrece las 3 acciones del AC', async () => {
    getExportStatus.mockResolvedValue({
      mobId: 'mob-1', mobName: 'Carcomido', hasSavedRevision: true, hasUnsavedChanges: true, fmmCompatible: false,
      fmmIssues: [{ severity: 'ERROR', rule: 'CUBOID_ZERO_SIZE', element: 'c1', message: 'El cuboid c1 tiene dimensión cero.' }],
    })
    getDraft.mockResolvedValue({ mobId: 'mob-1', draftVersion: 2, model: emptyModel, updatedAt: '' })
    const wrapper = mount(ExportScreen, { global: { plugins: [await routerAt('p1', 'mob-1')] } })
    await flushPromises()

    expect(buttonWithText(wrapper, 'Guardar y exportar')).toBeDefined()
    expect(buttonWithText(wrapper, 'Exportar última versión guardada')).toBeDefined()
    expect(buttonWithText(wrapper, 'Cancelar')).toBeDefined()
    expect(wrapper.text()).toContain('El cuboid c1 tiene dimensión cero.')
  })

  it('Exportar .bbmodel llama a downloadBbmodel y muestra el filename real exportado', async () => {
    getExportStatus.mockResolvedValue({
      mobId: 'mob-1', mobName: 'Carcomido', hasSavedRevision: true, hasUnsavedChanges: false, fmmCompatible: true, fmmIssues: [],
    })
    downloadBbmodel.mockResolvedValue({ filename: 'Carcomido.bbmodel', blob: new Blob(['{}']) })
    const wrapper = mount(ExportScreen, { global: { plugins: [await routerAt('p1', 'mob-1')] } })
    await flushPromises()

    await buttonWithText(wrapper, 'Exportar .bbmodel')!.trigger('click')
    await flushPromises()

    expect(downloadBbmodel).toHaveBeenCalledWith('mob-1')
    expect(wrapper.text()).toContain('Exportado: Carcomido.bbmodel')
  })

  it('Guardar y exportar pide confirmación antes de guardar la revisión y exportar', async () => {
    getExportStatus.mockResolvedValueOnce({
      mobId: 'mob-1', mobName: 'Carcomido', hasSavedRevision: true, hasUnsavedChanges: true, fmmCompatible: true, fmmIssues: [],
    })
    getDraft.mockResolvedValue({ mobId: 'mob-1', draftVersion: 2, model: emptyModel, updatedAt: '' })
    saveRevision.mockResolvedValue({ created: true, revisionNumber: 2, reason: null })
    downloadBbmodel.mockResolvedValue({ filename: 'Carcomido.bbmodel', blob: new Blob(['{}']) })
    getExportStatus.mockResolvedValue({
      mobId: 'mob-1', mobName: 'Carcomido', hasSavedRevision: true, hasUnsavedChanges: false, fmmCompatible: true, fmmIssues: [],
    })
    const wrapper = mount(ExportScreen, { global: { plugins: [await routerAt('p1', 'mob-1')] } })
    await flushPromises()

    await buttonWithText(wrapper, 'Guardar y exportar')!.trigger('click')
    await flushPromises()
    expect(saveRevision).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('¿Confirmar?')

    await buttonWithText(wrapper, 'Confirmar')!.trigger('click')
    await flushPromises()

    expect(saveRevision).toHaveBeenCalledWith('mob-1', emptyModel)
    expect(downloadBbmodel).toHaveBeenCalledWith('mob-1')
  })

  it('un mob inexistente muestra un error explícito, nunca una pantalla rota', async () => {
    getMob.mockRejectedValue(new ApiError('No existe.', 404, 'MOB_NOT_FOUND'))
    getExportStatus.mockResolvedValue({
      mobId: 'mob-1', mobName: 'Carcomido', hasSavedRevision: false, hasUnsavedChanges: false, fmmCompatible: null, fmmIssues: [],
    })
    const wrapper = mount(ExportScreen, { global: { plugins: [await routerAt('p1', 'no-existe')] } })
    await flushPromises()

    expect(wrapper.text()).toContain('Este mob no existe')
  })
})
