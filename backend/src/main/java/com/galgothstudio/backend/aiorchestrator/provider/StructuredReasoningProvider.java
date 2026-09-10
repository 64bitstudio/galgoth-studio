package com.galgothstudio.backend.aiorchestrator.provider;

import java.util.function.Consumer;

/**
 * Razonamiento estructurado sobre texto -- devuelve el JSON crudo de una
 * propuesta de `GeometryOperation[]` (master prompt §9.2/§9.3), SIN
 * VALIDAR (responsabilidad del caller, tickets 028/031). Mismo criterio
 * de selección por entorno que {@link VisionModelProvider}, vía
 * `AI_REASONING_PROVIDER`.
 */
public interface StructuredReasoningProvider {

	AiProviderResponse reason(ReasoningRequest request);

	/**
	 * Ticket 038 -- variante streaming: entrega fragmentos de texto real
	 * al callback A MEDIDA que el proveedor los produce, en vez de recién
	 * al final. Default no-op-friendly: un proveedor que no soporta
	 * streaming de verdad (ej. {@code MockReasoningProvider}, que ya
	 * resuelve en &lt;100ms) simplemente entrega la respuesta completa como
	 * un único fragmento -- sigue siendo 100% correcto, solo sin el
	 * beneficio de incrementalidad (nunca lo necesitó, el modo mock nunca
	 * fue lento). Solo {@link ClaudeReasoningProvider} sobreescribe esto
	 * con streaming real.
	 */
	default AiProviderResponse reasonStreaming(ReasoningRequest request, Consumer<String> onTextDelta) {
		AiProviderResponse response = reason(request);
		onTextDelta.accept(response.rawContent());
		return response;
	}

}
