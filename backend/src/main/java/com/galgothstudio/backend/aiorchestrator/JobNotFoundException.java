package com.galgothstudio.backend.aiorchestrator;

import java.util.UUID;

/** No existe ningún `ai_jobs` con ese id -- `GET /api/jobs/{jobId}/events` y `POST /api/jobs/{jobId}/cancel` (ticket 029) lo usan. */
public class JobNotFoundException extends RuntimeException {

	public JobNotFoundException(UUID jobId) {
		super("No existe ningún job de generación con id '" + jobId + "'.");
	}

}
