package com.galgothstudio.backend.aiorchestrator.planner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.galgothstudio.backend.domain.geometry.GeometryOperation;
import java.util.function.Consumer;

/**
 * Parsea incrementalmente el array JSON de {@link GeometryOperation} que
 * devuelve el Geometry Planner en modo streaming (ticket 038) -- el
 * proveedor entrega el texto de a fragmentos (deltas SSE de la API de
 * Anthropic) que pueden cortar cualquier token a la mitad; este parser
 * acumula el texto y, apenas detecta que un elemento completo del array
 * top-level (un objeto {@code {...}}) cerró su llave, lo deserializa y lo
 * entrega YA como {@link GeometryOperation} real al callback -- nunca
 * espera a que el array completo termine para entregar la primera
 * operación (a diferencia del modo no-streaming, que solo tiene la lista
 * completa recién cuando la respuesta entera llegó).
 *
 * <p>Deliberadamente simple (no es un parser JSON de propósito general):
 * solo necesita distinguir "estoy dentro de un string" (para no contar
 * llaves que aparezcan dentro de un valor de texto) y contar profundidad
 * de {@code {}} fuera de strings -- suficiente porque ninguno de los 9
 * tipos de {@link GeometryOperation} (ver `GeometryPlannerService`) anida
 * objetos dentro de sus campos, todos son escalares o arrays de números.
 */
public class StreamingOperationsParser {

	private final ObjectMapper objectMapper;
	private final Consumer<GeometryOperation> onOperation;

	private final StringBuilder elementBuffer = new StringBuilder();
	private int depth;
	private boolean insideString;
	private boolean escapeNext;

	public StreamingOperationsParser(ObjectMapper objectMapper, Consumer<GeometryOperation> onOperation) {
		this.objectMapper = objectMapper;
		this.onOperation = onOperation;
	}

	/** Alimenta un fragmento de texto nuevo (un delta SSE) -- puede cortar cualquier token a la mitad, incluso en medio de un string o un número. */
	public void feed(String textDelta) {
		for (int i = 0; i < textDelta.length(); i++) {
			consume(textDelta.charAt(i));
		}
	}

	private void consume(char c) {
		if (insideString) {
			appendIfInsideElement(c);
			if (escapeNext) {
				escapeNext = false;
			} else if (c == '\\') {
				escapeNext = true;
			} else if (c == '"') {
				insideString = false;
			}
			return;
		}
		if (c == '"') {
			insideString = true;
			appendIfInsideElement(c);
			return;
		}
		if (c == '{') {
			depth++;
			elementBuffer.append(c); // el `{` de apertura recién cuenta como "dentro" a partir de este incremento
			return;
		}
		if (c == '}') {
			elementBuffer.append(c); // el `}` de cierre es parte del elemento, se agrega ANTES de decrementar
			depth--;
			if (depth == 0) {
				emitElement();
			} else if (depth < 0) {
				// Cierre de `}` de más -- defensivo, nunca debería pasar con
				// una respuesta bien formada, pero no debe romper el parseo
				// de lo que sí vino bien.
				depth = 0;
				elementBuffer.setLength(0);
			}
			return;
		}
		appendIfInsideElement(c);
	}

	private void appendIfInsideElement(char c) {
		if (depth > 0) {
			elementBuffer.append(c);
		}
	}

	private void emitElement() {
		String elementJson = elementBuffer.toString();
		elementBuffer.setLength(0);
		GeometryOperation operation;
		try {
			operation = objectMapper.readValue(elementJson, GeometryOperation.class);
		} catch (Exception e) {
			throw new StreamingOperationParseException("No se pudo parsear una operación completa del stream: " + elementJson, e);
		}
		// Deliberadamente FUERA del try/catch de arriba: `onOperation` es
		// código del CALLER (ej. `MobGenerationService`, que puede lanzar
		// `GenerationCancelledException` real si el job se canceló justo
		// acá) -- envolverla junto con el parseo JSON disfrazaría una
		// cancelación real como un `StreamingOperationParseException` de
		// "JSON inválido", hallazgo real atrapado por el test de
		// cancelación a mitad de stream de `MobGenerationServiceStreamingTest`.
		onOperation.accept(operation);
	}

}
