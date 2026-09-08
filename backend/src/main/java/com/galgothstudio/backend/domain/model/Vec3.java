package com.galgothstudio.backend.domain.model;

/**
 * [x, y, z] -- unidades y semántica en docs/adr/0001-coordinate-system-contract.md.
 * Se serializa como array JSON de 3 números (ver
 * {@link com.galgothstudio.backend.domain.jackson.Vec3JacksonModule}), no
 * como objeto {x,y,z} -- para calzar con contracts/schemas/mob-project-model.schema.json.
 */
public record Vec3(double x, double y, double z) {

	public static Vec3 of(double x, double y, double z) {
		return new Vec3(x, y, z);
	}

	public static Vec3 fromArray(double[] a) {
		if (a.length != 3) {
			throw new IllegalArgumentException("Vec3 requiere exactamente 3 componentes, recibió " + a.length);
		}
		return new Vec3(a[0], a[1], a[2]);
	}

	public double[] toArray() {
		return new double[] { x, y, z };
	}

}
