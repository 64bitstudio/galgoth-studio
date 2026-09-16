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
 * @param semanticPart categoría semántica de la parte (ticket 099, HU-2) --
 *                      {@code null} para callers que no la necesitan (ver
 *                      constructor de compatibilidad); requerida (no vacía)
 *                      para operaciones que pasan por
 *                      {@link SecondaryGeometryConstraints}.
 */
public record CreateCuboid(String tempId, String name, String boneId, Vec3 from, Vec3 to, Vec3 origin, Vec3 rotation, String semanticPart)
		implements GeometryOperation {

	/** Constructor de compatibilidad -- ver {@link com.galgothstudio.backend.domain.model.Cuboid#Cuboid(String, String, String, Vec3, Vec3, Vec3, Vec3, com.galgothstudio.backend.domain.model.CuboidFaces)}. */
	public CreateCuboid(String tempId, String name, String boneId, Vec3 from, Vec3 to, Vec3 origin, Vec3 rotation) {
		this(tempId, name, boneId, from, to, origin, rotation, null);
	}
}
