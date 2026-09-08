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
})
