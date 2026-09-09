package com.galgothstudio.backend.aiorchestrator.edit;

import com.galgothstudio.backend.domain.geometry.GeometryOperation;
import java.util.List;

/** Forma cruda de la respuesta del `StructuredReasoningProvider` para una edición (master prompt §9.3) -- `{"summary":"...","operations":[...]}`. Deserializado directo desde el JSON crudo del proveedor. */
record RawEditPlan(String summary, List<GeometryOperation> operations) {
}
