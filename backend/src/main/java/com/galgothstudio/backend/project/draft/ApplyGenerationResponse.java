package com.galgothstudio.backend.project.draft;

/** Respuesta de {@link DraftPersistenceService#applyGenerationProposal} (ticket 030, "Usar este modelo") -- ambos valores YA avanzaron en la misma transacción. */
public record ApplyGenerationResponse(int revisionNumber, int draftVersion) {
}
