package com.galgothstudio.backend.domain.geometry;

import com.galgothstudio.backend.domain.model.Vec3;

/**
 * Reemplaza (absoluto, no delta -- de ahí "set") el pivote de un bone.
 *
 * @param target id real, o {@code tempId} de un {@code createBone} anterior en el mismo batch.
 * @param pivot nuevo punto de pivote (minecraft_pixels).
 */
public record SetBonePivot(String target, Vec3 pivot) implements GeometryOperation {
}
