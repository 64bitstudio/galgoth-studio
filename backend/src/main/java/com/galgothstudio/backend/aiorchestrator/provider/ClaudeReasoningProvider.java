package com.galgothstudio.backend.aiorchestrator.provider;

/**
 * Proveedor de razonamiento estructurado default/activo (master prompt
 * §20, ticket 025) -- delega la mecánica HTTP a {@link ClaudeMessagesClient}
 * (compartido con {@link ClaudeVisionProvider}). Ver `ClaudeVisionProvider`
 * y `AiProviderConfig` para el porqué de la separación de clases.
 */
public class ClaudeReasoningProvider implements StructuredReasoningProvider {

	private static final String PROVIDER_NAME = "claude";

	private final ClaudeMessagesClient client;

	public ClaudeReasoningProvider(ClaudeMessagesClient client) {
		this.client = client;
	}

	@Override
	public AiProviderResponse reason(ReasoningRequest request) {
		String rawContent = client.callWithText(request.systemPrompt(), request.userPrompt());
		return new AiProviderResponse(rawContent, PROVIDER_NAME, client.model(), request.promptVersion(), request.schemaVersion());
	}

}
