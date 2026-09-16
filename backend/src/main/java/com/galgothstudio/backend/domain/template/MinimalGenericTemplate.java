package com.galgothstudio.backend.domain.template;

import com.galgothstudio.backend.domain.model.BaseType;
import com.galgothstudio.backend.domain.model.Vec3;
import java.util.List;
import java.util.Map;

/**
 * Template mínimo (root + body) para {@link BaseType} sin catálogo completo
 * todavía -- ticket 097, alcance explícito del documento de definición
 * (arachnid/quadruped/flying quedan con esto hasta la iteración 2). No
 * ajusta proporciones (rangos vacíos): el resto de la anatomía la propone el
 * {@code SecondaryGeometryPlanner} (ticket 099) con confianza baja.
 */
final class MinimalGenericTemplate {

	private MinimalGenericTemplate() {
	}

	static CanonicalTemplate build(BaseType baseType) {
		List<TemplateBoneSpec> bones = List.of(
				new TemplateBoneSpec("body", "body", null, new Vec3(0, 12, 0), Vec3.of(0, 0, 0)));
		List<TemplateCuboidSpec> cuboids = List.of(
				new TemplateCuboidSpec("body", "body", "body", new Vec3(-4, 8, -4), new Vec3(4, 16, 4), new Vec3(0, 12, 0), Vec3.of(0, 0, 0), "TORSO"));
		return new CanonicalTemplate(baseType, bones, cuboids, Map.of(), (baseBones, baseCuboids, proportions, ranges) ->
				new ProportionAdjustmentResult(baseBones, baseCuboids, List.of()));
	}
}
