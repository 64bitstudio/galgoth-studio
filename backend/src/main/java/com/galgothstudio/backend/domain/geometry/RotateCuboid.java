package com.galgothstudio.backend.domain.geometry;

import com.galgothstudio.backend.domain.model.Vec3;

/**
 * Suma {@code rotationDeg} a la rotación actual del cuboid (delta, no
 * absoluto -- a diferencia de {@code setBoneRotation}, que sí reemplaza).
 * El resultado se interpreta con {@link com.galgothstudio.backend.domain.CoordinateSystem}
 * exactamente igual que cualquier otra rotación del modelo (AC #1).
 *
 * @param target id real, o {@code tempId} de un {@code createCuboid} anterior en el mismo batch.
 * @param rotationDeg grados a sumar por eje.
 */
public record RotateCuboid(String target, Vec3 rotationDeg) implements GeometryOperation {
}
