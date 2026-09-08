package com.galgothstudio.backend.domain.geometry;

/**
 * Elimina un cuboid del modelo. Además elimina en cascada cualquier
 * {@code uv.regions[]} que lo referencie por {@code cuboidId} -- así el
 * modelo resultante nunca queda con una referencia colgante hacia un
 * cuboid que ya no existe (AC #5: "las referencias... siguen siendo
 * válidas" tras el removeCuboid).
 *
 * @param target id real, o {@code tempId} de un {@code createCuboid} anterior en el mismo batch.
 */
public record RemoveCuboid(String target) implements GeometryOperation {
}
