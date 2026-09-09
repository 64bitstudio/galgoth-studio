package com.galgothstudio.backend.aiorchestrator;

import com.galgothstudio.backend.domain.model.MobProjectModel;
import java.util.UUID;

/** `model` es la propuesta completa -- lo mismo que queda en `ai_jobs.proposal_jsonb`. Ver HU-12: todavía NO es un draft ni una revisión persistida del mob; eso solo ocurre si el usuario confirma "Usar este modelo" (ticket 030). */
public record GenerationResult(UUID jobId, MobProjectModel model) {
}
