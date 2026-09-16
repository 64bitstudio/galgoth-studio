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
 * @param faceArea             distribución del área UV por cara (ticket 110) -- la métrica que hace visible, sin mirar el mob, que los rasgos chicos se quedaron sin píxeles.
 */
public record ModelGenerationQualityReport(
		Metric featureCoverage,
		List<SemanticPartCategory> uncoveredCategories,
		Metric semanticCoverage,
		int geometryComplexity,
		Metric fmmCompatible,
		FaceAreaStats faceArea) {

	/**
	 * Distribución del área UV por cara -- ticket 110
	 * (`docs/definiciones/densidad-de-texel-y-resolucion-de-textura.md`,
	 * HU-4).
	 *
	 * <p>Nace de un diagnóstico hecho a mano contra un mob real: el atlas de
	 * `Carcomido v2` medía 32×256 px para 276 caras, con 178 de ellas bajo
	 * 16 px² y mediana de 8 px² -- un ojo de 2×1.2 unidades recibía 2
	 * píxeles, así que el generador de imagen solo podía devolver ruido.
	 * Medirlo a mano funcionó una vez; la métrica existe para que una
	 * regresión así se vea sola.
	 *
	 * <p><b>Las caras degeneradas se cuentan aparte</b> (área cero, hallazgo
	 * real del ticket 064: geometría legítima con una dimensión colapsada).
	 * No participan de los percentiles -- incluirlas hundiría la mediana y
	 * haría ver como problema lo que es un caso esperado.
	 *
	 * @param minPx2         área de la cara no degenerada más chica.
	 * @param medianPx2      mediana del área entre caras no degeneradas.
	 * @param p75Px2         percentil 75.
	 * @param facesBelowMinimumLegible cuántas caras no degeneradas quedan bajo {@link #MINIMUM_LEGIBLE_PX2}.
	 * @param degenerateFaces cuántas caras tienen área cero -- informativo, nunca mezclado con lo anterior.
	 */
	public record FaceAreaStats(
			int minPx2, int medianPx2, int p75Px2, int facesBelowMinimumLegible, int degenerateFaces) {
	}

	/** Piso de área por cara por debajo del cual la IA no tiene dónde pintar nada reconocible -- 16 px² = 4×4, el objetivo fijado en el ticket 109. */
	public static final int MINIMUM_LEGIBLE_PX2 = 16;

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
		return of(intent, model, fmmCompatible, faceAreaOf(model));
	}

	/** Sobrecarga para tests que quieran fijar la distribución de área sin construir una UV completa. */
	public static ModelGenerationQualityReport of(
			ModelIntent intent, MobProjectModel model, Metric fmmCompatible, FaceAreaStats faceArea) {
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
				coverage, List.copyOf(uncovered), semanticCoverageOf(model), model.cuboids().size(), fmmCompatible, faceArea);
	}

	/**
	 * Distribución de área sobre {@code model.uv().regions()}. Sin ninguna
	 * región con área real, devuelve {@code null} -- el caller lo reporta
	 * como no disponible en vez de inventar ceros (mismo criterio que
	 * {@link Metric#unavailable()}).
	 */
	private static FaceAreaStats faceAreaOf(MobProjectModel model) {
		List<Integer> areas = new ArrayList<>();
		int degenerate = 0;
		for (UvRegion region : model.uv().regions()) {
			int width = (int) Math.round(region.rect().c() - region.rect().a());
			int height = (int) Math.round(region.rect().d() - region.rect().b());
			int area = width * height;
			if (area <= 0) {
				degenerate++;
			} else {
				areas.add(area);
			}
		}
		if (areas.isEmpty()) {
			return null;
		}
		areas.sort(Integer::compareTo);
		int belowMinimum = (int) areas.stream().filter(a -> a < MINIMUM_LEGIBLE_PX2).count();
		return new FaceAreaStats(
				areas.getFirst(), areas.get(areas.size() / 2), areas.get((int) (areas.size() * 0.75)), belowMinimum, degenerate);
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
				+ semanticCoverage.describe() + ", cuboids=" + geometryComplexity + ", fmmCompatible=" + fmmCompatible.describe()
				+ ", áreaPorCara=" + describeFaceArea();
	}

	private String describeFaceArea() {
		if (faceArea == null) {
			return "no disponible";
		}
		return "min=" + faceArea.minPx2() + "px², mediana=" + faceArea.medianPx2() + "px², p75=" + faceArea.p75Px2()
				+ "px², bajo " + MINIMUM_LEGIBLE_PX2 + "px²=" + faceArea.facesBelowMinimumLegible() + ", degeneradas="
				+ faceArea.degenerateFaces();
	}

}
