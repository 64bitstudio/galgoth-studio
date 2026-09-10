import { describe, expect, it } from 'vitest'
import { STAGE_ORDER, outcomeForStage, findStageIndex, stageStatusFor } from '../generationStages'

/**
 * Ticket 038 -- tests unitarios de la fuente única de verdad del mapeo
 * de etapas (extraída de `GenerationStep.vue` a `generationStages.ts`
 * para que sea testeable sin montar ningún componente). Casos exactos
 * pedidos en el reporte del bug: los 3 outcomes terminales, una etapa
 * intermedia real, y el estado visual PENDING/ACTIVE/COMPLETED de la
 * lista de etapas.
 */
describe('generationStages', () => {
  describe('outcomeForStage', () => {
    it.each([
      ['completado', 'completed'],
      ['fallido', 'failed'],
      ['cancelado', 'cancelled'],
    ])('mapea el stage terminal "%s" al outcome "%s"', (stage, expected) => {
      expect(outcomeForStage(stage)).toBe(expected)
    })

    it.each(STAGE_ORDER.map((s) => s.key))('la etapa intermedia real "%s" no es ningún outcome terminal', (stage) => {
      expect(outcomeForStage(stage)).toBeNull()
    })

    it('un stage-key desconocido tampoco es un outcome terminal (nunca revienta ante un valor inesperado del backend)', () => {
      expect(outcomeForStage('algo_que_no_existe')).toBeNull()
    })
  })

  describe('STAGE_ORDER', () => {
    it('tiene las 6 etapas reales, en el mismo orden real de emisión del backend (ticket 038)', () => {
      expect(STAGE_ORDER.map((s) => s.key)).toEqual([
        'analizando_referencia',
        'detectando_silueta',
        'creando_rig',
        'generando_cuboides',
        'preparando_resultado',
        'validando_geometria',
      ])
    })

    it('cada etapa trae un label y un hint contextual no vacíos', () => {
      for (const stage of STAGE_ORDER) {
        expect(stage.label.trim().length).toBeGreaterThan(0)
        expect(stage.hint.trim().length).toBeGreaterThan(0)
      }
    })
  })

  describe('findStageIndex', () => {
    it('encuentra el índice real de una etapa conocida', () => {
      expect(findStageIndex('creando_rig')).toBe(2)
    })

    it('devuelve -1 para un stage-key desconocido', () => {
      expect(findStageIndex('algo_que_no_existe')).toBe(-1)
    })
  })

  describe('stageStatusFor', () => {
    it('con el job corriendo, las etapas anteriores a la actual están "done"', () => {
      expect(stageStatusFor(0, 3, 'running')).toBe('done')
      expect(stageStatusFor(2, 3, 'running')).toBe('done')
    })

    it('con el job corriendo, la etapa actual está "current"', () => {
      expect(stageStatusFor(3, 3, 'running')).toBe('current')
    })

    it('con el job corriendo, las etapas futuras están "pending"', () => {
      expect(stageStatusFor(4, 3, 'running')).toBe('pending')
      expect(stageStatusFor(5, 3, 'running')).toBe('pending')
    })

    it.each(['completed', 'failed', 'cancelled'] as const)(
      'terminado el job (%s), cada etapa hasta la alcanzada queda "done" -- ninguna sigue "current"',
      (outcome) => {
        expect(stageStatusFor(0, 3, outcome)).toBe('done')
        expect(stageStatusFor(3, 3, outcome)).toBe('done')
        expect(stageStatusFor(4, 3, outcome)).toBe('pending')
      },
    )
  })
})
