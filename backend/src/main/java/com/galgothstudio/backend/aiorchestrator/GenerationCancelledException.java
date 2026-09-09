package com.galgothstudio.backend.aiorchestrator;

/**
 * Señal interna de control (ticket 029, AC #4) -- lanzada en un punto de
 * control cuando {@link com.galgothstudio.backend.aiorchestrator.progress.GenerationCancellationRegistry}
 * confirma que el job fue cancelado. Nunca cruza la frontera HTTP (no
 * hay ningún `@ExceptionHandler` para ella) -- `MobGenerationService`
 * la captura internamente para marcar el job `cancelled` y terminar el
 * stream SSE, no para reportar un error al cliente.
 */
class GenerationCancelledException extends RuntimeException {

	GenerationCancelledException() {
		super("La generación fue cancelada por el usuario.");
	}

}
