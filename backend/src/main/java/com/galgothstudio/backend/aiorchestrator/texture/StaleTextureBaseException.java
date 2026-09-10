package com.galgothstudio.backend.aiorchestrator.texture;

import java.util.UUID;

/**
 * El draft/revisión compartido avanzó (geometría O textura -- ambas
 * viven en el mismo {@code MobProjectModel}/misma fila de
 * {@code mob_drafts}) desde que se generó esta propuesta de textura --
 * ticket 054, Diseño técnico §16 (reutiliza el mismo mecanismo de
 * conflicto ya usado por 031 vía {@code base_revision_number}/
 * {@code base_draft_version}, mismo criterio de 409 pero como excepción
 * propia -- no se reutiliza {@code StaleEditBaseException} porque su
 * mensaje/nombre están específicamente redactados en términos de
 * "edición de geometría" (031); un mensaje de auditoría honesto para
 * textura merece su propio texto, mismo código HTTP/wire de error
 * (`STALE_TEXTURE_BASE`) para que el frontend lo distinga con claridad
 * de `STALE_EDIT_BASE`).
 */
public class StaleTextureBaseException extends RuntimeException {

	public StaleTextureBaseException(UUID jobId) {
		super("La propuesta de textura del job '" + jobId
				+ "' ya no aplica -- el draft/revisión compartido (geometría o textura) avanzó desde que se generó. "
				+ "Regenerá la propuesta contra el estado actual.");
	}

}
