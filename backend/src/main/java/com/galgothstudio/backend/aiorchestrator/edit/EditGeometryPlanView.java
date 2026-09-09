package com.galgothstudio.backend.aiorchestrator.edit;

import com.galgothstudio.backend.domain.model.MobProjectModel;
import java.util.List;
import java.util.UUID;

/**
 * Respuesta de `POST /api/mobs/{mobId}/ai/edit-geometry` (ticket 031,
 * AC #1/#2) -- `beforeModel`/`afterModel` completos para el Before/After
 * del mockup 06 (dos viewports de solo lectura, mismo criterio que
 * `GenerationPreviewViewport.vue`, 029). Ni `mob_drafts` ni
 * `mob_revisions` cambiaron todavía -- eso solo ocurre en
 * `POST /api/jobs/{jobId}/apply-edit`, tras confirmación explícita.
 */
public record EditGeometryPlanView(
		UUID jobId,
		String summary,
		int beforeCuboidCount,
		int beforeBoneCount,
		int afterCuboidCount,
		int afterBoneCount,
		List<ChangedElement> changedElements,
		MobProjectModel beforeModel,
		MobProjectModel afterModel) {
}
