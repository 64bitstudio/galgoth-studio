package com.galgothstudio.backend.domain.template;

import com.galgothstudio.backend.domain.model.BaseType;
import java.util.EnumMap;
import java.util.Map;

/**
 * Catálogo estático de {@link CanonicalTemplate} por {@link BaseType} --
 * ticket 097, HU-1. Extensible por dato: agregar un template completo nuevo
 * (arachnid/quadruped/flying, iteración 2) es agregar una entrada aquí, sin
 * tocar {@code GeometryEngine} ni {@link ProportionEstimator}.
 */
public final class CanonicalTemplateCatalog {

	private static final Map<BaseType, CanonicalTemplate> TEMPLATES = buildTemplates();

	private CanonicalTemplateCatalog() {
	}

	private static Map<BaseType, CanonicalTemplate> buildTemplates() {
		Map<BaseType, CanonicalTemplate> templates = new EnumMap<>(BaseType.class);
		templates.put(BaseType.HUMANOID, HumanoidCanonicalTemplate.build());
		return templates;
	}

	/**
	 * @return el template completo para {@code baseType}, o un
	 *         {@link MinimalGenericTemplate} (root+body, sin ajuste de
	 *         proporciones) si todavía no hay uno completo catalogado.
	 *         {@code MinimalGenericTemplate.build} se reconstruye en cada
	 *         llamada (inmutable, barato) en vez de cachearse -- así
	 *         {@link #TEMPLATES} queda de solo lectura tras el arranque,
	 *         sin mutación concurrente entre requests simultáneos.
	 */
	public static CanonicalTemplate forBaseType(BaseType baseType) {
		CanonicalTemplate complete = TEMPLATES.get(baseType);
		return complete != null ? complete : MinimalGenericTemplate.build(baseType);
	}

	/** @return true si {@code baseType} tiene un template completo (no el mínimo genérico). */
	public static boolean hasCompleteTemplate(BaseType baseType) {
		return TEMPLATES.containsKey(baseType);
	}
}
