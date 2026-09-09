package com.galgothstudio.backend.aiorchestrator.provider;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** AC #2 del ticket 025: respuestas deterministas configurables, sin ningún servicio externo (sin `RestClient`/`HttpClient` en ningún campo -- la ausencia misma ES la prueba). */
class MockVisionProviderTest {

	@Test
	void sin_configurar_nada_devuelve_el_ejemplo_por_defecto_de_ModelIntent_del_master_prompt() {
		MockVisionProvider provider = new MockVisionProvider();
		VisionAnalysisRequest request =
				new VisionAnalysisRequest(new byte[] {1, 2, 3}, "image/png", "system", "user", "v1", "schema-v1");

		AiProviderResponse response = provider.analyzeReferenceImage(request);

		assertThat(response.provider()).isEqualTo("mock");
		assertThat(response.rawContent()).contains("hunched humanoid");
		assertThat(response.promptVersion()).isEqualTo("v1");
		assertThat(response.schemaVersion()).isEqualTo("schema-v1");
	}

	@Test
	void setNextResponse_configura_la_proxima_respuesta() {
		MockVisionProvider provider = new MockVisionProvider();
		provider.setNextResponse("{\"silhouette\":\"custom-test-value\"}");
		VisionAnalysisRequest request = new VisionAnalysisRequest(new byte[] {1}, "image/png", "s", "u", "v1", "s1");

		AiProviderResponse response = provider.analyzeReferenceImage(request);

		assertThat(response.rawContent()).isEqualTo("{\"silhouette\":\"custom-test-value\"}");
	}

}
