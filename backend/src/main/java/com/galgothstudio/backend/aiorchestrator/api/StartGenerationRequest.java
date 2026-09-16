package com.galgothstudio.backend.aiorchestrator.api;

import com.galgothstudio.backend.domain.model.GeometryDetail;

/**
 * Cuerpo (opcional) de {@code POST /api/mobs/{mobId}/generate} -- ticket
 * 100. {@code geometryDetail} nulo/ausente (incluido un request SIN body,
 * compatibilidad con el contrato anterior a este ticket) se resuelve a
 * {@link GeometryDetail#MEDIUM} en el controller.
 */
public record StartGenerationRequest(GeometryDetail geometryDetail) {
}
