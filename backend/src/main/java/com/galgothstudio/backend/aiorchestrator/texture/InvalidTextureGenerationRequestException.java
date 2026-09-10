package com.galgothstudio.backend.aiorchestrator.texture;

/** `style`/`detailLevel` fuera de los valores aceptados, o el mob no tiene ningún bone con geometría que texturizar -- 400, nunca se llega a crear una fila `ai_jobs` (ticket 054). */
public class InvalidTextureGenerationRequestException extends RuntimeException {

	public InvalidTextureGenerationRequestException(String message) {
		super(message);
	}

}
