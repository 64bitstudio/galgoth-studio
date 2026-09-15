package com.galgothstudio.backend.account;

/** Content-Type fuera de la whitelist soportada, archivo por encima del tamaño máximo, o bytes que no decodifican como una imagen válida (ticket 091, mismo criterio que {@code InvalidReferenceImageException}, ticket 024). */
public class InvalidAvatarException extends RuntimeException {

	public InvalidAvatarException(String message) {
		super(message);
	}

}
