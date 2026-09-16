package com.galgothstudio.backend.domain.model;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Reporte de calidad de UNA generación -- ticket 104 (HU-5b, Decisión 5
 * de `docs/definiciones/anatomia-por-capas-generacion-mobs.md`).
 *
 * <p><b>Puramente diagnóstico y determinista</b>: se calcula sobre el
 * {@link MobProjectModel} YA generado + el {@link ModelIntent} que lo
 * originó + el resultado de la validación FMM ya existente. No llama a
 * ninguna IA, no bloquea el job, no se expone en UI en v1 (confirmado en
 * el documento de definición) -- hoy solo se loguea, igual que las
 * advertencias de {@code SecondaryGeometryConstraints}.
 *
 * <p><b>Nunca inventa una métrica</b> (AC explícito): lo que no se puede
 * calcular en un caso dado se reporta como {@code unavailable} -- ver
 * {@link Metric}. Ejemplo real: sin features detectadas por vision no hay
 * denominador posible para {@code featureCoverage}; reportar 0% o 100%
 * sería igual de falso.
 *
 * @param featureCoverage      proporción de categorías detectadas en la referencia que terminaron con al menos un cuboid de esa misma categoría.
 * @param uncoveredCategories  categorías detectadas SIN ningún cuboid que las represente -- listadas explícitamente, no solo agregadas en el número (AC).
 * @param semanticCoverage     proporción de cuboids del modelo que declaran una {@code semanticPart} conocida (no {@code GENERIC}/vacía).
 * @param geometryComplexity   cantidad total de cuboids del modelo generado.
 * @param fmmCompatible        resultado de la validación FMM ya existente -- {@code unavailable} si no se corrió en ese flujo.
 */
public record ModelGenerationQualityReport(
		Metric featureCoverage,
		List<SemanticPartCategory> uncoveredCategories,
		Metric semanticCoverage,
		int geometryComplexity,
		Metric fmmCompatible) {

	/**
	 * Una métrica que puede no ser calculable. {@code available=false}
	 * significa "no se pudo medir en este caso" y {@link #value()} no debe
	 * leerse -- nunca un 0 que se confunda con "medido y dio cero".
	 */
	public record Metric(boolean available, double value) {

		public static Metric of(double value) {
			return new Metric(true, value);
		}

		public static Metric unavailable() {
			return new Metric(false, 0);
		}

		public String describe() {
			return available ? String.format("%.0f%%", value * 100) : "no disponible";
		}
	}

	/**
	 * Calcula el reporte. {@code fmmCompatible} llega ya resuelto por el
	 * caller ({@code MobGenerationService} ya corre
	 * {@code FmmCompatibilityValidator}) -- este tipo no valida FMM por su
	 * cuenta, solo consolida.
	 */
	public static ModelGenerationQualityReport of(ModelIntent intent, MobProjectModel model, Metric fmmCompatible) {
		Set<SemanticPartCategory> detected = detectedCategories(intent);
		Set<SemanticPartCategory> present = presentCategories(model);

		List<SemanticPartCategory> uncovered = new ArrayList<>();
		for (SemanticPartCategory category : detected) {
			if (!present.contains(category)) {
				uncovered.add(category);
			}
		}

		Metric coverage = detected.isEmpty()
				// Sin categorías detectadas no hay denominador: cualquier número
				// sería inventado (ni 0% ni 100% describen "no se midió").
				? Metric.unavailable()
				: Metric.of((detected.size() - uncovered.size()) / (double) detected.size());

		return new ModelGenerationQualityReport(
				coverage, List.copyOf(uncovered), semanticCoverageOf(model), model.cuboids().size(), fmmCompatible);
	}

	/** Comparación por igualdad de enum, nunca por texto libre (mandato del PO, HU-5b). */
	private static Set<SemanticPartCategory> detectedCategories(ModelIntent intent) {
		Set<SemanticPartCategory> detected = EnumSet.noneOf(SemanticPartCategory.class);
		if (intent != null) {
			detected.addAll(intent.categoriesOrDerived());
		}
		return detected;
	}

	private static Set<SemanticPartCategory> presentCategories(MobProjectModel model) {
		Set<SemanticPartCategory> present = EnumSet.noneOf(SemanticPartCategory.class);
		for (Cuboid cuboid : model.cuboids()) {
			if (cuboid.semanticPart() != null && !cuboid.semanticPart().isBlank()) {
				present.add(SemanticPartCategory.fromRawValue(cuboid.semanticPart()));
			}
		}
		return present;
	}

	/** Sin cuboids no hay denominador -- {@code unavailable}, nunca 0%. */
	private static Metric semanticCoverageOf(MobProjectModel model) {
		if (model.cuboids().isEmpty()) {
			return Metric.unavailable();
		}
		long withKnownPart = model.cuboids()
				.stream()
				.filter(cuboid -> cuboid.semanticPart() != null && !cuboid.semanticPart().isBlank())
				.filter(cuboid -> SemanticPartCategory.fromRawValue(cuboid.semanticPart()) != SemanticPartCategory.GENERIC)
				.count();
		return Metric.of(withKnownPart / (double) model.cuboids().size());
	}

	/** Una línea legible para el log de diagnóstico -- el único consumidor del reporte en v1. */
	public String describe() {
		return "featureCoverage=" + featureCoverage.describe() + ", sinCobertura=" + uncoveredCategories + ", semanticCoverage="
				+ semanticCoverage.describe() + ", cuboids=" + geometryComplexity + ", fmmCompatible=" + fmmCompatible.describe();
	}

}
