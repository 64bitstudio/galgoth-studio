package com.galgothstudio.backend.aiorchestrator.edit;

import java.util.UUID;

/**
 * El mob todavía no tiene ninguna revisión guardada (`mobs.current_revision_number=0`)
 * -- la edición por IA (ticket 031) necesita una base real contra la
 * cual registrar `ai_jobs.base_revision_number` (`job_type='edit'`
 * exige ambos `base_*` NOT NULL, y la FK compuesta `fk_ai_jobs_base_revision`
 * -- ticket 003 -- exige que ese valor apunte a una fila real de
 * `mob_revisions`; `revision_number=0` nunca existe, las revisiones
 * arrancan en 1). "Usar este modelo" (030) o "Guardar" (020) deben
 * correr primero.
 */
public class NoBaseRevisionException extends RuntimeException {

	public NoBaseRevisionException(UUID mobId) {
		super("El mob '" + mobId
				+ "' todavía no tiene ninguna revisión guardada -- la edición por IA necesita una base real (\"Usar este modelo\" o \"Guardar\" primero).");
	}

}
