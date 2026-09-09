package com.galgothstudio.backend.aiorchestrator.provider;

/** Fallo real de un proveedor de IA (red, HTTP no-2xx, respuesta inesperada) -- nunca se traga en silencio, siempre se propaga con el detalle real. */
public class AiProviderException extends RuntimeException {

	public AiProviderException(String message) {
		super(message);
	}

	public AiProviderException(String message, Throwable cause) {
		super(message, cause);
	}

}
