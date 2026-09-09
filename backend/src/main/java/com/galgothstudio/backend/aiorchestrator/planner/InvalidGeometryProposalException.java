package com.galgothstudio.backend.aiorchestrator.planner;

import com.galgothstudio.backend.aiorchestrator.GenerationValidationException;
import com.galgothstudio.backend.aiorchestrator.provider.AiProviderResponse;

/**
 * El JSON crudo devuelto por el `StructuredReasoningProvider` no
 * deserializa como `GeometryOperation[]` (operación fuera de la
 * whitelist de 005, o JSON malformado), o la geometría resultante no
 * pasa la validación del `GeometryEngine` (005) -- ticket 028, AC #2.
 * Carga la {@link AiProviderResponse} completa por el mismo motivo que
 * {@link com.galgothstudio.backend.aiorchestrator.vision.InvalidModelIntentException}.
 */
public class InvalidGeometryProposalException extends GenerationValidationException {

	private final transient AiProviderResponse providerResponse;

	public InvalidGeometryProposalException(String message, AiProviderResponse providerResponse, Throwable cause) {
		super(message, cause);
		this.providerResponse = providerResponse;
	}

	@Override
	public AiProviderResponse providerResponse() {
		return providerResponse;
	}

}
