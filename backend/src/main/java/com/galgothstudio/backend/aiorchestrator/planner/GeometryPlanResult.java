package com.galgothstudio.backend.aiorchestrator.planner;

import com.galgothstudio.backend.aiorchestrator.provider.AiProviderResponse;
import com.galgothstudio.backend.domain.geometry.GeometryOperation;
import com.galgothstudio.backend.domain.model.MobProjectModel;
import java.util.List;

/**
 * `model` es el `MobProjectModel` YA CON la geometría propuesta aplicada
 * (`GeometryEngine`, 005) y su UV recalculada (`AutoUv`, 006) -- listo
 * para guardarse tal cual en `ai_jobs.proposal_jsonb`, sin que un
 * consumidor futuro (ticket 030) tenga que re-ejecutar el motor.
 *
 * <p>`operations` (agregado en el ticket 029) es la lista cruda de
 * operaciones YA deserializadas/whitelisteadas (aún con `tempId`s sin
 * resolver) que produjo `model` -- el pipeline asíncrono de
 * `MobGenerationService` la reutiliza para reproducir el mismo batch de
 * forma incremental (progreso/preview en vivo) sin volver a llamar al
 * proveedor de IA una segunda vez.
 */
public record GeometryPlanResult(MobProjectModel model, List<GeometryOperation> operations, AiProviderResponse providerResponse) {
}
