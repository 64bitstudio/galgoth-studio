package com.galgothstudio.backend.aiorchestrator.edit;

import com.galgothstudio.backend.aiorchestrator.provider.AiProviderResponse;
import com.galgothstudio.backend.domain.model.MobProjectModel;

/** Resultado de {@link AiGeometryEditPlannerService#plan} -- `afterModel` YA tiene las operaciones aplicadas (vía `GeometryEngine`, 005/006), listo para mostrarse en el Before/After sin que el caller vuelva a ejecutar el motor. */
record EditPlanResult(String summary, MobProjectModel afterModel, AiProviderResponse providerResponse) {
}
