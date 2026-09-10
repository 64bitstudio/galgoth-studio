/**
 * Tipos de dominio -- contrato compartido con backend/.../domain/model/
 * (Java) y contracts/schemas/mob-project-model.schema.json (JSON Schema,
 * fuente de verdad formal). Ticket 004.
 *
 * `texture`, `uv`, `animations`, `exportSettings` existen en el tipo sin
 * UI/lógica funcional este ciclo (Fase 3/4) -- ver docs/definiciones/
 * galgoth-studio-mvp.md.
 */

/** [x, y, z] -- unidades y semántica en docs/adr/0001-coordinate-system-contract.md */
export type Vec3 = [number, number, number]

export type FaceName = 'north' | 'south' | 'east' | 'west' | 'up' | 'down'

export type BaseType = 'humanoid' | 'arachnid' | 'quadruped' | 'flying' | 'custom'

export interface Bone {
  id: string
  name: string
  parentId: string | null
  pivot: Vec3
  rotation: Vec3
}

export interface Face {
  /** [u0, v0, u1, v1] en píxeles de textura. */
  uv: [number, number, number, number]
  /** Índice del atlas de textura, o null hasta que AutoUv lo asigne (ticket 006). */
  texture: number | null
}

export type CuboidFaces = Record<FaceName, Face>

export interface Cuboid {
  id: string
  name: string
  boneId: string
  from: Vec3
  to: Vec3
  origin: Vec3
  rotation: Vec3
  faces: CuboidFaces
}

export interface TextureDocument {
  width: number
  height: number
  /** Clave del PNG en MinIO -- null hasta que exista textura real o el placeholder de export (ticket 011). */
  storageKey: string | null
}

/**
 * Ticket 040. UNPAINTED: sin arte real todavía (default de una región
 * nueva y de cualquier revisión legacy de Fase 1+2, que no tiene este
 * campo). PAINTED: al menos un píxel editado a mano o compuesto por IA --
 * flag explícito que la app actualiza en el mismo commit que pinta, nunca
 * derivado por diff de píxeles. ORPHAN: cuboidId ya no existe en
 * MobProjectModel.cuboids (cuboid eliminado) -- se conserva solo para
 * bookkeeping de espacio ocupado, su rect original nunca se mueve.
 */
export type UvRegionStatus = 'unpainted' | 'painted' | 'orphan'

/**
 * Ticket 054. Origen del contenido de una UvRegion PAINTED -- 'hand'
 * (pintado a mano) o 'ai' (compuesto por el pipeline de generación de
 * textura por IA). `undefined`/`null` (el JSON legacy completo anterior
 * a 054, y toda región pintada a mano por el editor manual -- 047 -- que
 * todavía no escribe este campo explícitamente) se trata como origen
 * desconocido/posible pintado a mano por quien lo interprete -- nunca se
 * asume 'ai' por default.
 */
export type UvPaintOrigin = 'hand' | 'ai'

export interface UvRegion {
  cuboidId: string
  face: FaceName
  rect: [number, number, number, number]
  status: UvRegionStatus
  paintedBy?: UvPaintOrigin | null
}

/** Ticket 040. Único valor este ciclo: el rect que una cara PAINTED ocupaba antes de un resize destructivo confirmado que la reubicó. */
export type UvReservationReason = 'resize_abandoned'

/**
 * Tombstone explícito de espacio de atlas abandonado que NO puede
 * describirse como UvRegion porque ya no corresponde a ningún
 * (cuboidId, face) vivo o vigente (ticket 040). sourceCuboidId/sourceFace
 * son solo trazabilidad para debug/QA, no se usan para resolver ocupación
 * (eso lo hace rect). Sin lógica de negocio todavía que cree/consuma
 * reservas -- eso es StableUvStrategy (ticket 041).
 */
export interface UvReservation {
  id: string
  rect: [number, number, number, number]
  reason: UvReservationReason
  sourceCuboidId: string
  sourceFace: FaceName
}

/**
 * Bookkeeping del atlas a nivel de mob -- lo puebla AutoUv (ticket 006/007).
 * La UV real por cara ya vive en Cuboid.faces[x].uv; esto es el índice de
 * qué región del atlas está ocupada, para que el packing no genere overlaps.
 * `reservations` (ticket 040) son tombstones explícitos de espacio
 * abandonado por un resize destructivo confirmado.
 */
export interface UvLayout {
  textureWidth: number
  textureHeight: number
  regions: UvRegion[]
  reservations: UvReservation[]
}

export type AnimationChannel = 'rotation' | 'position' | 'scale'
export type Interpolation = 'linear' | 'step' | 'catmullrom'

export interface Keyframe {
  time: number
  value: Vec3
}

export interface AnimationTrack {
  boneId: string
  channel: AnimationChannel
  interpolation: Interpolation
  keyframes: Keyframe[]
}

export interface AnimationEvent {
  time: number
  type: string
  payload: Record<string, unknown>
}

export interface AnimationStyle {
  weight?: number
  asymmetry?: number
  aggression?: number
  dragLeg?: string
}

/** Sin generador/timeline funcional este ciclo (Fase 4). Forma fiel a samples/animation_spec_example.json. */
export interface AnimationSpec {
  id: string
  name: string
  duration: number
  loop: boolean
  category: string
  style?: AnimationStyle
  tracks: AnimationTrack[]
  events: AnimationEvent[]
}

export interface ExportSettings {
  preferredFormatVersion: 'v4' | 'v5'
}

export interface ReferenceImage {
  id: string
  storageKey: string
  width: number
  height: number
  contentType: string
}

export interface MobProjectModel {
  mobId: string
  projectId: string
  name: string
  baseType: BaseType
  /** Fijo -- ver docs/adr/0001-coordinate-system-contract.md. */
  units: 'minecraft_pixels'
  bones: Bone[]
  cuboids: Cuboid[]
  texture: TextureDocument
  uv: UvLayout
  animations: AnimationSpec[]
  exportSettings: ExportSettings
  referenceImages: ReferenceImage[]
}
