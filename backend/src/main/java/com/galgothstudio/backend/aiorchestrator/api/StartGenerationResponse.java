package com.galgothstudio.backend.aiorchestrator.api;

import java.util.UUID;

/** Respuesta de `POST /api/mobs/{mobId}/generate` (ticket 029) -- el `jobId` que el cliente usa de inmediato para abrir `GET /api/jobs/{jobId}/events`. */
public record StartGenerationResponse(UUID jobId) {
}
