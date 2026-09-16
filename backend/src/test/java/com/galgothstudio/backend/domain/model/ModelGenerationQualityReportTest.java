package com.galgothstudio.backend.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.galgothstudio.backend.domain.model.ModelGenerationQualityReport.Metric;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * `ModelGenerationQualityReport` + `SemanticPartCategory` -- ticket 104
 * (HU-5b): la cobertura se mide por igualdad de enum, nunca comparando
 * strings libres, y lo que no se puede calcular se marca `unavailable`
 * en vez de inventar un número.
 */
class ModelGenerationQualityReportTest {

	private static Cuboid cuboidWith(String id, String semanticPart) {
		Face placeholder = new Face(null, null);
		CuboidFaces faces = new CuboidFaces(placeholder, placeholder, placeholder, placeholder, placeholder, placeholder);
		return new Cuboid(
				id, "cuboid-" + id, "bone-1", new Vec3(0, 0, 0), new Vec3(4, 4, 4), new Vec3(0, 0, 0), new Vec3(0, 0, 0), faces, semanticPart);
	}

	private static MobProjectModel modelWith(List<Cuboid> cuboids) {
		return new MobProjectModel(
				"mob-1", "project-1", "Test Mob", BaseType.HUMANOID, MobProjectModel.UNITS_MINECRAFT_PIXELS,
				List.of(new Bone("bone-1", "body", null, new Vec3(0, 0, 0), new Vec3(0, 0, 0))), cuboids,
				new TextureDocument(64, 64, null), new UvLayout(64, 64, List.of()), List.of(), new ExportSettings(FormatVersion.V5), List.of());
	}

	private static ModelIntent intentWith(List<String> features, List<SemanticPartCategory> categories) {
		return new ModelIntent(
				"hunched humanoid", new Proportions(1.0, 1.0, 1.0, 1.0), 0.1, features, List.of("piel agrietada"), categories);
	}

	@Test
	void unaCategoriaDetectadaConCuboidDeEsaCategoria_cuentaComoCubierta_porIgualdadDeEnum_AC() {
		ModelIntent intent = intentWith(List.of("garras enormes", "grietas violeta"), List.of(SemanticPartCategory.CLAW, SemanticPartCategory.EMISSIVE_CRACK));
		MobProjectModel model = modelWith(List.of(cuboidWith("c1", "CLAW"), cuboidWith("c2", "EMISSIVE_CRACK")));

		ModelGenerationQualityReport report = ModelGenerationQualityReport.of(intent, model, Metric.of(1));

		assertThat(report.featureCoverage().available()).isTrue();
		assertThat(report.featureCoverage().value()).isEqualTo(1.0);
		assertThat(report.uncoveredCategories()).isEmpty();
	}

	@Test
	void lasCategoriasSinNingunCuboid_seListanExplicitamente_noSoloComoNumero_AC() {
		ModelIntent intent = intentWith(
				List.of("garras", "cuernos", "ropa desgarrada"),
				List.of(SemanticPartCategory.CLAW, SemanticPartCategory.HORN, SemanticPartCategory.TORN_CLOTH));
		MobProjectModel model = modelWith(List.of(cuboidWith("c1", "CLAW")));

		ModelGenerationQualityReport report = ModelGenerationQualityReport.of(intent, model, Metric.of(1));

		assertThat(report.featureCoverage().value()).isEqualTo(1.0 / 3);
		assertThat(report.uncoveredCategories()).containsExactlyInAnyOrder(SemanticPartCategory.HORN, SemanticPartCategory.TORN_CLOTH);
	}

	@Test
	void sinFeaturesDetectadas_featureCoverageEsUnavailable_nuncaUnNumeroInventado_AC() {
		ModelIntent intent = intentWith(List.of(), List.of());
		MobProjectModel model = modelWith(List.of(cuboidWith("c1", "TORSO")));

		ModelGenerationQualityReport report = ModelGenerationQualityReport.of(intent, model, Metric.of(1));

		assertThat(report.featureCoverage().available()).isFalse();
		assertThat(report.featureCoverage().describe()).isEqualTo("no disponible");
	}

	@Test
	void sinCuboids_semanticCoverageEsUnavailable_nuncaCero() {
		ModelIntent intent = intentWith(List.of("garras"), List.of(SemanticPartCategory.CLAW));

		ModelGenerationQualityReport report = ModelGenerationQualityReport.of(intent, modelWith(List.of()), Metric.of(1));

		assertThat(report.semanticCoverage().available()).isFalse();
		assertThat(report.geometryComplexity()).isZero();
	}

	@Test
	void unaMetricaQueNoSePudoCalcular_seMarcaUnavailable_yNoSeLeeSuValor() {
		ModelIntent intent = intentWith(List.of("garras"), List.of(SemanticPartCategory.CLAW));

		ModelGenerationQualityReport report = ModelGenerationQualityReport.of(intent, modelWith(List.of(cuboidWith("c1", "CLAW"))), Metric.unavailable());

		assertThat(report.fmmCompatible().available()).isFalse();
		assertThat(report.describe()).contains("fmmCompatible=no disponible");
	}

	@Test
	void semanticCoverage_cuentaSoloCuboidsConCategoriaConocida_noGenericNiVacia() {
		MobProjectModel model = modelWith(
				List.of(cuboidWith("c1", "TORSO"), cuboidWith("c2", "algo-que-no-existe"), cuboidWith("c3", null), cuboidWith("c4", "CLAW")));
		ModelIntent intent = intentWith(List.of("garras"), List.of(SemanticPartCategory.CLAW));

		ModelGenerationQualityReport report = ModelGenerationQualityReport.of(intent, model, Metric.of(1));

		// 2 de 4 (TORSO y CLAW) -- el valor libre desconocido cae en GENERIC y el null no cuenta.
		assertThat(report.semanticCoverage().value()).isEqualTo(0.5);
	}

	@Test
	void unValorLibreDesconocidoDeSemanticPart_seNormalizaAGeneric_sinDescartarElCuboid_AC() {
		assertThat(SemanticPartCategory.fromRawValue("una-cosa-rarísima")).isEqualTo(SemanticPartCategory.GENERIC);
		assertThat(SemanticPartCategory.fromRawValue(null)).isEqualTo(SemanticPartCategory.GENERIC);
		assertThat(SemanticPartCategory.fromRawValue("  ")).isEqualTo(SemanticPartCategory.GENERIC);
	}

	@Test
	void fromRawValue_normalizaMayusculasEspaciosYGuiones() {
		assertThat(SemanticPartCategory.fromRawValue("torn cloth")).isEqualTo(SemanticPartCategory.TORN_CLOTH);
		assertThat(SemanticPartCategory.fromRawValue("torn-cloth")).isEqualTo(SemanticPartCategory.TORN_CLOTH);
		assertThat(SemanticPartCategory.fromRawValue(" Claw ")).isEqualTo(SemanticPartCategory.CLAW);
	}

	/** AC: si el proveedor de vision no devolvió categorías, se derivan del texto libre -- ninguna feature se pierde. */
	@Test
	void sinFeatureCategoriesDelProveedor_seDerivanDelTextoLibre_yLoQueNoEncajaEsGeneric() {
		ModelIntent sinCategorias = new ModelIntent(
				"hunched humanoid", new Proportions(1.0, 1.0, 1.0, 1.0), 0.1, List.of("claw", "algo inclasificable"), List.of("piel"));

		assertThat(sinCategorias.categoriesOrDerived())
				.containsExactly(SemanticPartCategory.CLAW, SemanticPartCategory.GENERIC);
	}

	@Test
	void siElProveedorDevuelveCategoriasDeDistintoLargoQueFeatures_seIgnoranYSeDerivan_nuncaSeDesalinean() {
		ModelIntent desalineado = intentWith(List.of("claw", "horn"), List.of(SemanticPartCategory.CLAW));

		assertThat(desalineado.categoriesOrDerived()).containsExactly(SemanticPartCategory.CLAW, SemanticPartCategory.HORN);
	}

}
