package com.galgothstudio.backend.domain;

import com.galgothstudio.backend.domain.model.Vec3;

/**
 * CoordinateSystemContract -- implementación Java.
 * Contrato formal: docs/adr/0001-coordinate-system-contract.md
 *
 * Ningún otro módulo del backend (Geometry Engine, AutoUv, exportador)
 * reimplementa su propia conversión de ejes/grados/orden de rotación --
 * todos usan esta clase. Espejo exacto de
 * frontend/src/domain/coordinateSystem.ts -- mismo fixture compartido
 * (contracts/fixtures/coordinate-system-fixture.json) verifica paridad.
 *
 * Sin dependencia externa de matemática 3D a propósito: la cantidad de
 * operaciones que este contrato necesita (rotación XYZ extrínseca
 * alrededor de un pivote) es pequeña y queda totalmente auditable con una
 * matriz 3x3 escrita a mano -- no justifica sumar una librería.
 */
public final class CoordinateSystem {

	private CoordinateSystem() {
	}

	public static double degreesToRadians(double deg) {
		return deg * Math.PI / 180.0;
	}

	public static double radiansToDegrees(double rad) {
		return rad * 180.0 / Math.PI;
	}

	/** Matriz 3x3, fila-mayor: {@code m[fila][columna]}. */
	private static double[][] rotationX(double rad) {
		double c = Math.cos(rad);
		double s = Math.sin(rad);
		return new double[][] {
				{ 1, 0, 0 },
				{ 0, c, -s },
				{ 0, s, c }
		};
	}

	private static double[][] rotationY(double rad) {
		double c = Math.cos(rad);
		double s = Math.sin(rad);
		return new double[][] {
				{ c, 0, s },
				{ 0, 1, 0 },
				{ -s, 0, c }
		};
	}

	private static double[][] rotationZ(double rad) {
		double c = Math.cos(rad);
		double s = Math.sin(rad);
		return new double[][] {
				{ c, -s, 0 },
				{ s, c, 0 },
				{ 0, 0, 1 }
		};
	}

	private static double[][] multiply(double[][] a, double[][] b) {
		double[][] result = new double[3][3];
		for (int i = 0; i < 3; i++) {
			for (int j = 0; j < 3; j++) {
				double sum = 0;
				for (int k = 0; k < 3; k++) {
					sum += a[i][k] * b[k][j];
				}
				result[i][j] = sum;
			}
		}
		return result;
	}

	private static Vec3 apply(double[][] m, Vec3 v) {
		double x = m[0][0] * v.x() + m[0][1] * v.y() + m[0][2] * v.z();
		double y = m[1][0] * v.x() + m[1][1] * v.y() + m[1][2] * v.z();
		double z = m[2][0] * v.x() + m[2][1] * v.y() + m[2][2] * v.z();
		return new Vec3(x, y, z);
	}

	/**
	 * Matriz de rotación para rotationDeg=[x,y,z] (grados), orden
	 * extrínseco X -&gt; Y -&gt; Z, R = Rz · Ry · Rx (Rx se aplica primero
	 * al vector columna) -- ver ADR.
	 */
	static double[][] rotationMatrixFromEulerXYZDeg(Vec3 rotationDeg) {
		double[][] rx = rotationX(degreesToRadians(rotationDeg.x()));
		double[][] ry = rotationY(degreesToRadians(rotationDeg.y()));
		double[][] rz = rotationZ(degreesToRadians(rotationDeg.z()));
		return multiply(multiply(rz, ry), rx);
	}

	/**
	 * punto' = origin + R(rotationDeg) · (punto - origin)
	 * Aplicación de una rotación alrededor de un pivote arbitrario -- la
	 * misma fórmula para bones y para cuboides (ver ADR).
	 */
	public static Vec3 applyPivotRotation(Vec3 point, Vec3 origin, Vec3 rotationDeg) {
		Vec3 relative = new Vec3(point.x() - origin.x(), point.y() - origin.y(), point.z() - origin.z());
		Vec3 rotated = apply(rotationMatrixFromEulerXYZDeg(rotationDeg), relative);
		return new Vec3(rotated.x() + origin.x(), rotated.y() + origin.y(), rotated.z() + origin.z());
	}

	/**
	 * Composición padre-hijo: aplica primero la rotación del cuboid sobre
	 * su propio pivote, y sobre ESE resultado aplica la rotación del bone
	 * padre sobre el pivote del bone -- nunca sobre las coordenadas
	 * originales sin acumular (ver ADR, "Composición padre-hijo").
	 */
	public static Vec3 composeBoneChildTransform(
			Vec3 point,
			Vec3 cuboidOrigin,
			Vec3 cuboidRotationDeg,
			Vec3 boneOrigin,
			Vec3 boneRotationDeg) {
		Vec3 afterCuboid = applyPivotRotation(point, cuboidOrigin, cuboidRotationDeg);
		return applyPivotRotation(afterCuboid, boneOrigin, boneRotationDeg);
	}

}
