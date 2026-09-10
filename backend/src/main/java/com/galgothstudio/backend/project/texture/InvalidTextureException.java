package com.galgothstudio.backend.project.texture;

/** Bytes que no decodifican como un PNG válido en `PUT /api/mobs/{mobId}/texture` (ticket 045) -- nunca se confía en el `Content-Type` declarado por el cliente. */
public class InvalidTextureException extends RuntimeException {

	public InvalidTextureException(String message) {
		super(message);
	}

}
