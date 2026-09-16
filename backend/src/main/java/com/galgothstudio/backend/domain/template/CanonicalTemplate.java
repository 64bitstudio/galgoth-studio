package com.galgothstudio.backend.domain.template;

import com.galgothstudio.backend.domain.model.BaseType;
import java.util.List;
import java.util.Map;

/**
 * Esqueleto anatómico determinista para un {@link BaseType} -- ticket 097,
 * HU-1. La anatomía primaria de un mob generado ya NO depende enteramente de
 * lo que el LLM decida inventar en cada llamada: este catálogo vive en
 * código, versionado, y es lo que {@code PrimaryGeometryGenerator} (ticket
 * 098) materializa sin ninguna llamada de red.
 *
 * <p>Extensible por dato, no por rama de código: agregar un template nuevo
 * es agregar una entrada a {@link CanonicalTemplateCatalog}, nunca tocar
 * {@code GeometryEngine} ni {@link ProportionEstimator}.
 *
 * @param baseType tipo de entidad al que aplica este template.
 * @param bones jerarquía de bones en rest-pose (multiplicador de proporción
 *              1.0 implícito) -- coordenadas absolutas, ver ADR 0001.
 * @param cuboids cuboides de anatomía primaria en rest-pose.
 * @param proportionRanges rango válido por cada clave de
 *                         {@link com.galgothstudio.backend.domain.model.Proportions}
 *                         que este template sabe interpretar (ej.
 *                         {@code "headScale"}) -- una clave ausente aquí no
 *                         se clampa (el template no la usa).
 * @param proportionApplier traduce proporciones ya clampadas a bones/cuboides
 *                          ajustados, específico de la anatomía de este template.
 */
public record CanonicalTemplate(
		BaseType baseType,
		List<TemplateBoneSpec> bones,
		List<TemplateCuboidSpec> cuboids,
		Map<String, ProportionRange> proportionRanges,
		ProportionApplier proportionApplier) {
}
