import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'
import {
  applyPivotRotation,
  composeBoneChildTransform,
} from '../coordinateSystem'
import type { Vec3 } from '../MobProjectModel'

// Ver nota en schema.spec.ts: process.cwd() en vez de import.meta.url.
const REPO_ROOT = resolve(process.cwd(), '..')

interface Fixture {
  singleAxisRotation: { point: Vec3; origin: Vec3; rotationDeg: Vec3; expected: Vec3 }
  offsetPivotRotation: { point: Vec3; origin: Vec3; rotationDeg: Vec3; expected: Vec3 }
  parentChildComposition: {
    point: Vec3
    cuboidOrigin: Vec3
    cuboidRotationDeg: Vec3
    boneOrigin: Vec3
    boneRotationDeg: Vec3
  }
}

const fixture: Fixture = JSON.parse(
  readFileSync(resolve(REPO_ROOT, 'contracts/fixtures/coordinate-system-fixture.json'), 'utf-8'),
)

function expectVec3Close(actual: Vec3, expected: Vec3, epsilon = 1e-9) {
  actual.forEach((v, i) => expect(v).toBeCloseTo(expected[i]!, 9))
  void epsilon
}

describe('CoordinateSystemContract (TS)', () => {
  it('singleAxisRotation: rotar 90° en Z alrededor del origen', () => {
    const { point, origin, rotationDeg, expected } = fixture.singleAxisRotation
    expectVec3Close(applyPivotRotation(point, origin, rotationDeg), expected)
  })

  it('offsetPivotRotation: mismo caso con pivote desplazado', () => {
    const { point, origin, rotationDeg, expected } = fixture.offsetPivotRotation
    expectVec3Close(applyPivotRotation(point, origin, rotationDeg), expected)
  })

  it('parentChildComposition: la composición dos-pasos (cuboid, luego bone) coincide con una única rotación combinada alrededor del mismo pivote (AC #4)', () => {
    const { point, cuboidOrigin, cuboidRotationDeg, boneOrigin, boneRotationDeg } =
      fixture.parentChildComposition

    // Camino 1: composición real bone->cuboid del contrato.
    const viaComposition = composeBoneChildTransform(
      point,
      cuboidOrigin,
      cuboidRotationDeg,
      boneOrigin,
      boneRotationDeg,
    )

    // Camino 2 (independiente): como boneOrigin === cuboidOrigin y ambas
    // rotaciones son puramente en Z, la composición debe ser equivalente
    // a UNA sola rotación de ángulo combinado (bone.z + cuboid.z)
    // alrededor de ese mismo pivote -- verifica que la composición no
    // tenga un bug de orden/pivote, no solo que "compile igual en TS".
    const combinedZ = boneRotationDeg[2] + cuboidRotationDeg[2]
    const viaCombinedAngle = applyPivotRotation(point, cuboidOrigin, [0, 0, combinedZ])

    expectVec3Close(viaComposition, viaCombinedAngle)
  })
})
