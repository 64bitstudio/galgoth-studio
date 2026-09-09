import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../../api/ApiError'
import { applyEdit, requestEditPlan } from '../aiEditApi'

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

const emptyModel = {
  mobId: 'mob-1',
  projectId: 'p1',
  name: 'Carcomido',
  baseType: 'humanoid' as const,
  units: 'minecraft_pixels' as const,
  bones: [],
  cuboids: [],
  texture: { width: 128, height: 128, storageKey: null },
  uv: { textureWidth: 128, textureHeight: 128, regions: [] },
  animations: [],
  exportSettings: { preferredFormatVersion: 'v5' as const },
  referenceImages: [],
}

describe('aiEditApi', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('requestEditPlan hace POST a /api/mobs/{mobId}/ai/edit-geometry con la instrucción y devuelve el plan real', async () => {
    const fetchMock = vi.fn<typeof fetch>(async () =>
      jsonResponse({
        jobId: 'job-1',
        summary: 'Manos más grandes',
        beforeCuboidCount: 14,
        beforeBoneCount: 7,
        afterCuboidCount: 14,
        afterBoneCount: 7,
        changedElements: [{ type: 'cuboid', id: 'hand_right', name: 'hand_right', changeKind: 'modified' }],
        beforeModel: emptyModel,
        afterModel: emptyModel,
      }),
    )
    vi.stubGlobal('fetch', fetchMock)

    const plan = await requestEditPlan('mob-1', 'Haz las manos más grandes')

    const [url, init] = fetchMock.mock.calls[0]!
    expect(String(url)).toContain('/api/mobs/mob-1/ai/edit-geometry')
    expect(init?.method).toBe('POST')
    expect(JSON.parse(init?.body as string)).toEqual({ instruction: 'Haz las manos más grandes' })
    expect(plan.jobId).toBe('job-1')
    expect(plan.changedElements).toHaveLength(1)
  })

  it('requestEditPlan sobre un mob sin ninguna revisión base lanza un ApiError con NO_BASE_REVISION', async () => {
    vi.stubGlobal('fetch', vi.fn<typeof fetch>(async () => jsonResponse({ error: 'NO_BASE_REVISION', message: 'Sin revisión base.' }, 400)))

    await expect(requestEditPlan('mob-1', 'instrucción')).rejects.toBeInstanceOf(ApiError)
    await expect(requestEditPlan('mob-1', 'instrucción')).rejects.toMatchObject({ status: 400, code: 'NO_BASE_REVISION' })
  })

  it('applyEdit hace POST a /api/jobs/{jobId}/apply-edit y devuelve revisionNumber/draftVersion', async () => {
    const fetchMock = vi.fn<typeof fetch>(async () => jsonResponse({ revisionNumber: 2, draftVersion: 2 }, 201))
    vi.stubGlobal('fetch', fetchMock)

    const result = await applyEdit('job-1')

    const [url, init] = fetchMock.mock.calls[0]!
    expect(String(url)).toContain('/api/jobs/job-1/apply-edit')
    expect(init?.method).toBe('POST')
    expect(result).toEqual({ revisionNumber: 2, draftVersion: 2 })
  })

  it('applyEdit con un draft/revisión base ya vencidos lanza un ApiError con STALE_EDIT_BASE', async () => {
    vi.stubGlobal('fetch', vi.fn<typeof fetch>(async () => jsonResponse({ error: 'STALE_EDIT_BASE', message: 'El draft avanzó.' }, 409)))

    await expect(applyEdit('job-1')).rejects.toBeInstanceOf(ApiError)
    await expect(applyEdit('job-1')).rejects.toMatchObject({ status: 409, code: 'STALE_EDIT_BASE' })
  })
})
