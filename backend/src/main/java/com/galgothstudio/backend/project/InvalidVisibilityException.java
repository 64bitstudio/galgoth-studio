package com.galgothstudio.backend.project;

/** `visibility` fuera de `"PRIVATE"`/`"PUBLIC"` (ticket 086, mismo `CHECK` de la columna, `V5`). */
public class InvalidVisibilityException extends RuntimeException {

	public InvalidVisibilityException(String visibility) {
		super("visibility inválida: '" + visibility + "' -- debe ser 'PRIVATE' o 'PUBLIC'.");
	}

}
