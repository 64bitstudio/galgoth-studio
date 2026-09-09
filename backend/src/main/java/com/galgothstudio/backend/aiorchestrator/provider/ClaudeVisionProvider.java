package com.galgothstudio.backend.aiorchestrator.provider;

/**
 * Proveedor de visión default/activo (master prompt §20, ticket 025) --
 * delega la mecánica HTTP a {@link ClaudeMessagesClient} (compartido con
 * {@link ClaudeReasoningProvider}). Clase separada de
 * `ClaudeReasoningProvider` a propósito -- ver el hallazgo real
 * documentado en `AiProviderConfig`: una sola clase implementando
 * `VisionModelProvider` Y `StructuredReasoningProvider` a la vez rompe
 * la selección por `@ConditionalOnProperty` (Spring encuentra la misma
 * instancia como candidata de ambas interfaces sin importar bajo cuál
 * bean se expuso).
 */
public class ClaudeVisionProvider implements VisionModelProvider {

	private static final String PROVIDER_NAME = "claude";

	private final ClaudeMessagesClient client;

	public ClaudeVisionProvider(ClaudeMessagesClient client) {
		this.client = client;
	}

	@Override
	public AiProviderResponse analyzeReferenceImage(VisionAnalysisRequest request) {
		String rawContent = client.callWithImage(request.systemPrompt(), request.imageBytes(), request.contentType(), request.userPrompt());
		return new AiProviderResponse(rawContent, PROVIDER_NAME, client.model(), request.promptVersion(), request.schemaVersion());
	}

}
