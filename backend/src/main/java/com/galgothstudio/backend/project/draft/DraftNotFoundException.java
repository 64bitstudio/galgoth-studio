package com.galgothstudio.backend.project.draft;

import java.util.UUID;

/** El mob existe pero todavía no tiene ninguna fila en `mob_drafts` (AC #3, ticket 020: respuesta explícita, no un draft vacío implícito). */
public class DraftNotFoundException extends RuntimeException {

	public DraftNotFoundException(UUID mobId) {
		super("El mob '" + mobId + "' todavía no tiene ningún draft persistido.");
	}

}
