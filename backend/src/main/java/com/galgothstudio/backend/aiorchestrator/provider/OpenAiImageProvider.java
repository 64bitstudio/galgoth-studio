package com.galgothstudio.backend.aiorchestrator.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Primer proveedor real de {@link ImageGenerationProvider} (ticket 051,
 * Diseño técnico §11/§12 de `docs/definiciones/galgoth-studio-fase3-textura.md`).
 * Mismo patrón que {@link ClaudeMessagesClient}: sin SDK oficial de OpenAI
 * para Java en Maven Central verificado para este proyecto, se llama la
 * API REST directamente con `RestClient` (ya viene con
 * `spring-boot-starter-webmvc`, sin dependencia nueva).
 *
 * <p><b>Modelo configurable, nunca hardcodeado (§12, cerrado por el PO)</b>:
 * {@code model} llega por constructor desde
 * {@code ai.openai.image-model=${OPENAI_IMAGE_MODEL:gpt-image-2.5-sunburst-2026-09-08}}
 * (ver {@link AiProviderConfig}) y viaja tal cual en el body de cada
 * llamada -- ningún literal de modelo en esta clase ni en el dominio.
 *
 * <p><b>Selección de endpoint (§12)</b>: {@code referenceImageBytes} no
 * vacío -&gt; {@code POST /v1/images/edits} (inpaint/edición sobre esa
 * imagen base, multipart/form-data); vacío/nulo -&gt; {@code POST
 * /v1/images/generations} (primera pasada, JSON). Ambos devuelven
 * {@code data[0].b64_json} -- se pide explícitamente `response_format:
 * b64_json` en generations (la familia `gpt-image-*` no soporta URLs
 * temporales de vuelta); `edits` no acepta `response_format` como
 * parámetro propio en la API pública de OpenAI vigente al momento de
 * escribir esto y siempre devuelve `b64_json` para `gpt-image-*` -- **a
 * verificar contra la documentación real cuando 054 conecte este
 * proveedor de punta a punta**, tal como pide el ticket 051.
 *
 * <p><b>{@code style} plegado en el prompt</b>: ver el Javadoc de
 * {@link ImageGenerationProvider.TextureGenerationSheetRequest}.
 *
 * <p><b>Reporte del modelo real usado (punto 6 del ticket 051)</b>:
 * {@link #model()} expone el valor configurado para que un futuro
 * orquestador (ticket 054) lo persista en {@code ai_jobs.model} sin que
 * este proveedor necesite conocer nada de persistencia -- mismo mecanismo
 * ya usado por {@code ClaudeVisionProvider}/{@code ClaudeReasoningProvider}
 * vía {@code ClaudeMessagesClient#model()}.
 */
public class OpenAiImageProvider implements ImageGenerationProvider {

	private static final Logger log = LoggerFactory.getLogger(OpenAiImageProvider.class);

	/** Tamaño default de {@link #generateImage(String)} -- ese método no recibe dimensiones (contrato heredado de 025, sin cambios); 1024x1024 es el tamaño cuadrado estándar soportado por toda la familia `gpt-image-*`/DALL-E conocida. */
	private static final String DEFAULT_SIZE = "1024x1024";

	private final RestClient restClient;
	private final ObjectMapper objectMapper;
	private final String apiKey;
	private final String model;

	public OpenAiImageProvider(RestClient.Builder restClientBuilder, ObjectMapper objectMapper, String baseUrl, String apiKey, String model) {
		this.restClient = restClientBuilder.baseUrl(baseUrl).build();
		this.objectMapper = objectMapper;
		this.apiKey = apiKey;
		this.model = model;
	}

	/** Modelo configurado (`ai.openai.image-model`) -- ver el Javadoc de la clase, punto 6 del ticket 051. */
	public String model() {
		return model;
	}

	@Override
	public byte[] generateImage(String prompt) {
		return callGenerations(prompt, DEFAULT_SIZE);
	}

	@Override
	public byte[] generateTextureSheet(TextureGenerationSheetRequest request) {
		String prompt = composePrompt(request.prompt(), request.style());
		String size = request.sheetWidth() + "x" + request.sheetHeight();
		if (request.referenceImageBytes() != null && request.referenceImageBytes().length > 0) {
			return callEdits(prompt, size, request.referenceImageBytes());
		}
		return callGenerations(prompt, size);
	}

	/** `style` no es un parámetro propio de `/v1/images/*` para esta familia de modelos (ver Javadoc de {@link ImageGenerationProvider.TextureGenerationSheetRequest}) -- se agrega como instrucción de texto explícita al final del prompt. */
	private String composePrompt(String prompt, String style) {
		if (style == null || style.isBlank()) {
			return prompt;
		}
		return prompt + "\n\nStyle: " + style;
	}

	private byte[] callGenerations(String prompt, String size) {
		requireApiKeyConfigured();
		ObjectNode body = objectMapper.createObjectNode();
		body.put("model", model);
		body.put("prompt", prompt);
		body.put("size", size);
		body.put("n", 1);
		body.put("response_format", "b64_json");

		log.info("Llamando a OpenAI Images API (generations) -- model={}", model);

		JsonNode response;
		try {
			response = restClient
					.post()
					.uri("/v1/images/generations")
					.header("Authorization", "Bearer " + apiKey)
					.contentType(MediaType.APPLICATION_JSON)
					.body(body)
					.retrieve()
					.body(JsonNode.class);
		} catch (RestClientException e) {
			throw new AiProviderException("Llamada a OpenAI Images API (generations) falló: " + e.getMessage(), e);
		}
		return extractImageBytes(response);
	}

	private byte[] callEdits(String prompt, String size, byte[] referenceImageBytes) {
		requireApiKeyConfigured();
		MultipartBodyBuilder multipart = new MultipartBodyBuilder();
		multipart.part("model", model);
		multipart.part("prompt", prompt);
		multipart.part("size", size);
		multipart.part("image", new ByteArrayResource(referenceImageBytes) {
			@Override
			public String getFilename() {
				return "reference.png";
			}
		}).contentType(MediaType.IMAGE_PNG);

		log.info("Llamando a OpenAI Images API (edits) -- model={}", model);

		JsonNode response;
		try {
			response = restClient
					.post()
					.uri("/v1/images/edits")
					.header("Authorization", "Bearer " + apiKey)
					.contentType(MediaType.MULTIPART_FORM_DATA)
					.body(multipart.build())
					.retrieve()
					.body(JsonNode.class);
		} catch (RestClientException e) {
			throw new AiProviderException("Llamada a OpenAI Images API (edits) falló: " + e.getMessage(), e);
		}
		return extractImageBytes(response);
	}

	private byte[] extractImageBytes(JsonNode response) {
		JsonNode data = response == null ? null : response.path("data");
		if (data == null || !data.isArray() || data.isEmpty()) {
			throw new AiProviderException("Respuesta de OpenAI Images API sin datos: " + response);
		}
		String base64 = data.get(0).path("b64_json").asText(null);
		if (base64 == null || base64.isBlank()) {
			throw new AiProviderException("Respuesta de OpenAI Images API sin b64_json: " + response);
		}
		return Base64.getDecoder().decode(base64);
	}

	private void requireApiKeyConfigured() {
		if (apiKey == null || apiKey.isBlank()) {
			throw new AiProviderException(
					"OPENAI_API_KEY no está configurada -- OpenAiImageProvider no puede llamar a la API real. "
							+ "Usa AI_IMAGE_PROVIDER=mock para desarrollo/tests sin key.");
		}
	}

}
