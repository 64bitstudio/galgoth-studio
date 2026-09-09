package com.galgothstudio.backend.project.draft;

import java.util.UUID;

/** No existe ningún mob con el id dado -- distinto de {@link DraftNotFoundException} (mob real, sin draft todavía). */
public class MobNotFoundException extends RuntimeException {

	public MobNotFoundException(UUID mobId) {
		super("No existe ningún mob con id '" + mobId + "'.");
	}

}
