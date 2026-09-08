package com.galgothstudio.backend.domain.geometry;

import com.galgothstudio.backend.domain.model.Vec3;

/**
 * Crea un cuboid nuevo, con caras placeholder ({@code uv=[0,0,0,0]},
 * {@code texture=null}) hasta que AutoUv (ticket 006) las asigne.
 *
 * @param tempId referencia temporal única dentro del batch -- ver {@link CreateBone#tempId()}.
 * @param name nombre del cuboid (no vacío).
 * @param boneId id real ya existente en el modelo, o el {@code tempId} de un
 *               {@code createBone} anterior en el mismo batch.
 * @param from esquina mínima del AABB local (minecraft_pixels).
 * @param to esquina máxima del AABB local -- debe ser estrictamente mayor
 *           que {@code from} en cada eje (dimensiones positivas, AC #4).
 * @param origin punto de pivote de rotación -- ver CoordinateSystemContract.
 * @param rotation rotación en grados alrededor de {@code origin}.
 */
public record CreateCuboid(String tempId, String name, String boneId, Vec3 from, Vec3 to, Vec3 origin, Vec3 rotation)
		implements GeometryOperation {
}
