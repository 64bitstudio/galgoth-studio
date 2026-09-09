package com.galgothstudio.backend.aiorchestrator.provider;

/**
 * Doble determinista de {@link StructuredReasoningProvider} (master
 * prompt §20, ticket 025) -- SIN llamar a ningún servicio externo. Uso
 * EXCLUSIVO en tests/desarrollo (`AI_REASONING_PROVIDER=mock`, ver
 * `AiProviderConfig`). Ver `MockVisionProvider`/`AiProviderConfig` para
 * el porqué de la separación de clases.
 */
public class MockReasoningProvider implements StructuredReasoningProvider {

	private static final String PROVIDER_NAME = "mock";
	private static final String DEFAULT_MODEL = "mock-model";

	/** Ejemplo literal de propuesta de edición del master prompt §9.3. */
	private static final String DEFAULT_RESPONSE =
			"""
			{
			  "summary": "Increase both hands and add asymmetry to shoulders",
			  "operations": [
			    {"op": "resizeCuboid", "target": "hand_right", "scale": [1.2, 1.15, 1.2]},
			    {"op": "resizeCuboid", "target": "hand_left", "scale": [1.15, 1.1, 1.15]},
			    {"op": "moveCuboid", "target": "shoulder_right_detail", "delta": [-0.5, 0.25, 0]}
			  ]
			}
			""";

	private String nextResponse = DEFAULT_RESPONSE;

	public void setNextResponse(String rawJson) {
		this.nextResponse = rawJson;
	}

	@Override
	public AiProviderResponse reason(ReasoningRequest request) {
		return new AiProviderResponse(nextResponse, PROVIDER_NAME, DEFAULT_MODEL, request.promptVersion(), request.schemaVersion());
	}

}
