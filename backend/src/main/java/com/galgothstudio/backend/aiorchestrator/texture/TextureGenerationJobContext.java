package com.galgothstudio.backend.aiorchestrator.texture;

import com.galgothstudio.backend.domain.model.MobProjectModel;
import java.util.List;
import java.util.UUID;

/**
 * Todo lo que {@code TextureGenerationService#runPipeline} necesita,
 * capturado de forma síncrona en {@code startGeneration} ANTES de
 * despachar al {@code generationExecutor} -- mismo patrón que
 * {@code GenerationJobContext} (028/029). El {@code model} es el draft
 * EXACTO contra el que se generó la propuesta (mismo objeto que
 * `base_revision_number`/`base_draft_version` describen) -- el pipeline
 * nunca vuelve a leerlo del draft real mientras corre, evitando una
 * carrera con un autosave concurrente durante los ~segundos que dura la
 * generación.
 */
record TextureGenerationJobContext(
		UUID jobId,
		UUID mobId,
		MobProjectModel model,
		TextureStyle style,
		TextureDetailLevel detailLevel,
		boolean wholeModel,
		List<String> targetBoneIds,
		UUID referenceImageId,
		String referenceStorageKey,
		String referenceContentType) {
}
