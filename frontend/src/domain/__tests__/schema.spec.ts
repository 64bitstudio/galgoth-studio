import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'
import Ajv2020 from 'ajv/dist/2020.js'
import type { MobProjectModel } from '../MobProjectModel'

// contracts/ vive en la raíz del repo, no dentro de frontend/ -- ver
// docs/definiciones/galgoth-studio-mvp.md Diseño técnico §1. Vitest bajo
// entorno jsdom no da un import.meta.url de esquema file:// confiable --
// process.cwd() sí (siempre frontend/, donde se invoca `npm run test`).
const REPO_ROOT = resolve(process.cwd(), '..')

function readJson(relativePath: string): unknown {
  return JSON.parse(readFileSync(resolve(REPO_ROOT, relativePath), 'utf-8'))
}

describe('MobProjectModel JSON Schema', () => {
  const schema = readJson('contracts/schemas/mob-project-model.schema.json')
  const ajv = new Ajv2020({ strict: true, allErrors: true })
  const validate = ajv.compile(schema as object)

  it('valida contracts/fixtures/model-spec-example.json sin errores', () => {
    const example = readJson('contracts/fixtures/model-spec-example.json')
    const valid = validate(example)
    expect(validate.errors ?? []).toEqual([])
    expect(valid).toBe(true)
  })

  it('rechaza un documento al que le falta un campo requerido (texture)', () => {
    const withoutTexture = readJson('contracts/fixtures/model-spec-example.json') as Record<
      string,
      unknown
    >
    delete withoutTexture.texture
    expect(validate(withoutTexture)).toBe(false)
  })

  it('el tipo TS MobProjectModel acepta el fixture sin pérdida de datos (round-trip)', () => {
    const example = readJson('contracts/fixtures/model-spec-example.json')
    const model = example as MobProjectModel
    const roundTripped = JSON.parse(JSON.stringify(model))
    expect(roundTripped).toEqual(example)
  })

  it('texture, uv, animations y exportSettings existen en el esquema (AC #5)', () => {
    const props = (schema as { properties: Record<string, unknown> }).properties
    expect(Object.keys(props)).toEqual(
      expect.arrayContaining(['texture', 'uv', 'animations', 'exportSettings', 'referenceImages']),
    )
  })

  it('valida contracts/fixtures/carcomido-mob-project-model.json sin errores (ticket 008, dev harness)', () => {
    const carcomido = readJson('contracts/fixtures/carcomido-mob-project-model.json')
    const valid = validate(carcomido)
    expect(validate.errors ?? []).toEqual([])
    expect(valid).toBe(true)
  })

  describe('UvRegion.status / UvReservation (ticket 040)', () => {
    it('uvRegion.status y uvLayout.reservations existen en el esquema (AC #5)', () => {
      const defs = (schema as { $defs: Record<string, { properties?: Record<string, unknown> }> }).$defs
      expect(Object.keys(defs.uvRegion!.properties!)).toEqual(expect.arrayContaining(['status']))
      expect(Object.keys(defs.uvLayout!.properties!)).toEqual(expect.arrayContaining(['reservations']))
      expect(defs.uvReservation).toBeDefined()
      expect(defs.uvRegionStatus).toBeDefined()
      expect(defs.uvReservationReason).toBeDefined()
    })

    it('rechaza una uvRegion a la que le falta el campo requerido status', () => {
      const withoutStatus = readJson('contracts/fixtures/model-spec-example.json') as {
        uv: { regions: Array<Record<string, unknown>> }
      }
      delete withoutStatus.uv.regions[0]!.status
      expect(validate(withoutStatus)).toBe(false)
    })

    it('rechaza un uvLayout al que le falta el campo requerido reservations', () => {
      const withoutReservations = readJson('contracts/fixtures/model-spec-example.json') as {
        uv: Record<string, unknown>
      }
      delete withoutReservations.uv.reservations
      expect(validate(withoutReservations)).toBe(false)
    })

    it('acepta una uvReservation completa con reason=resize_abandoned', () => {
      const withReservation = readJson('contracts/fixtures/model-spec-example.json') as {
        uv: { reservations: unknown[] }
      }
      withReservation.uv.reservations = [
        {
          id: 'reservation-1',
          rect: [0, 0, 8, 8],
          reason: 'resize_abandoned',
          sourceCuboidId: 'head_main',
          sourceFace: 'north',
        },
      ]
      const valid = validate(withReservation)
      expect(validate.errors ?? []).toEqual([])
      expect(valid).toBe(true)
    })
  })
})
