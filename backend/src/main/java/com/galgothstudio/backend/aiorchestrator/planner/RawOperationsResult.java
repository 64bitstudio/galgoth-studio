package com.galgothstudio.backend.aiorchestrator.planner;

import com.galgothstudio.backend.aiorchestrator.provider.AiProviderResponse;
import com.galgothstudio.backend.domain.geometry.GeometryOperation;
import java.util.List;

/** Resultado de {@link GeometryPlannerService#requestOperations} (ticket 029) -- operaciones ya deserializadas/whitelisteadas, TODAVÍA sin aplicar al `GeometryEngine`. */
public record RawOperationsResult(List<GeometryOperation> operations, AiProviderResponse providerResponse) {
}
