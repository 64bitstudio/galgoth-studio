/**
 * CoordinateSystemContract -- implementación TS.
 * Contrato formal: docs/adr/0001-coordinate-system-contract.md
 *
 * Ningún otro módulo (viewport, Geometry Engine del frontend si lo hubiera,
 * AutoUv) reimplementa su propia conversión de ejes/grados/orden de
 * rotación -- todos importan de aquí.
 *
 * Nota deliberada: se construye la matriz de rotación explícitamente
 * (Rz · Ry · Rx, multiplicando matrices de un solo eje) en vez de usar
 * `new THREE.Euler(x, y, z, 'XYZ')` directo -- así el resultado queda
 * garantizado por ESTE contrato, no por la semántica interna de
 * THREE.Euler (que mezcla convenciones intrínseca/extrínseca según el
 * string de orden, un detalle que no queremos heredar sin verificar).
 */
import { Matrix4, Vector3 } from 'three'
import type { Vec3 } from './MobProjectModel'

export function degreesToRadians(deg: number): number {
  return (deg * Math.PI) / 180
}

export function radiansToDegrees(rad: number): number {
  return (rad * 180) / Math.PI
}

/**
 * Matriz de rotación para rotationDeg=[x,y,z] (grados), orden extrínseco
 * X -> Y -> Z, R = Rz · Ry · Rx (Rx se aplica primero al vector columna).
 */
export function rotationMatrixFromEulerXYZDeg(rotationDeg: Vec3): Matrix4 {
  const [xDeg, yDeg, zDeg] = rotationDeg
  const rx = new Matrix4().makeRotationX(degreesToRadians(xDeg))
  const ry = new Matrix4().makeRotationY(degreesToRadians(yDeg))
  const rz = new Matrix4().makeRotationZ(degreesToRadians(zDeg))
  // three.js Matrix4.multiply(m) hace this = this * m -- encadenar así
  // produce exactamente Rz * Ry * Rx.
  return rz.multiply(ry).multiply(rx)
}

/**
 * punto' = origin + R(rotationDeg) · (punto - origin)
 * Aplicación de una rotación alrededor de un pivote arbitrario -- la
 * misma fórmula para bones y para cuboides (ver ADR).
 */
export function applyPivotRotation(point: Vec3, origin: Vec3, rotationDeg: Vec3): Vec3 {
  const p = new Vector3(...point)
  const o = new Vector3(...origin)
  const relative = p.clone().sub(o)
  const rotated = relative.applyMatrix4(rotationMatrixFromEulerXYZDeg(rotationDeg))
  const result = rotated.add(o)
  return [result.x, result.y, result.z]
}

/**
 * Composición padre-hijo: aplica primero la rotación del cuboid sobre su
 * propio pivote, y sobre ESE resultado aplica la rotación del bone padre
 * sobre el pivote del bone -- nunca sobre las coordenadas originales sin
 * acumular (ver ADR, "Composición padre-hijo").
 */
export function composeBoneChildTransform(
  point: Vec3,
  cuboidOrigin: Vec3,
  cuboidRotationDeg: Vec3,
  boneOrigin: Vec3,
  boneRotationDeg: Vec3,
): Vec3 {
  const afterCuboid = applyPivotRotation(point, cuboidOrigin, cuboidRotationDeg)
  return applyPivotRotation(afterCuboid, boneOrigin, boneRotationDeg)
}
