package com.galgothstudio.backend.aiorchestrator.provider;

/** Pedido a un {@link StructuredReasoningProvider}: sin imagen (a diferencia de {@link VisionAnalysisRequest}) -- solo texto, ej. la instrucción de edición de HU-14 ("Haz las manos más grandes..."). */
public record ReasoningRequest(String systemPrompt, String userPrompt, String promptVersion, String schemaVersion) {
}
