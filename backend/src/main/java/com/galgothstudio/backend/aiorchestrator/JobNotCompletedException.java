package com.galgothstudio.backend.aiorchestrator;

import java.util.UUID;

/** `GET /api/jobs/{jobId}/result` y `POST /api/jobs/{jobId}/apply` (ticket 030) solo tienen sentido sobre un job en estado `completed` -- no hay ninguna propuesta que mostrar/aceptar mientras corre, ni si falló/se canceló. */
public class JobNotCompletedException extends RuntimeException {

	public JobNotCompletedException(UUID jobId, String currentStatus) {
		super("El job '" + jobId + "' no tiene un resultado disponible -- su estado es '" + currentStatus + "', no 'completed'.");
	}

}
