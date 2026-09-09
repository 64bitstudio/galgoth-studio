package com.galgothstudio.backend.aiorchestrator;

import com.galgothstudio.backend.aiorchestrator.provider.AiProviderResponse;

/**
 * Base común de los fallos de VALIDACIÓN del pipeline de generación
 * (028/029, AC #1/#2) que sí traen una {@link AiProviderResponse} real
 * -- {@code InvalidModelIntentException} (vision) e
 * {@code InvalidGeometryProposalException} (planner). Permite a
 * {@link MobGenerationService#runPipeline} manejar ambas con un único
 * `catch` (Sonar `S2147`: dos catches con el mismo cuerpo deben
 * combinarse) en vez de duplicar la misma lógica de `failJob`+emit.
 */
public abstract class GenerationValidationException extends RuntimeException {

	protected GenerationValidationException(String message) {
		super(message);
	}

	protected GenerationValidationException(String message, Throwable cause) {
		super(message, cause);
	}

	public abstract AiProviderResponse providerResponse();

}
