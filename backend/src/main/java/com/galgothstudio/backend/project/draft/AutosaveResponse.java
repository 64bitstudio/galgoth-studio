package com.galgothstudio.backend.project.draft;

import java.time.Instant;

/**
 * Respuesta de `PATCH /api/mobs/{mobId}/draft`. `changed=false` significa
 * que el contenido era idéntico al último persistido -- no se escribió
 * NADA (ni siquiera `updated_at`), AC #2 del ticket 020.
 */
public record AutosaveResponse(boolean changed, int draftVersion, Instant updatedAt) {
}
