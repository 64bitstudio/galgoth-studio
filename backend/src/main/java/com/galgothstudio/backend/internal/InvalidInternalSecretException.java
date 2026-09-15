package com.galgothstudio.backend.internal;

/** Falta el header `X-Internal-Secret`, o no coincide con `galgoth.internal.secret` (ticket 091) -- nunca revela cuál de los dos casos fue. */
public class InvalidInternalSecretException extends RuntimeException {

	public InvalidInternalSecretException() {
		super("Secreto interno inválido o ausente.");
	}

}
