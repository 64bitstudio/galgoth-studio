package com.galgothstudio.backend.project.draft;

/**
 * Respuesta de `POST /api/mobs/{mobId}/revisions`. `created=false` +
 * `reason` significa que no hubo cambios desde la última revisión --
 * AC #5 del ticket 020 (no se genera una revisión duplicada).
 */
public record SaveRevisionResponse(boolean created, int revisionNumber, String reason) {
}
