/**
 * Fuente única de verdad del mapeo de etapas del pipeline de generación
 * de TEXTURA por IA (ticket 054 backend, `GenerationStage.java` /
 * `TextureGenerationService`; ticket 055 frontend, HU-36/HU-42, Diseño
 * técnico §13 de `docs/definiciones/galgoth-studio-fase3-textura.md`) --
 * mismo criterio que `generationStages.ts` (038, pipeline de geometría):
 * un solo lugar canónico, unit-testeable sin montar ningún componente Vue,
 * para que un cambio de stage-key en el backend no pueda divergir en
 * silencio del frontend sin que ningún test lo note.
 *
 * A diferencia de la geometría, una de las 5 etapas reales
 * (`generando_bone_<id>`) es DINÁMICA -- incluye el id real del bone que
 * se está generando en ese momento (ver Javadoc de
 * `GenerationStage.GENERANDO_BONE_PREFIX`). `canonicalStageKey` normaliza
 * cualquier `generando_bone_*` a la categoría fija `generando_bone` para
 * poder ubicarla en {@link TEXTURE_STAGE_ORDER}.
 *
 * Tampoco existe un `cancelado` para textura (a diferencia de la
 * geometría, 038) -- `TextureGenerationController` no expone ningún
 * endpoint de cancelación (ver `docs/API.md`), así que
 * {@link TextureGenerationOutcome} solo tiene 2 terminales reales.
 */

export interface TextureStageDefinition {
  key: string
  label: string
}

export const TEXTURE_STAGE_ORDER: readonly TextureStageDefinition[] = [
  { key: 'analizando_paleta', label: 'Analizando paleta…' },
  { key: 'mapeando_caras', label: 'Mapeando caras…' },
  { key: 'generando_bone', label: 'Generando textura…' },
  { key: 'componiendo_atlas', label: 'Componiendo atlas…' },
  { key: 'limpiando_pixeles', label: 'Limpiando bordes de píxeles…' },
]

const GENERANDO_BONE_PREFIX = 'generando_bone_'

/** Normaliza `generando_bone_<id>` (valor dinámico real del backend, un id de bone distinto por llamada) a la categoría fija `generando_bone` -- cualquier otro valor de `stage` pasa sin cambios. */
export function canonicalStageKey(stage: string): string {
  return stage.startsWith(GENERANDO_BONE_PREFIX) ? 'generando_bone' : stage
}

export type TextureGenerationOutcome = 'running' | 'completed' | 'failed'

/** `null` mientras `stage` no es uno de los 2 terminales reales que emite el backend (`completado`/`fallido`) -- cualquier otro valor es una etapa intermedia real, nunca un outcome. */
export function outcomeForTextureStage(stage: string): TextureGenerationOutcome | null {
  if (stage === 'completado') {
    return 'completed'
  }
  if (stage === 'fallido') {
    return 'failed'
  }
  return null
}

export function findTextureStageIndex(stageKey: string): number {
  return TEXTURE_STAGE_ORDER.findIndex((s) => s.key === canonicalStageKey(stageKey))
}

export type TextureStageStatus = 'done' | 'current' | 'pending'

/**
 * Estado visual de una etapa de la lista -- función pura, sin ningún
 * estado de componente, mismo criterio que `stageStatusFor` de geometría
 * (038): dado el índice de la etapa actual y el outcome del job, dice si
 * la etapa `index` ya se completó, es la actual, o todavía no llegó.
 */
export function textureStageStatusFor(index: number, currentStageIndex: number, outcome: TextureGenerationOutcome): TextureStageStatus {
  if (outcome !== 'running') {
    return index <= currentStageIndex ? 'done' : 'pending'
  }
  if (index < currentStageIndex) {
    return 'done'
  }
  return index === currentStageIndex ? 'current' : 'pending'
}
