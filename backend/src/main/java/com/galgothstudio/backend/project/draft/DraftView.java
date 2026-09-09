package com.galgothstudio.backend.project.draft;

import com.galgothstudio.backend.domain.model.MobProjectModel;
import java.time.Instant;

/** Respuesta de `GET /api/mobs/{mobId}/draft`. */
public record DraftView(String mobId, int draftVersion, MobProjectModel model, Instant updatedAt) {
}
