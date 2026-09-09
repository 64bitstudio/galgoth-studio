package com.galgothstudio.backend.aiorchestrator.edit;

import java.util.UUID;

/** El draft o la revisión base avanzaron desde que se generó esta propuesta de edición (ticket 031, AC #5) -- `POST /api/jobs/{jobId}/apply-edit` rechaza con 409, sin aplicar ninguna operación. El cliente debe regenerar la propuesta contra el estado actual. */
public class StaleEditBaseException extends RuntimeException {

	public StaleEditBaseException(UUID jobId) {
		super("La propuesta de edición del job '" + jobId
				+ "' ya no aplica -- el draft o la revisión base avanzaron desde que se generó. Regenerá la propuesta contra el estado actual.");
	}

}
