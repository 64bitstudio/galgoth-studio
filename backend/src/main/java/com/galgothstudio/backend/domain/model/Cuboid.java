package com.galgothstudio.backend.domain.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * @param semanticPart categoría semántica de la parte que este cuboid
 *                      representa (ej. {@code "TORSO"}, {@code "CLAW"}) --
 *                      campo aditivo, ticket 099 (HU-2). {@code null} para
 *                      cuboids creados antes de este ticket o por caminos
 *                      que no lo necesitan (edición manual/IA sobre un
 *                      modelo existente) -- ver {@link #Cuboid(String, String,
 *                      String, Vec3, Vec3, Vec3, Vec3, CuboidFaces)}.
 *                      Todavía un `String` libre, no el enum cerrado
 *                      {@code SemanticPartCategory} -- eso llega en el
 *                      ticket 104. {@code @JsonInclude(NON_NULL)} -- mismo
 *                      motivo/mecanismo que {@link UvRegion#paintedBy}: sin
 *                      esto, cada cuboid legacy serializaría
 *                      {@code "semanticPart":null} explícito, rompiendo la
 *                      comparación JSON estricta de
 *                      {@code MobProjectModelRoundTripTest}/fixtures ya
 *                      congeladas contra revisiones anteriores a este ticket.
 */
public record Cuboid(
		String id,
		String name,
		String boneId,
		Vec3 from,
		Vec3 to,
		Vec3 origin,
		Vec3 rotation,
		CuboidFaces faces,
		@JsonInclude(JsonInclude.Include.NON_NULL) String semanticPart) {

	/**
	 * Constructor de compatibilidad para los ~28 call sites anteriores al
	 * ticket 099 (la mayoría tests de UV/export/geometría que no necesitan
	 * semántica) -- {@code semanticPart} queda {@code null}, cambio
	 * puramente aditivo, cero archivos existentes tocados por este campo.
	 */
	public Cuboid(String id, String name, String boneId, Vec3 from, Vec3 to, Vec3 origin, Vec3 rotation, CuboidFaces faces) {
		this(id, name, boneId, from, to, origin, rotation, faces, null);
	}
}
