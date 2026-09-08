package com.galgothstudio.backend.domain.model;

/**
 * [a, b, c, d] -- usado para rangos UV [u0, v0, u1, v1] (ver {@link Face}
 * y {@link UvRegion}). Mismo motivo que {@link Vec3}: un record con un
 * campo {@code double[]} hereda el equals/hashCode/toString por defecto
 * de Object para ese campo (comparación por referencia, no por
 * contenido) -- hallazgo real de SonarQube (java:S2384) al implementar
 * el ticket 004, no un problema teórico.
 */
public record Vec4(double a, double b, double c, double d) {

	public static Vec4 fromArray(double[] arr) {
		if (arr.length != 4) {
			throw new IllegalArgumentException("Vec4 requiere exactamente 4 componentes, recibió " + arr.length);
		}
		return new Vec4(arr[0], arr[1], arr[2], arr[3]);
	}

	public double[] toArray() {
		return new double[] { a, b, c, d };
	}

}
