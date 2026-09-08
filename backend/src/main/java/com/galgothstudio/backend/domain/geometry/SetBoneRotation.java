package com.galgothstudio.backend.domain.geometry;

import com.galgothstudio.backend.domain.model.Vec3;

/**
 * Reemplaza (absoluto -- de ahí "set", a diferencia de {@code rotateCuboid}
 * que suma un delta) la rotación de un bone.
 *
 * @param target id real, o {@code tempId} de un {@code createBone} anterior en el mismo batch.
 * @param rotation nueva rotación en grados -- ver CoordinateSystemContract.
 */
public record SetBoneRotation(String target, Vec3 rotation) implements GeometryOperation {
}
