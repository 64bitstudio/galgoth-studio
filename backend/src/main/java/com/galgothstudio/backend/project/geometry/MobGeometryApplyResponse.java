package com.galgothstudio.backend.project.geometry;

import com.galgothstudio.backend.domain.model.MobProjectModel;

/**
 * Respuesta 200 de {@code POST /api/mobs/{mobId}/geometry/apply} (ticket
 * 043) -- {@code model} es el {@link MobProjectModel} COMPLETO ya
 * recalculado server-side (geometría + UV), listo para que el frontend lo
 * pase tal cual a {@code draftModelStore.commitExternalModel} (ticket 040).
 * {@code draftVersion} refleja el mismo contador de {@code mob_drafts} que
 * {@code GET}/{@code PATCH /draft} -- este endpoint persiste el resultado
 * en el draft por el mismo mecanismo ({@link
 * com.galgothstudio.backend.project.draft.DraftPersistenceService#autosave}).
 */
public record MobGeometryApplyResponse(MobProjectModel model, int draftVersion) {
}
