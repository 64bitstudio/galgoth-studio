package com.galgothstudio.backend.project;

import java.util.UUID;

/** No existe ningún proyecto con el id dado, o ya está soft-deleted (`deleted_at` no nulo se trata como "no existe" para efectos de la API). */
public class ProjectNotFoundException extends RuntimeException {

	public ProjectNotFoundException(UUID projectId) {
		super("No existe ningún proyecto con id '" + projectId + "'.");
	}

}
