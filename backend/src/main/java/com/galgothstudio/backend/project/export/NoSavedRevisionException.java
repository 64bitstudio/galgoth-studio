package com.galgothstudio.backend.project.export;

import java.util.UUID;

/**
 * El mob existe pero nunca tiene una revisión guardada (`mobs.current_revision_number=0`) --
 * ticket 032, AC #1: el exportador SIEMPRE usa `mob_revisions`, nunca el
 * draft en curso, así que sin ninguna revisión no hay nada exportable
 * todavía ("Guardar y exportar" es la única acción posible en ese caso).
 */
public class NoSavedRevisionException extends RuntimeException {

	public NoSavedRevisionException(UUID mobId) {
		super("El mob '" + mobId + "' todavía no tiene ninguna revisión guardada -- no hay nada que exportar todavía.");
	}

}
