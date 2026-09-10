/**
 * Fuente única de verdad del mapeo de etapas del pipeline de generación
 * (ticket 038) -- antes vivía inline dentro de `GenerationStep.vue`
 * (`STAGE_ORDER`/`outcomeForStage`/`stageStatus`), lo que el reporte del
 * bug señaló como un riesgo real: sin un solo lugar canónico, un cambio
 * de stage-key en el backend podía divergir silenciosamente del
 * frontend sin que ningún test lo cubriera. Extraído acá para que sea
 * unit-testeable en aislamiento, sin montar ningún componente Vue.
 *
 * Los 6 stage-keys (snake_case español) y los 3 terminales son EXACTOS
 * a los que emite `GenerationStage.java`/`MobGenerationService` del
 * backend -- ver `docs/API.md` para el contrato completo del evento SSE.
 */

export interface StageDefinition {
  key: string
  label: string
  /** Submensaje contextual (ticket 038) -- copy redactada por el equipo, pendiente de aprobación final del PO (ver comentario de `GenerationStep.vue`). */
  hint: string
}

export const STAGE_ORDER: readonly StageDefinition[] = [
  {key: 'analizando_referencia', label: 'Analizando referencia…', hint: 'Esto puede tardar unos segundos…'},
  {key: 'detectando_silueta', label: 'Detectando silueta…', hint: 'Interpretando la forma general del mob…'},
  {key: 'creando_rig', label: 'Creando rig…', hint: 'Construyendo el esqueleto que va a animar el modelo…'},
  {key: 'generando_cuboides', label: 'Generando cuboides…', hint: 'La IA está construyendo el modelo en tiempo real…'},
  {key: 'preparando_resultado', label: 'Preparando resultado…', hint: 'Aplicando la textura UV final…'},
  {key: 'validando_geometria', label: 'Validando geometría…', hint: 'Verificando que el modelo sea compatible con Blockbench/FMM…'},
]

export type GenerationOutcome = 'running' | 'completed' | 'failed' | 'cancelled'

/** `null` mientras `stage` no es uno de los 3 terminales reales que emite el backend (`completado`/`fallido`/`cancelado`) -- cualquier otro valor es una etapa intermedia real, nunca un outcome. */
export function outcomeForStage(stage: string): GenerationOutcome | null {
  if (stage === 'completado') return 'completed'
  if (stage === 'fallido') return 'failed'
  if (stage === 'cancelado') return 'cancelled'
  return null
}

export function findStageIndex(stageKey: string): number {
  return STAGE_ORDER.findIndex((s) => s.key === stageKey)
}

export type StageStatus = 'done' | 'current' | 'pending'

/**
 * Estado visual de una etapa de la lista (PENDING/ACTIVE/COMPLETED del
 * AC del bugfix) -- función pura, sin ningún estado de componente: dado
 * el índice de la etapa actual y el outcome del job, dice si la etapa
 * `index` ya se completó, es la actual, o todavía no llegó. Terminado el
 * job (outcome !== 'running'), cada etapa hasta la actual queda "done"
 * (no queda ninguna "current" activa una vez terminado).
 */
export function stageStatusFor(index: number, currentStageIndex: number, outcome: GenerationOutcome): StageStatus {
  if (outcome !== 'running') {
    return index <= currentStageIndex ? 'done' : 'pending'
  }
  if (index < currentStageIndex) {
    return 'done'
  }
  return index === currentStageIndex ? 'current' : 'pending'
}
