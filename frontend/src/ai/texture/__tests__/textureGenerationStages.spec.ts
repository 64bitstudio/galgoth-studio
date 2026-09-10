import { describe, expect, it } from 'vitest'
import {
  TEXTURE_STAGE_ORDER,
  canonicalStageKey,
  findTextureStageIndex,
  outcomeForTextureStage,
  textureStageStatusFor,
} from '../textureGenerationStages'

describe('textureGenerationStages', () => {
  it('define las 5 etapas reales del pipeline de textura, en orden', () => {
    expect(TEXTURE_STAGE_ORDER.map((s) => s.key)).toEqual([
      'analizando_paleta',
      'mapeando_caras',
      'generando_bone',
      'componiendo_atlas',
      'limpiando_pixeles',
    ])
  })

  it('canonicalStageKey normaliza cualquier generando_bone_<id> dinámico a la categoría fija', () => {
    expect(canonicalStageKey('generando_bone_b1')).toBe('generando_bone')
    expect(canonicalStageKey('generando_bone_head-uuid-123')).toBe('generando_bone')
  })

  it('canonicalStageKey deja pasar sin cambios cualquier otro valor', () => {
    expect(canonicalStageKey('analizando_paleta')).toBe('analizando_paleta')
    expect(canonicalStageKey('completado')).toBe('completado')
  })

  it('outcomeForTextureStage reconoce completado/fallido como terminales, y ningún cancelado (sin endpoint de cancelación para textura)', () => {
    expect(outcomeForTextureStage('completado')).toBe('completed')
    expect(outcomeForTextureStage('fallido')).toBe('failed')
    expect(outcomeForTextureStage('cancelado')).toBeNull()
    expect(outcomeForTextureStage('componiendo_atlas')).toBeNull()
  })

  it('findTextureStageIndex resuelve un stage dinámico de bone a su índice canónico', () => {
    expect(findTextureStageIndex('generando_bone_arbitrary-id')).toBe(2)
    expect(findTextureStageIndex('limpiando_pixeles')).toBe(4)
    expect(findTextureStageIndex('etapa_inexistente')).toBe(-1)
  })

  it('textureStageStatusFor: running marca done antes de la actual, current en la actual, pending después', () => {
    expect(textureStageStatusFor(0, 2, 'running')).toBe('done')
    expect(textureStageStatusFor(2, 2, 'running')).toBe('current')
    expect(textureStageStatusFor(3, 2, 'running')).toBe('pending')
  })

  it('textureStageStatusFor: terminado (completed/failed) marca done hasta la etapa actual, sin ninguna current', () => {
    expect(textureStageStatusFor(0, 3, 'completed')).toBe('done')
    expect(textureStageStatusFor(3, 3, 'completed')).toBe('done')
    expect(textureStageStatusFor(4, 3, 'completed')).toBe('pending')
    expect(textureStageStatusFor(1, 1, 'failed')).toBe('done')
  })
})
