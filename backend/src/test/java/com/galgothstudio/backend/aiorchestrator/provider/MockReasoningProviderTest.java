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

	/**
	 * Ticket 033 -- sin configurar nada, una request con `promptVersion`
	 * de generación (`GeometryPlannerService.PROMPT_VERSION`, replicado
	 * literal acá) devuelve un rig de creación (createBone/createCuboid),
	 * nunca el ejemplo de edición -- ese fallaría sobre un modelo vacío.
	 */
	@Test
	void sin_configurar_nada_con_promptVersion_de_generacion_devuelve_un_rig_de_creacion() {
		MockReasoningProvider provider = new MockReasoningProvider();
		ReasoningRequest request = new ReasoningRequest("system", "user", "planner-v1", "geometry-operations-v1");

		AiProviderResponse response = provider.reason(request);

		assertThat(response.rawContent()).contains("createBone").contains("createCuboid").doesNotContain("resizeCuboid");
	}

	/**
	 * Ticket 033 -- sin configurar nada, editando un modelo REAL cuyo
	 * cuboid no se llama literal `hand_right` (el caso de cualquier
	 * modelo generado, con ids reales tipo UUID -- `GeometryEngine`
	 * siempre asigna uno nuevo al crear, nunca el `tempId` original), el
	 * default de edición apunta al id REAL de ese cuboid -- nunca al
	 * ejemplo estático de `hand_right`/`hand_left`/`shoulder_right_detail`,
	 * que fallaría (`resizeCuboid.target` no resuelto).
	 */
	@Test
	void sin_configurar_nada_editando_un_modelo_sin_hand_right_apunta_al_id_real_del_primer_cuboid() {
		MockReasoningProvider provider = new MockReasoningProvider();
		String userPrompt = "Modelo actual:\n"
				+ "{\"cuboids\":[{\"id\":\"7df30c0e-dec1-4268-9689-251c8efb7ba0\",\"name\":\"torso\",\"boneId\":\"root\"}]}"
				+ "\n\nInstrucción del usuario: prueba";
		ReasoningRequest request = new ReasoningRequest("system", userPrompt, "edit-planner-v1", "geometry-edit-v1");

		AiProviderResponse response = provider.reason(request);

		assertThat(response.rawContent()).contains("7df30c0e-dec1-4268-9689-251c8efb7ba0").doesNotContain("hand_right");
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
