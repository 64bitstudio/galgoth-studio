package com.galgothstudio.backend.aiorchestrator.vision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.galgothstudio.backend.aiorchestrator.provider.MockVisionProvider;
import com.galgothstudio.backend.modelvalidation.ModelIntentValidator;
import org.junit.jupiter.api.Test;

/**
 * `VisionAnalysisService` con `MockVisionProvider` (ticket 025) -- AC #2
 * del ticket 025 ("MockProvider, uso exclusivo en tests"): esta suite
 * nunca llama a la API real de Anthropic.
 */
class VisionAnalysisServiceTest {

	private VisionAnalysisService newService(MockVisionProvider mockProvider) {
		return new VisionAnalysisService(mockProvider, new ModelIntentValidator(new ObjectMapper()), new ObjectMapper());
	}

	@Test
	void una_respuesta_valida_del_proveedor_se_parsea_como_ModelIntent_AC1() {
		MockVisionProvider mockProvider = new MockVisionProvider(); // default: el ejemplo literal del master prompt §9.1
		VisionAnalysisService service = newService(mockProvider);

		ModelIntentAnalysisResult result = service.analyze(new byte[] {1, 2, 3}, "image/png", "humanoid");

		assertThat(result.modelIntent().silhouette()).isEqualTo("hunched humanoid");
		assertThat(result.modelIntent().proportions().headScale()).isEqualTo(1.08);
		assertThat(result.modelIntent().features()).contains("oversized hands");
		assertThat(result.providerResponse().provider()).isEqualTo("mock");
	}

	@Test
	void una_respuesta_invalida_del_proveedor_detiene_el_flujo_sin_generar_geometria_AC1() {
		MockVisionProvider mockProvider = new MockVisionProvider();
		mockProvider.setNextResponse("{\"silhouette\": \"incompleto\"}"); // faltan proportions/asymmetry/features/materials
		VisionAnalysisService service = newService(mockProvider);

		assertThatThrownBy(() -> service.analyze(new byte[] {1}, "image/png", "humanoid"))
				.isInstanceOf(InvalidModelIntentException.class)
				.satisfies(e -> {
					InvalidModelIntentException invalid = (InvalidModelIntentException) e;
					assertThat(invalid.validationErrors()).isNotEmpty();
					assertThat(invalid.providerResponse().provider()).isEqualTo("mock"); // se conserva incluso en el camino de fallo
				});
	}

	@Test
	void un_JSON_directamente_malformado_tambien_se_reporta_como_InvalidModelIntentException() {
		MockVisionProvider mockProvider = new MockVisionProvider();
		mockProvider.setNextResponse("esto no es JSON");
		VisionAnalysisService service = newService(mockProvider);

		assertThatThrownBy(() -> service.analyze(new byte[] {1}, "image/png", "humanoid")).isInstanceOf(InvalidModelIntentException.class);
	}

}
