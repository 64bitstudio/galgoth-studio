package com.galgothstudio.backend.domain.model;

import java.util.List;

/**
 * DTO de dominio -- contrato compartido con frontend/src/domain/MobProjectModel.ts
 * y contracts/schemas/mob-project-model.schema.json (fuente de verdad
 * formal). Ticket 004.
 *
 * {@code texture}, {@code uv}, {@code animations}, {@code exportSettings}
 * existen en el tipo sin UI/lógica funcional este ciclo (Fase 3/4) -- ver
 * docs/definiciones/galgoth-studio-mvp.md.
 *
 * @param units fijo "minecraft_pixels" -- ver docs/adr/0001-coordinate-system-contract.md.
 */
public record MobProjectModel(
		String mobId,
		String projectId,
		String name,
		BaseType baseType,
		String units,
		List<Bone> bones,
		List<Cuboid> cuboids,
		TextureDocument texture,
		UvLayout uv,
		List<AnimationSpec> animations,
		ExportSettings exportSettings,
		List<ReferenceImage> referenceImages) {

	public static final String UNITS_MINECRAFT_PIXELS = "minecraft_pixels";

}
