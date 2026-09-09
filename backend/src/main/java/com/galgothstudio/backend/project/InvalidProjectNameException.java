package com.galgothstudio.backend.project;

/** HU-01 AC #2: crear/renombrar un proyecto sin nombre (blank) se rechaza con un mensaje de validación claro. */
public class InvalidProjectNameException extends RuntimeException {

	public InvalidProjectNameException() {
		super("El nombre del proyecto no puede estar vacío.");
	}

}
