package com.galgothstudio.backend.aiorchestrator.edit;

/** Body de `POST /api/mobs/{mobId}/ai/edit-geometry` -- instrucción en lenguaje natural (mockup 06: "Haz las manos más grandes y los hombros más irregulares."). */
public record EditGeometryRequest(String instruction) {
}
