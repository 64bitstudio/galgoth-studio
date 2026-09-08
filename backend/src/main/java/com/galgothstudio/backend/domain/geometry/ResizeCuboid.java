package com.galgothstudio.backend.domain.geometry;

import com.galgothstudio.backend.domain.model.Vec3;

/**
 * Escala las dimensiones de un cuboid manteniendo su centro fijo:
 * {@code newSize = (to - from) * scale} (componente a componente),
 * {@code newFrom/newTo = center ∓ newSize/2}. Cada componente de
 * {@code scale} debe ser {@code > 0} -- un componente {@code <= 0}
 * produciría una dimensión resultante {@code <= 0} y se rechaza antes de
 * aplicarse (AC #4).
 *
 * @param target id real, o {@code tempId} de un {@code createCuboid} anterior en el mismo batch.
 * @param scale factor de escala por eje.
 */
public record ResizeCuboid(String target, Vec3 scale) implements GeometryOperation {
}
