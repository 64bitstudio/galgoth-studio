package com.galgothstudio.backend.domain.geometry;

import com.galgothstudio.backend.domain.model.Vec3;

/**
 * Traslada un cuboid rígidamente: {@code delta} se suma a {@code from},
 * {@code to} Y {@code origin} por igual, así el pivote de rotación se
 * mueve junto con la forma y no queda desacoplado de la geometría. Nunca
 * cambia las dimensiones del cuboid (decisión de producto confirmada con
 * el Product Owner -- ver Hecho del ticket 005).
 *
 * @param target id real, o {@code tempId} de un {@code createCuboid} anterior en el mismo batch.
 * @param delta desplazamiento por eje (minecraft_pixels).
 */
public record MoveCuboid(String target, Vec3 delta) implements GeometryOperation {
}
