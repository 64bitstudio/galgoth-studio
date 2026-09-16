package com.galgothstudio.backend.aiorchestrator.api;

import com.galgothstudio.backend.domain.model.GeometryDetail;
import com.galgothstudio.backend.domain.model.TextureDensity;

/**
 * Cuerpo (opcional) de {@code POST /api/mobs/{mobId}/generate} -- ticket
 * 100. {@code geometryDetail} nulo/ausente (incluido un request SIN body,
 * compatibilidad con el contrato anterior a este ticket) se resuelve a
 * {@link GeometryDetail#MEDIUM} en el controller.
 *
 * <p>{@code textureDensity} (ticket 109, reemplaza al
 * {@code textureResolution} del 103) sigue el mismo criterio:
 * nulo/ausente -&gt; {@link TextureDensity#MAX}, la densidad con la que los
 * rasgos chicos reciben píxeles suficientes.
 */
public record StartGenerationRequest(GeometryDetail geometryDetail, TextureDensity textureDensity) {
}
