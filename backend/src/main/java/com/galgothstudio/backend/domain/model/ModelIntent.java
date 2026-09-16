package com.galgothstudio.backend.domain.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Salida cruda del `VisionModelProvider` (ticket 025) tras analizar una
 * imagen de referencia -- master prompt §9.1, ticket 028. Contrato
 * compartido con `contracts/schemas/model-intent.schema.json` (fuente de
 * verdad formal, mismo criterio que {@link MobProjectModel}/ticket 004).
 * Nunca se persiste tal cual -- solo alimenta el Geometry planner, que
 * lo traduce a {@link com.galgothstudio.backend.domain.geometry.GeometryOperation}[].
 *
 * @param features         descripciones libres de cada rasgo distintivo -- SIN cambios de forma (ticket 104): son el contexto legible que sigue alimentando el prompt de geometría secundaria.
 * @param featureCategories categoría {@link SemanticPartCategory} de cada rasgo de {@code features}, EN EL MISMO ORDEN -- campo ADITIVO del ticket 104 (HU-5b). Permite medir cobertura por igualdad de enum en vez de comparar strings libres (mandato del PO). {@code null}/vacío cuando el proveedor de vision no la devolvió (respuestas anteriores a este ticket, o un proveedor que ignore esa parte del prompt) -- {@link #categoriesOrDerived()} resuelve ese caso sin perder ninguna feature.
 */
public record ModelIntent(
		String silhouette, Proportions proportions, double asymmetry, List<String> features, List<String> materials,
		@JsonInclude(JsonInclude.Include.NON_NULL) List<SemanticPartCategory> featureCategories) {

	/**
	 * Constructor de compatibilidad para los call sites anteriores al ticket
	 * 104 -- mismo patrón aditivo ya usado en {@code Cuboid}/{@code CreateCuboid}
	 * (099): {@code featureCategories} queda {@code null}, cero call sites rotos.
	 */
	public ModelIntent(String silhouette, Proportions proportions, double asymmetry, List<String> features, List<String> materials) {
		this(silhouette, proportions, asymmetry, features, materials, null);
	}

	/**
	 * Categorías de {@link #features()}, una por feature y en el mismo
	 * orden. Si el proveedor las devolvió, se usan tal cual; si no, se
	 * derivan del texto libre con {@link SemanticPartCategory#fromRawValue}
	 * como FALLBACK explícito (nunca se descarta una feature: lo que no
	 * encaja queda {@link SemanticPartCategory#GENERIC}).
	 *
	 * <p>Ojo con la diferencia real: la derivación por texto solo acierta
	 * cuando la feature ES el nombre de la categoría (ej. {@code "claw"});
	 * la categorización BUENA es la que hace el proveedor de vision, que ve
	 * la imagen. Por eso el prompt la pide explícitamente -- este fallback
	 * existe para no romper respuestas viejas, no como camino principal.
	 */
	public List<SemanticPartCategory> categoriesOrDerived() {
		if (featureCategories != null && featureCategories.size() == features.size()) {
			return featureCategories;
		}
		return features.stream().map(SemanticPartCategory::fromRawValue).toList();
	}
}
