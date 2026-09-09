package com.galgothstudio.backend.aiorchestrator.api;

import com.galgothstudio.backend.domain.export.validation.ValidationIssue;
import java.util.List;
import java.util.UUID;

/**
 * Resumen de un job de generación YA completado (ticket 030, HU-12, AC
 * #1) -- calculado en vivo a partir de `ai_jobs.proposal_jsonb` (nunca
 * se re-ejecuta `GeometryEngine`, ese trabajo ya está hecho desde 028).
 * `fmmCompatible`/`fmmIssues` vienen de exportar la propuesta a `.bbmodel`
 * (005/010/011) y correrle encima {@code FmmCompatibilityValidator}
 * (013) -- el estado REAL de compatibilidad, no un estimado.
 */
public record GenerationResultView(
		UUID jobId,
		UUID mobId,
		String mobName,
		int cuboidCount,
		int boneCount,
		int textureWidth,
		int textureHeight,
		boolean fmmCompatible,
		List<ValidationIssue> fmmIssues) {
}
