package com.galgothstudio.backend.aiorchestrator;

import java.util.UUID;

/** El mob no tiene ninguna imagen de referencia subida (024) todavía -- no hay nada que analizar. Nunca se llega a crear una fila en `ai_jobs` (el pipeline ni siquiera empieza). */
public class NoReferenceImageException extends RuntimeException {

	public NoReferenceImageException(UUID mobId) {
		super("El mob '" + mobId + "' no tiene ninguna imagen de referencia subida todavía.");
	}

}
