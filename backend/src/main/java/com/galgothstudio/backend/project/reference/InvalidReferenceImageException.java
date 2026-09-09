package com.galgothstudio.backend.project.reference;

/** Content-Type fuera de la whitelist soportada, archivo por encima del tamaño máximo, o bytes que no decodifican como una imagen válida (ticket 024). */
public class InvalidReferenceImageException extends RuntimeException {

	public InvalidReferenceImageException(String message) {
		super(message);
	}

}
