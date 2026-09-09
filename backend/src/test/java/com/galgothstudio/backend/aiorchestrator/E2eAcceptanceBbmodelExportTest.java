package com.galgothstudio.backend.aiorchestrator;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.galgothstudio.backend.aiorchestrator.planner.GeometryPlanResult;
import com.galgothstudio.backend.aiorchestrator.planner.GeometryPlannerService;
import com.galgothstudio.backend.aiorchestrator.provider.MockReasoningProvider;
import com.galgothstudio.backend.domain.export.BBModelExporterV4;
import com.galgothstudio.backend.domain.export.BBModelExporterV5;
import com.galgothstudio.backend.domain.export.validation.FmmCompatibilityValidator;
import com.galgothstudio.backend.domain.export.validation.ValidationResult;
import com.galgothstudio.backend.domain.jackson.Vec3JacksonModule;
import com.galgothstudio.backend.domain.jackson.Vec4JacksonModule;
import com.galgothstudio.backend.domain.model.BaseType;
import com.galgothstudio.backend.domain.model.ExportSettings;
import com.galgothstudio.backend.domain.model.FormatVersion;
import com.galgothstudio.backend.domain.model.ModelIntent;
import com.galgothstudio.backend.domain.model.MobProjectModel;
import com.galgothstudio.backend.domain.model.Proportions;
import com.galgothstudio.backend.domain.model.TextureDocument;
import com.galgothstudio.backend.domain.model.UvLayout;
import com.galgothstudio.backend.domain.uv.AlphaAutoPackStrategy;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Ticket 033 (AC #2), la mitad v4: la suite Playwright (`frontend/e2e/`)
 * cubre el flujo real completo por navegador, pero exclusivamente en v5
 * -- el producto NUNCA expuso una opción de exportar en v4 (ni mockup ni
 * endpoint, ticket 032; V4 es una capacidad interna de compatibilidad
 * con FreeMinecraftModels, 014). Decisión confirmada explícitamente con
 * el PO: la parte v4 del AC se cubre acá, con un test de integración
 * backend que ejercita el MISMO pipeline de generación (mock providers,
 * 025) que la suite E2E dispara por navegador, y exporta ese resultado
 * en AMBOS formatos -- sin acoplarse al proceso/DB de la corrida
 * Playwright (frágil, cruza lenguajes), pero validando genuinamente que
 * v4 funciona sobre un modelo con la misma forma (multi-bone/multi-cuboid)
 * que el flujo real produce, no un fixture trivial de un solo cuboid.
 */
class E2eAcceptanceBbmodelExportTest {

	private static ModelIntent aModelIntent() {
		return new ModelIntent(
				"hunched humanoid", new Proportions(1.08, 1.18, 1.30, 1.10), 0.72, List.of("oversized hands"),
				List.of("desaturated grey-green skin"));
	}

	private static MobProjectModel emptyModel() {
		return new MobProjectModel(
				"e2e-mob", "e2e-project", "Carcomido", BaseType.HUMANOID, MobProjectModel.UNITS_MINECRAFT_PIXELS, List.of(), List.of(),
				new TextureDocument(128, 128, null), new UvLayout(128, 128, List.of()), List.of(), new ExportSettings(FormatVersion.V5), List.of());
	}

	private static ObjectMapper objectMapper() {
		return new ObjectMapper().registerModule(new Vec3JacksonModule()).registerModule(new Vec4JacksonModule());
	}

	/** El mismo modelo generado (mock providers, sin `setNextResponse` -- el default real de un servidor recién levantado) que produciría el paso "generar por IA" de la suite Playwright. */
	private static MobProjectModel generatedModel() {
		GeometryPlannerService planner = new GeometryPlannerService(new MockReasoningProvider(), objectMapper(), new AlphaAutoPackStrategy());
		GeometryPlanResult result = planner.plan(aModelIntent(), emptyModel());
		return result.model();
	}

	@Test
	void elModeloGeneradoPorElPipelineMockSeExportaValidaYAbrePotencialmenteEnAmbosFormatos() throws Exception {
		MobProjectModel model = generatedModel();
		assertThat(model.bones()).hasSizeGreaterThanOrEqualTo(2);
		assertThat(model.cuboids()).hasSizeGreaterThanOrEqualTo(2);

		String v5Json = BBModelExporterV5.export(model, new AlphaAutoPackStrategy());
		ValidationResult v5Validation = FmmCompatibilityValidator.validate(v5Json);
		assertThat(v5Validation.pass()).as("v5: %s", v5Validation.issues()).isTrue();
		JsonNode v5Root = objectMapper().readTree(v5Json);
		assertThat(v5Root.path("meta").path("format_version").asText()).isEqualTo("5.0");
		assertThat(v5Root.has("groups")).isTrue();

		String v4Json = BBModelExporterV4.export(model, new AlphaAutoPackStrategy());
		ValidationResult v4Validation = FmmCompatibilityValidator.validate(v4Json);
		assertThat(v4Validation.pass()).as("v4: %s", v4Validation.issues()).isTrue();
		JsonNode v4Root = objectMapper().readTree(v4Json);
		assertThat(v4Root.path("meta").path("format_version").asText()).isEqualTo("4.10");
		assertThat(v4Root.has("groups")).isFalse();

		// Misma geometría en ambos formatos -- solo cambia cómo se escribe la jerarquía.
		assertThat(v4Root.path("elements")).hasSameSizeAs(v5Root.path("elements"));
	}

}
