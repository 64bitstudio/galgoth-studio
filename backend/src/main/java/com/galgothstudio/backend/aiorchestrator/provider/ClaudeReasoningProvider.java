package com.galgothstudio.backend.aiorchestrator.provider;

import java.util.function.Consumer;

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

	/** Ticket 038 -- streaming real, único proveedor que lo implementa de verdad (ver {@link StructuredReasoningProvider#reasonStreaming} para el default de los demás). */
	@Override
	public AiProviderResponse reasonStreaming(ReasoningRequest request, Consumer<String> onTextDelta) {
		String rawContent = client.callWithTextStreaming(request.systemPrompt(), request.userPrompt(), onTextDelta);
		return new AiProviderResponse(rawContent, PROVIDER_NAME, client.model(), request.promptVersion(), request.schemaVersion());
	}

}
