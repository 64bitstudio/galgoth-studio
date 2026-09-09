package com.galgothstudio.backend.aiorchestrator.planner;

import com.galgothstudio.backend.aiorchestrator.provider.AiProviderResponse;
import com.galgothstudio.backend.domain.model.MobProjectModel;

/** `model` es el `MobProjectModel` YA CON la geometría propuesta aplicada (`GeometryEngine`, 005) y su UV recalculada (`AutoUv`, 006) -- listo para guardarse tal cual en `ai_jobs.proposal_jsonb`, sin que un consumidor futuro (ticket 030) tenga que re-ejecutar el motor. */
public record GeometryPlanResult(MobProjectModel model, AiProviderResponse providerResponse) {
}
