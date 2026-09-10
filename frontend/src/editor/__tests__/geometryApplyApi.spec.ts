import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../../api/ApiError'
import { applyGeometry, PaintedRegionResizeConfirmationRequiredError } from '../geometryApplyApi'
import type { MobProjectModel } from '../../domain/MobProjectModel'

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

function emptyModel(mobId: string): MobProjectModel {
  return {
    mobId,
    projectId: 'test-project',
    name: 'Test',
    baseType: 'humanoid',
    units: 'minecraft_pixels',
    bones: [],
    cuboids: [],
    texture: { width: 64, height: 64, storageKey: null },
    uv: { textureWidth: 64, textureHeight: 64, regions: [], reservations: [] },
    animations: [],
    exportSettings: { preferredFormatVersion: 'v5' },
    referenceImages: [],
  }
}

describe('geometryApplyApi', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('hace POST a /api/mobs/{mobId}/geometry/apply con las operations y confirmPaintLoss en el body', async () => {
    const fetchMock = vi.fn<typeof fetch>(async () => jsonResponse({ model: emptyModel('mob-1'), draftVersion: 2 }))
    vi.stubGlobal('fetch', fetchMock)

    const result = await applyGeometry('mob-1', [{ op: 'resizeCuboid', target: 'c1', scale: [2, 1, 1] }])

    expect(result).toEqual({ model: emptyModel('mob-1'), draftVersion: 2 })
    const [url, init] = fetchMock.mock.calls[0]!
    expect(String(url)).toContain('/api/mobs/mob-1/geometry/apply')
    expect(init?.method).toBe('POST')
    expect(JSON.parse(init?.body as string)).toEqual({
      operations: [{ op: 'resizeCuboid', target: 'c1', scale: [2, 1, 1] }],
      confirmPaintLoss: false,
    })
  })

  it('confirmPaintLoss=true viaja en el body cuando se pasa explícito', async () => {
    const fetchMock = vi.fn<typeof fetch>(async () => jsonResponse({ model: emptyModel('mob-1'), draftVersion: 3 }))
    vi.stubGlobal('fetch', fetchMock)

    await applyGeometry('mob-1', [{ op: 'resizeCuboid', target: 'c1', scale: [2, 1, 1] }], true)

    const [, init] = fetchMock.mock.calls[0]!
    expect(JSON.parse(init?.body as string)).toMatchObject({ confirmPaintLoss: true })
  })

  it('409 PAINTED_REGION_RESIZE_CONFIRMATION_REQUIRED lanza PaintedRegionResizeConfirmationRequiredError con el detalle parseado', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn<typeof fetch>(async () =>
        jsonResponse(
          {
            error: 'PAINTED_REGION_RESIZE_CONFIRMATION_REQUIRED',
            message: 'El resize cambiaría el footprint de 1 cara(s) ya pintada(s).',
            details: ['head_main:north', 'head_main:up'],
          },
          409,
        ),
      ),
    )

    const error = await applyGeometry('mob-1', [{ op: 'resizeCuboid', target: 'head_main', scale: [2, 2, 2] }]).catch((e) => e)

    expect(error).toBeInstanceOf(PaintedRegionResizeConfirmationRequiredError)
    expect(error).toBeInstanceOf(ApiError)
    expect(error.affectedFaces).toEqual([
      { cuboidId: 'head_main', face: 'north' },
      { cuboidId: 'head_main', face: 'up' },
    ])
    expect(error.status).toBe(409)
    expect(error.code).toBe('PAINTED_REGION_RESIZE_CONFIRMATION_REQUIRED')
  })

  it('400 UV_ATLAS_OVERFLOW propaga un ApiError genérico (no el de confirmación)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn<typeof fetch>(async () =>
        jsonResponse({ error: 'UV_ATLAS_OVERFLOW', message: 'No cabe en el atlas actual.', details: ['requiredWidth=256'] }, 400),
      ),
    )

    await expect(
      applyGeometry('mob-1', [{ op: 'createCuboid', tempId: 't1', name: 'x', boneId: 'b', from: [0, 0, 0], to: [1, 1, 1], origin: [0, 0, 0], rotation: [0, 0, 0] }]),
    ).rejects.toMatchObject({ status: 400, code: 'UV_ATLAS_OVERFLOW' })
  })

  it('400 UNSUPPORTED_GEOMETRY_OPERATION propaga un ApiError', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn<typeof fetch>(async () =>
        jsonResponse({ error: 'UNSUPPORTED_GEOMETRY_OPERATION', message: 'moveCuboid no permitido acá.' }, 400),
      ),
    )

    await expect(applyGeometry('mob-1', [{ op: 'removeCuboid', target: 'c1' }])).rejects.toMatchObject({
      status: 400,
      code: 'UNSUPPORTED_GEOMETRY_OPERATION',
    })
  })

  it('404 DRAFT_NOT_FOUND propaga un ApiError', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn<typeof fetch>(async () => jsonResponse({ error: 'DRAFT_NOT_FOUND', message: 'Sin draft todavía.' }, 404)),
    )

    await expect(applyGeometry('mob-1', [{ op: 'removeCuboid', target: 'c1' }])).rejects.toMatchObject({
      status: 404,
      code: 'DRAFT_NOT_FOUND',
    })
  })
})
