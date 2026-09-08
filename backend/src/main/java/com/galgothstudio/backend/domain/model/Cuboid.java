package com.galgothstudio.backend.domain.model;

public record Cuboid(
		String id,
		String name,
		String boneId,
		Vec3 from,
		Vec3 to,
		Vec3 origin,
		Vec3 rotation,
		CuboidFaces faces) {
}
