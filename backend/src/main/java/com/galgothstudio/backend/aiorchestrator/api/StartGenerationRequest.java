package com.galgothstudio.backend.aiorchestrator.api;

import com.galgothstudio.backend.domain.model.GeometryDetail;
import com.galgothstudio.backend.domain.model.TextureResolution;

/**
 * Cuerpo (opcional) de {@code POST /api/mobs/{mobId}/generate} -- ticket
 * 100. {@code geometryDetail} nulo/ausente (incluido un request SIN body,
 * compatibilidad con el contrato anterior a este ticket) se resuelve a
 * {@link GeometryDetail#MEDIUM} en el controller.
 *
 * <p>{@code textureResolution} (ticket 103) sigue el mismo criterio:
 * nulo/ausente -&gt; {@link TextureResolution#MAX_128}, el default que ya
 * mostraba el selector de Configuración antes de estar cableado.
 */
public record StartGenerationRequest(GeometryDetail geometryDetail, TextureResolution textureResolution) {
}
