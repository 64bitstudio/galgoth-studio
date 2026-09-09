package com.galgothstudio.backend.aiorchestrator.provider;

/**
 * Razonamiento estructurado sobre texto -- devuelve el JSON crudo de una
 * propuesta de `GeometryOperation[]` (master prompt §9.2/§9.3), SIN
 * VALIDAR (responsabilidad del caller, tickets 028/031). Mismo criterio
 * de selección por entorno que {@link VisionModelProvider}, vía
 * `AI_REASONING_PROVIDER`.
 */
public interface StructuredReasoningProvider {

	AiProviderResponse reason(ReasoningRequest request);

}
