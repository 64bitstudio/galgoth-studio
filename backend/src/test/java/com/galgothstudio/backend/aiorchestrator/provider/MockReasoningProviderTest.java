package com.galgothstudio.backend.aiorchestrator.provider;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** AC #2 del ticket 025 -- ver `MockVisionProviderTest` para el criterio completo. */
class MockReasoningProviderTest {

	@Test
	void sin_configurar_nada_devuelve_el_ejemplo_por_defecto_de_operaciones_de_edicion_del_master_prompt() {
		MockReasoningProvider provider = new MockReasoningProvider();
		ReasoningRequest request = new ReasoningRequest("system", "user", "v1", "schema-v1");

		AiProviderResponse response = provider.reason(request);

		assertThat(response.provider()).isEqualTo("mock");
		assertThat(response.rawContent()).contains("resizeCuboid");
	}

	@Test
	void setNextResponse_configura_la_proxima_respuesta() {
		MockReasoningProvider provider = new MockReasoningProvider();
		provider.setNextResponse("{\"summary\":\"custom-test-value\"}");
		ReasoningRequest request = new ReasoningRequest("s", "u", "v1", "s1");

		AiProviderResponse response = provider.reason(request);

		assertThat(response.rawContent()).isEqualTo("{\"summary\":\"custom-test-value\"}");
	}

}
