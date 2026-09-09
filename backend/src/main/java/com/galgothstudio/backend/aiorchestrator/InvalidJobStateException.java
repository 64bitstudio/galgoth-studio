package com.galgothstudio.backend.aiorchestrator;

import java.util.UUID;

/** `POST /api/jobs/{jobId}/cancel` (ticket 029) sobre un job que ya alcanzó un estado terminal (`completed`/`failed`/`cancelled`) -- no hay nada en curso que detener. */
public class InvalidJobStateException extends RuntimeException {

	public InvalidJobStateException(UUID jobId, String currentStatus) {
		super("El job '" + jobId + "' no se puede cancelar -- su estado actual es '" + currentStatus + "', no 'running'.");
	}

}
