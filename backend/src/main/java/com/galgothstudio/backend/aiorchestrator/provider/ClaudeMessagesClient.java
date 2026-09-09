package com.galgothstudio.backend.aiorchestrator.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Cliente HTTP compartido de la API de mensajes de Anthropic
 * (`POST /v1/messages`) -- la mecánica real (headers, forma del body,
 * parseo de la respuesta) vive UNA sola vez acá; {@link ClaudeVisionProvider}
 * y {@link ClaudeReasoningProvider} solo arman el contenido específico
 * de su interfaz y delegan.
 *
 * Deliberadamente NO implementa ninguna interfaz de proveedor -- por eso
 * es seguro exponerlo como un bean Spring compartido entre ambos
 * providers sin el hallazgo de ambigüedad documentado en
 * `AiProviderConfig` (ese problema ocurre solo cuando UNA clase
 * implementa 2+ interfaces de proveedor a la vez).
 *
 * Sin SDK oficial de Anthropic para Java en Maven Central (verificado) --
 * se llama la API REST directamente con `RestClient` (ya viene con
 * `spring-boot-starter-webmvc`, sin dependencia nueva), reutilizando el
 * `ObjectMapper`/converter Jackson 2 ya configurados (ticket 020) para
 * que la serialización sea consistente con el resto del backend.
 */
public class ClaudeMessagesClient {

	private static final Logger log = LoggerFactory.getLogger(ClaudeMessagesClient.class);
	private static final String ANTHROPIC_VERSION = "2023-06-01";
	/**
	 * Hallazgo real de verificación en vivo (ticket 028): `claude-sonnet-5`
	 * devuelve un bloque `"type":"thinking"` (razonamiento extendido) ANTES
	 * del bloque `"type":"text"` real -- con `max_tokens` en 4096, el
	 * modelo gastó el presupuesto COMPLETO pensando (`stop_reason:
	 * "max_tokens"`) y nunca llegó a emitir ningún texto, sin que el
	 * request lo haya pedido explícitamente. 16000 deja margen real para
	 * el razonamiento MÁS la salida (un batch de geometría completo para
	 * un rig humanoide puede ser largo).
	 */
	private static final int MAX_TOKENS = 16000;

	private final RestClient restClient;
	private final ObjectMapper objectMapper;
	private final String apiKey;
	private final String model;

	public ClaudeMessagesClient(RestClient.Builder restClientBuilder, ObjectMapper objectMapper, String baseUrl, String apiKey, String model) {
		this.restClient = restClientBuilder.baseUrl(baseUrl).build();
		this.objectMapper = objectMapper;
		this.apiKey = apiKey;
		this.model = model;
	}

	public String model() {
		return model;
	}

	/** Mensaje con imagen + texto (visión) -- dos bloques de contenido, el orden (imagen antes que texto) sigue la convención recomendada por Anthropic para prompts multimodales. */
	public String callWithImage(String systemPrompt, byte[] imageBytes, String contentType, String userPrompt) {
		ObjectNode imageBlock = objectMapper.createObjectNode();
		imageBlock.put("type", "image");
		ObjectNode source = imageBlock.putObject("source");
		source.put("type", "base64");
		source.put("media_type", contentType);
		source.put("data", Base64.getEncoder().encodeToString(imageBytes));

		ObjectNode textBlock = objectMapper.createObjectNode();
		textBlock.put("type", "text");
		textBlock.put("text", userPrompt);

		ArrayNode content = objectMapper.createArrayNode();
		content.add(imageBlock);
		content.add(textBlock);

		return call(systemPrompt, content);
	}

	/** Mensaje solo de texto (razonamiento) -- `content` como `String` plano, forma igualmente válida del wire format de Anthropic. */
	public String callWithText(String systemPrompt, String userPrompt) {
		return call(systemPrompt, userPrompt);
	}

	private String call(String systemPrompt, Object userContent) {
		requireApiKeyConfigured();

		ObjectNode message = objectMapper.createObjectNode();
		message.put("role", "user");
		message.putPOJO("content", userContent);

		ArrayNode messages = objectMapper.createArrayNode();
		messages.add(message);

		ObjectNode body = objectMapper.createObjectNode();
		body.put("model", model);
		body.put("max_tokens", MAX_TOKENS);
		body.put("system", systemPrompt);
		body.set("messages", messages);

		log.info("Llamando a Anthropic Messages API -- model={}", model);

		JsonNode response;
		try {
			// Se pasa el ObjectNode tal cual (no bytes pre-serializados a mano):
			// un byte[] con Content-Type JSON es ambiguo entre "bytes crudos a
			// escribir" y "un valor a serializar como JSON" (Jackson serializa
			// byte[] como string base64 por defecto) -- confirmado real en el
			// test de este cliente, que capturaba un body base64 en vez del JSON
			// esperado. Dejar que el converter serialice el objeto elimina la
			// ambigüedad.
			response = restClient
					.post()
					.uri("/v1/messages")
					.header("x-api-key", apiKey)
					.header("anthropic-version", ANTHROPIC_VERSION)
					.contentType(MediaType.APPLICATION_JSON)
					.body(body)
					.retrieve()
					.body(JsonNode.class);
		} catch (RestClientException e) {
			throw new AiProviderException("Llamada a la API de Anthropic falló: " + e.getMessage(), e);
		}

		return extractText(response);
	}

	private String extractText(JsonNode response) {
		JsonNode content = response == null ? null : response.path("content");
		if (content == null || !content.isArray() || content.isEmpty()) {
			throw new AiProviderException("Respuesta de Anthropic sin contenido: " + response);
		}
		StringBuilder text = new StringBuilder();
		for (JsonNode block : content) {
			if ("text".equals(block.path("type").asText())) {
				text.append(block.path("text").asText());
			}
		}
		if (text.isEmpty()) {
			String stopReason = response.path("stop_reason").asText("");
			if ("max_tokens".equals(stopReason)) {
				// Hallazgo real (ticket 028): el modelo gastó todo max_tokens en
				// un bloque "thinking" antes de llegar a emitir texto -- un
				// error específico ahorra tener que releer la respuesta cruda
				// completa (miles de caracteres de "thinking" en base64) la
				// próxima vez que esto pase.
				throw new AiProviderException(
						"Respuesta de Anthropic truncada por max_tokens antes de emitir ningún bloque de texto "
								+ "(probable razonamiento extendido consumiendo todo el presupuesto) -- subir MAX_TOKENS.");
			}
			throw new AiProviderException("Respuesta de Anthropic sin ningún bloque de texto: " + response);
		}
		return stripMarkdownCodeFence(text.toString());
	}

	/**
	 * Claude envuelve la respuesta en un bloque de código Markdown
	 * (```json ... ``` o ``` ... ```) con cierta frecuencia incluso
	 * cuando el prompt pide explícitamente "sin texto antes ni después"
	 * -- confirmado real en verificación en vivo contra la API real
	 * (ticket 028): el planner de geometría lo hizo, la respuesta de
	 * visión no. Nunca confiar en que el modelo respete la instrucción al
	 * 100% -- se despoja el fence acá, en el único punto por el que pasa
	 * CUALQUIER llamada (visión o razonamiento), en vez de duplicar esta
	 * lógica en cada caller.
	 */
	private String stripMarkdownCodeFence(String text) {
		String trimmed = text.strip();
		if (!trimmed.startsWith("```")) {
			return trimmed;
		}
		int firstNewline = trimmed.indexOf('\n');
		if (firstNewline == -1) {
			return trimmed;
		}
		int closingFence = trimmed.lastIndexOf("```");
		if (closingFence <= firstNewline) {
			return trimmed;
		}
		return trimmed.substring(firstNewline + 1, closingFence).strip();
	}

	private void requireApiKeyConfigured() {
		if (apiKey == null || apiKey.isBlank()) {
			throw new AiProviderException(
					"ANTHROPIC_API_KEY no está configurada -- ClaudeProvider no puede llamar a la API real. "
							+ "Usa AI_VISION_PROVIDER=mock/AI_REASONING_PROVIDER=mock para desarrollo/tests sin key.");
		}
	}

}
