package com.galgothstudio.backend.domain.model;

import java.util.List;

/**
 * Salida cruda del `VisionModelProvider` (ticket 025) tras analizar una
 * imagen de referencia -- master prompt §9.1, ticket 028. Contrato
 * compartido con `contracts/schemas/model-intent.schema.json` (fuente de
 * verdad formal, mismo criterio que {@link MobProjectModel}/ticket 004).
 * Nunca se persiste tal cual -- solo alimenta el Geometry planner, que
 * lo traduce a {@link com.galgothstudio.backend.domain.geometry.GeometryOperation}[].
 */
public record ModelIntent(String silhouette, Proportions proportions, double asymmetry, List<String> features, List<String> materials) {
}
