package com.galgothstudio.backend.aiorchestrator.texture;

import com.galgothstudio.backend.aiorchestrator.provider.AiProviderResponse;
import com.galgothstudio.backend.domain.model.TexturePlan;

/**
 * `providerResponse` se conserva junto al `TexturePlan` ya parseado --
 * un futuro orquestador de textura (ticket 054) lo necesita para
 * registrar proveedor/modelo/versiones reales en `ai_jobs` (mismo
 * criterio que {@code ModelIntentAnalysisResult}, 028).
 */
public record TexturePlanAnalysisResult(TexturePlan texturePlan, AiProviderResponse providerResponse) {
}
