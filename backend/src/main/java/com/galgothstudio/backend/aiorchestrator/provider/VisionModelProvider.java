package com.galgothstudio.backend.aiorchestrator.provider;

/**
 * Analiza una imagen de referencia y devuelve el JSON crudo de un
 * `ModelIntent` (master prompt §9.1) -- SIN VALIDAR contra su JSON
 * Schema, esa responsabilidad es del caller (ticket 028). Seleccionable
 * en runtime vía la variable de entorno `AI_VISION_PROVIDER`
 * (`AiProviderConfig`) -- ningún código de `ai-orchestrator` ni del
 * dominio depende de qué implementación esté activa.
 */
public interface VisionModelProvider {

	AiProviderResponse analyzeReferenceImage(VisionAnalysisRequest request);

}
