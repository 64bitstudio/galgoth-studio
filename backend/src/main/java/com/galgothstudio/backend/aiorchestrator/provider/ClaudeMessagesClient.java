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
	private static final int MAX_TOKENS = 4096;

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
			throw new AiProviderException("Respuesta de Anthropic sin ningún bloque de texto: " + response);
		}
		return text.toString();
	}

	private void requireApiKeyConfigured() {
		if (apiKey == null || apiKey.isBlank()) {
			throw new AiProviderException(
					"ANTHROPIC_API_KEY no está configurada -- ClaudeProvider no puede llamar a la API real. "
							+ "Usa AI_VISION_PROVIDER=mock/AI_REASONING_PROVIDER=mock para desarrollo/tests sin key.");
		}
	}

}
