package com.galgothstudio.backend.project.draft;

import com.galgothstudio.backend.domain.model.MobProjectModel;

/** Body de `PATCH /api/mobs/{mobId}/draft`. */
public record AutosaveRequest(MobProjectModel model) {
}
