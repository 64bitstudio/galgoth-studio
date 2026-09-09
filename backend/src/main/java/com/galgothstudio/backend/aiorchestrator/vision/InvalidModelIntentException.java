package com.galgothstudio.backend.aiorchestrator.vision;

import com.galgothstudio.backend.aiorchestrator.GenerationValidationException;
import com.galgothstudio.backend.aiorchestrator.provider.AiProviderResponse;
import java.util.List;

/**
 * El JSON crudo devuelto por el `VisionModelProvider` no valida contra
 * `contracts/schemas/model-intent.schema.json` -- ticket 028, AC #1: el
 * flujo se detiene acá, nunca se genera geometría a partir de un
 * `ModelIntent` inválido. Carga la {@link AiProviderResponse} completa
 * (no solo el mensaje) para que el caller pueda registrar proveedor/
 * modelo reales en `ai_jobs` incluso en el camino de fallo.
 */
public class InvalidModelIntentException extends GenerationValidationException {

	private final transient AiProviderResponse providerResponse;
	private final transient List<String> validationErrors;

	public InvalidModelIntentException(AiProviderResponse providerResponse, List<String> validationErrors) {
		super("El VisionModelProvider devolvió un ModelIntent inválido: " + validationErrors);
		this.providerResponse = providerResponse;
		this.validationErrors = validationErrors;
	}

	@Override
	public AiProviderResponse providerResponse() {
		return providerResponse;
	}

	public List<String> validationErrors() {
		return validationErrors;
	}

}
