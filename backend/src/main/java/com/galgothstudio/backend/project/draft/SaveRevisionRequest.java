package com.galgothstudio.backend.project.draft;

import com.galgothstudio.backend.domain.model.MobProjectModel;

/** Body de `POST /api/mobs/{mobId}/revisions` (Guardar). */
public record SaveRevisionRequest(MobProjectModel model) {
}
