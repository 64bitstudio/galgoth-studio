package com.galgothstudio.backend.domain.template;

import com.galgothstudio.backend.domain.model.Vec3;

/**
 * Especificación de un cuboid de anatomía primaria dentro de un
 * {@link CanonicalTemplate} -- ticket 097, HU-1. Mismas reglas de
 * {@code from}/{@code to}/{@code origin} que
 * {@link com.galgothstudio.backend.domain.model.Cuboid} (ADR 0001):
 * coordenadas absolutas de rest-pose, {@code from <= to} componente a
 * componente, {@code origin} elegido por semántica de articulación (no
 * necesariamente el centro del bounding box).
 *
 * @param id id lógico estable dentro del template.
 * @param name nombre legible del cuboid.
 * @param boneId {@code id} de un {@link TemplateBoneSpec} del mismo template.
 * @param from esquina mínima del AABB en rest-pose (minecraft_pixels).
 * @param to esquina máxima del AABB -- estrictamente mayor que {@code from}
 *           en cada eje.
 * @param origin punto de pivote de rotación de este cuboid.
 * @param rotation rotación base en grados alrededor de {@code origin}.
 * @param semanticPart categoría semántica fija de anatomía primaria (ver
 *                     ticket 099/104, {@code SemanticPartCategory}) -- se
 *                     referencia aquí por nombre de constante para no crear
 *                     una dependencia circular con el ticket que la define;
 *                     {@code PrimaryGeometryGenerator} (098) la resuelve.
 */
public record TemplateCuboidSpec(
		String id,
		String name,
		String boneId,
		Vec3 from,
		Vec3 to,
		Vec3 origin,
		Vec3 rotation,
		String semanticPart) {
}
