/**
 * Cliente HTTP de `POST /api/mobs/{mobId}/geometry/apply` (ticket 043,
 * Diseño técnico §2/§15 de `docs/definiciones/galgoth-studio-fase3-textura.md`)
 * -- la vía manual real que cierra el Hallazgo B para Resize/Add/Remove de
 * cuboid: el backend recalcula geometría/UV server-side (`GeometryEngine`/
 * `UvLayoutSelector`) en vez de confiar en lo que ya calculó el cliente.
 *
 * Whitelist cerrada: solo `createCuboid`/`resizeCuboid`/`removeCuboid` --
 * `moveCuboid`/`rotateCuboid`/pivot nunca se envían a este cliente (siguen
 * 100% client-side vía `geometryOperations.ts`, sin cambios).
 */
import { API_BASE_URL } from '../api/apiConfig'
import { ApiError } from '../api/ApiError'
import type { FaceName, MobProjectModel, Vec3 } from '../domain/MobProjectModel'

export interface ResizeCuboidOperation {
  op: 'resizeCuboid'
  target: string
  scale: Vec3
}

export interface CreateCuboidOperation {
  op: 'createCuboid'
  tempId: string
  name: string
  boneId: string
  from: Vec3
  to: Vec3
  origin: Vec3
  rotation: Vec3
}

export interface RemoveCuboidOperation {
  op: 'removeCuboid'
  target: string
}

export type GeometryApplyOperation = ResizeCuboidOperation | CreateCuboidOperation | RemoveCuboidOperation

export interface GeometryApplyResult {
  model: MobProjectModel
  draftVersion: number
}

/** Una cara ya `PAINTED` cuyo footprint cambiaría con el resize propuesto -- detalle de `PAINTED_REGION_RESIZE_CONFIRMATION_REQUIRED`. */
export interface AffectedFace {
  cuboidId: string
  face: FaceName
}

/** Reenviar la MISMA operación con `confirmPaintLoss: true` (botón "Confirmar" del modal) para proceder. */
export class PaintedRegionResizeConfirmationRequiredError extends ApiError {
  readonly affectedFaces: AffectedFace[]

  constructor(message: string, affectedFaces: AffectedFace[]) {
    super(message, 409, 'PAINTED_REGION_RESIZE_CONFIRMATION_REQUIRED')
    this.affectedFaces = affectedFaces
  }
}

function parseAffectedFaces(details: unknown): AffectedFace[] {
  if (!Array.isArray(details)) {
    return []
  }
  return details
    .filter((entry): entry is string => typeof entry === 'string')
    .map((entry) => {
      const [cuboidId, face] = entry.split(':')
      return { cuboidId, face: face as FaceName }
    })
}

export async function applyGeometry(
  mobId: string,
  operations: GeometryApplyOperation[],
  confirmPaintLoss = false,
): Promise<GeometryApplyResult> {
  const response = await fetch(`${API_BASE_URL}/api/mobs/${mobId}/geometry/apply`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ operations, confirmPaintLoss }),
  })

  const body = await response.json().catch(() => null)
  if (!response.ok) {
    if (body?.error === 'PAINTED_REGION_RESIZE_CONFIRMATION_REQUIRED') {
      throw new PaintedRegionResizeConfirmationRequiredError(
        body?.message ?? 'El resize afecta contenido ya pintado.',
        parseAffectedFaces(body?.details),
      )
    }
    throw new ApiError(body?.message ?? `Error HTTP ${response.status}`, response.status, body?.error)
  }
  return body as GeometryApplyResult
}
