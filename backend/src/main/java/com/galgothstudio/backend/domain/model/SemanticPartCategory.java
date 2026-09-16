package com.galgothstudio.backend.domain.model;

import java.util.Locale;

/**
 * Taxonomía semántica CERRADA de partes de un mob -- ticket 104
 * (Decisión 5b de `docs/definiciones/anatomia-por-capas-generacion-mobs.md`).
 * La comparan por igualdad tanto {@link Cuboid#semanticPart()} (qué
 * representa cada cuboid generado) como las features detectadas por
 * vision ({@link ModelIntent#featureCategories()}), para que la cobertura
 * de features (HU-5b) sea igualdad de enum y NUNCA *fuzzy matching* de
 * texto libre -- mandato explícito del Product Owner.
 *
 * <p><b>Anatomía primaria + rasgos secundarios en el mismo catálogo</b>:
 * las primeras entradas son las que ya emite
 * {@code HumanoidCanonicalTemplate} (097/098) y las siguientes cubren los
 * rasgos del benchmark Carcomido (garras, cuernos, ropa desgarrada,
 * mandíbula, grietas emisivas...) que produce
 * {@code SecondaryGeometryPlanner} (099). Un solo catálogo: comparar
 * cobertura exige que ambos lados hablen el mismo idioma.
 *
 * <p><b>Extensible de forma aditiva</b>: agregar una categoría nueva no
 * rompe a ningún consumidor (los valores existentes conservan su nombre).
 * {@link #GENERIC} es el valor de escape obligatorio -- una feature real
 * detectada por vision que no encaje en ninguna categoría se registra
 * como {@code GENERIC}, NUNCA se descarta (AC explícito del ticket).
 */
public enum SemanticPartCategory {

	// --- Anatomía primaria (CanonicalTemplate, 097/098) ---
	HEAD,
	TORSO,
	ARM,
	FOREARM,
	HAND,
	LEG,
	SHIN,
	FOOT,

	// --- Rasgos secundarios (SecondaryGeometryPlanner, 099) ---
	JAW,
	CLAW,
	HORN,
	SPIKE,
	TAIL,
	WING,
	EAR,
	EYE,
	TORN_CLOTH,
	LOINCLOTH,
	ARMOR,
	EMISSIVE_CRACK,

	/** Valor de escape -- una feature/parte real que no encaja en ninguna categoría de arriba. Nunca significa "descartada". */
	GENERIC;

	/**
	 * Categoría correspondiente a un valor libre ya persistido
	 * ({@link Cuboid#semanticPart()} es todavía un {@code String}: lo
	 * escribe el LLM de geometría secundaria, que puede devolver cualquier
	 * cosa por más que el prompt liste las categorías esperadas).
	 *
	 * <p>Normaliza mayúsculas/minúsculas y espacios/guiones ({@code "torn
	 * cloth"}, {@code "torn-cloth"} y {@code "TORN_CLOTH"} son la misma
	 * categoría). Cualquier valor desconocido -- incluido {@code null} o
	 * vacío -- cae en {@link #GENERIC}: es un dato real que existe, solo
	 * que sin categoría conocida, y perderlo falsearía la cobertura hacia
	 * arriba.
	 */
	public static SemanticPartCategory fromRawValue(String rawValue) {
		if (rawValue == null || rawValue.isBlank()) {
			return GENERIC;
		}
		String normalized = rawValue.trim().toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
		for (SemanticPartCategory category : values()) {
			if (category.name().equals(normalized)) {
				return category;
			}
		}
		return GENERIC;
	}

}
