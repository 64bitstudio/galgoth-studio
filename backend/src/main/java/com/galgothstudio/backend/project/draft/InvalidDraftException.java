package com.galgothstudio.backend.project.draft;

import java.util.List;

/** El draft enviado a Guardar no pasa la validación de invariantes ({@link com.galgothstudio.backend.modelvalidation.MobProjectModelValidator}) -- no se crea ninguna revisión. */
public class InvalidDraftException extends RuntimeException {

	private final transient List<String> errors;

	public InvalidDraftException(List<String> errors) {
		super("El draft no pasa la validación de invariantes: " + errors);
		this.errors = errors;
	}

	public List<String> getErrors() {
		return errors;
	}

}
