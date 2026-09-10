package com.galgothstudio.backend.aiorchestrator.texture;

import com.galgothstudio.backend.aiorchestrator.provider.AiProviderResponse;
import java.util.List;

/**
 * El JSON crudo devuelto por el `VisionModelProvider` no valida contra
 * `contracts/schemas/texture-plan.schema.json` -- ticket 052, AC #2: el
 * flujo se detiene acá, nunca se genera ninguna imagen a partir de un
 * `TexturePlan` inválido (mismo criterio que {@code InvalidModelIntentException},
 * 028). Carga la {@link AiProviderResponse} completa (no solo el
 * mensaje) para que un futuro orquestador de textura (ticket 054) pueda
 * registrar proveedor/modelo reales en `ai_jobs` incluso en el camino de
 * fallo -- mismo motivo que {@code InvalidModelIntentException}.
 *
 * <p>Deliberadamente NO extiende {@code GenerationValidationException}
 * (esa jerarquía es propia del catch unificado de
 * {@code MobGenerationService}, el orquestador de GEOMETRÍA ya
 * existente, 028/029) -- el orquestador de generación de textura (054)
 * todavía no existe; unificar la jerarquía de excepciones del pipeline
 * de textura es una decisión de ESE ticket, no de este.
 */
public class InvalidTexturePlanException extends RuntimeException {

	private final transient AiProviderResponse providerResponse;
	private final transient List<String> validationErrors;

	public InvalidTexturePlanException(AiProviderResponse providerResponse, List<String> validationErrors) {
		super("El VisionModelProvider devolvió un TexturePlan inválido: " + validationErrors);
		this.providerResponse = providerResponse;
		this.validationErrors = validationErrors;
	}

	public InvalidTexturePlanException(String message, AiProviderResponse providerResponse, Throwable cause) {
		super(message, cause);
		this.providerResponse = providerResponse;
		this.validationErrors = List.of();
	}

	public AiProviderResponse providerResponse() {
		return providerResponse;
	}

	public List<String> validationErrors() {
		return validationErrors;
	}

}
