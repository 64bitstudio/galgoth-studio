package com.galgothstudio.backend.aiorchestrator.vision;

import com.galgothstudio.backend.aiorchestrator.provider.AiProviderResponse;
import com.galgothstudio.backend.domain.model.ModelIntent;

/** `providerResponse` se conserva junto al `ModelIntent` ya parseado -- el orquestador (`MobGenerationService`) lo necesita para registrar proveedor/modelo reales en `ai_jobs`. */
public record ModelIntentAnalysisResult(ModelIntent modelIntent, AiProviderResponse providerResponse) {
}
