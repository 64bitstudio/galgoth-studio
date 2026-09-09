package com.galgothstudio.backend.aiorchestrator.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * `ClaudeMessagesClient` NUNCA llama a la API real de Anthropic en este
 * test -- `MockRestServiceServer` (Spring Test, sin dependencia nueva)
 * intercepta el `RestClient` a nivel de transporte HTTP, verificando la
 * forma REAL del request (método, headers, JSON body) y controlando la
 * respuesta, sin red de verdad. Consistente con el AC del ticket 025:
 * "MockProvider, uso exclusivo en tests" -- ningún test de la suite
 * automatizada gasta cuota real de la API.
 */
class ClaudeMessagesClientTest {

	private static final String BASE_URL = "https://api.anthropic.com";

	/**
	 * `RestClient.builder()` "a pelo" (sin pasar por la autoconfiguración
	 * de Spring Boot) usa el converter Jackson por defecto de Spring
	 * Framework 7 (Jackson 3, `tools.jackson.*`) -- no puede deserializar
	 * `com.fasterxml.jackson.databind.JsonNode` (Jackson 2, lo que este
	 * cliente pide) y falla con `InvalidDefinitionException`. En
	 * producción esto nunca pasa: el `RestClient.Builder` inyectado viene
	 * de Spring Boot ya configurado con el `MappingJackson2HttpMessageConverter`
	 * de `JacksonConfig` (ticket 020) -- mismo hallazgo raíz, registrado
	 * acá explícitamente para el test.
	 */
	@SuppressWarnings({"deprecation", "removal"})
	private ClaudeMessagesClient newClientWithMockServer(MockRestServiceServer[] serverOut, String apiKey) {
		RestClient.Builder builder = RestClient.builder()
				.messageConverters(converters -> converters.add(0, new MappingJackson2HttpMessageConverter(new ObjectMapper())));
		serverOut[0] = MockRestServiceServer.bindTo(builder).build();
		return new ClaudeMessagesClient(builder, new ObjectMapper(), BASE_URL, apiKey, "claude-sonnet-5");
	}

	@Test
	void callWithImage_envia_bloques_imagen_y_texto_y_parsea_el_texto_de_la_respuesta() {
		MockRestServiceServer[] serverBox = new MockRestServiceServer[1];
		ClaudeMessagesClient client = newClientWithMockServer(serverBox, "test-api-key");
		byte[] imageBytes = {1, 2, 3, 4};
		String expectedBase64 = Base64.getEncoder().encodeToString(imageBytes);

		serverBox[0]
				.expect(requestTo(BASE_URL + "/v1/messages"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(header("x-api-key", "test-api-key"))
				.andExpect(header("anthropic-version", "2023-06-01"))
				.andExpect(jsonPath("$.model").value("claude-sonnet-5"))
				.andExpect(jsonPath("$.system").value("system-prompt"))
				.andExpect(jsonPath("$.messages[0].role").value("user"))
				.andExpect(jsonPath("$.messages[0].content[0].type").value("image"))
				.andExpect(jsonPath("$.messages[0].content[0].source.data").value(expectedBase64))
				.andExpect(jsonPath("$.messages[0].content[1].type").value("text"))
				.andExpect(jsonPath("$.messages[0].content[1].text").value("user-prompt"))
				.andRespond(withSuccess(
						"""
						{"content":[{"type":"text","text":"{\\"silhouette\\":\\"test\\"}"}]}
						""",
						MediaType.APPLICATION_JSON));

		String rawContent = client.callWithImage("system-prompt", imageBytes, "image/png", "user-prompt");

		assertThat(rawContent).isEqualTo("{\"silhouette\":\"test\"}");
		serverBox[0].verify();
	}

	@Test
	void callWithText_envia_el_prompt_como_contenido_de_texto_plano() {
		MockRestServiceServer[] serverBox = new MockRestServiceServer[1];
		ClaudeMessagesClient client = newClientWithMockServer(serverBox, "test-api-key");

		serverBox[0]
				.expect(requestTo(BASE_URL + "/v1/messages"))
				.andExpect(jsonPath("$.messages[0].content").value("Haz las manos más grandes"))
				.andRespond(withSuccess(
						"""
						{"content":[{"type":"text","text":"{\\"summary\\":\\"ok\\"}"}]}
						""",
						MediaType.APPLICATION_JSON));

		String rawContent = client.callWithText("system-prompt", "Haz las manos más grandes");

		assertThat(rawContent).isEqualTo("{\"summary\":\"ok\"}");
		serverBox[0].verify();
	}

	@Test
	void una_respuesta_truncada_por_max_tokens_sin_ningun_bloque_de_texto_da_un_error_especifico_hallazgo_real() {
		MockRestServiceServer[] serverBox = new MockRestServiceServer[1];
		ClaudeMessagesClient client = newClientWithMockServer(serverBox, "test-api-key");
		serverBox[0]
				.expect(requestTo(BASE_URL + "/v1/messages"))
				.andRespond(withSuccess(
						"""
						{"content":[{"type":"thinking","thinking":"..."}],"stop_reason":"max_tokens"}
						""",
						MediaType.APPLICATION_JSON));

		assertThatThrownBy(() -> client.callWithText("s", "u"))
				.isInstanceOf(AiProviderException.class)
				.hasMessageContaining("max_tokens");
	}

	@Test
	void una_respuesta_envuelta_en_un_bloque_de_codigo_markdown_se_despoja_del_fence_AC_hallazgo_real() {
		MockRestServiceServer[] serverBox = new MockRestServiceServer[1];
		ClaudeMessagesClient client = newClientWithMockServer(serverBox, "test-api-key");
		serverBox[0]
				.expect(requestTo(BASE_URL + "/v1/messages"))
				.andRespond(withSuccess(
						"""
						{"content":[{"type":"text","text":"```json\\n{\\"summary\\":\\"ok\\"}\\n```"}]}
						""",
						MediaType.APPLICATION_JSON));

		String rawContent = client.callWithText("s", "u");

		assertThat(rawContent).isEqualTo("{\"summary\":\"ok\"}");
	}

	@Test
	void un_fence_generico_sin_el_lenguaje_json_tambien_se_despoja() {
		MockRestServiceServer[] serverBox = new MockRestServiceServer[1];
		ClaudeMessagesClient client = newClientWithMockServer(serverBox, "test-api-key");
		serverBox[0]
				.expect(requestTo(BASE_URL + "/v1/messages"))
				.andRespond(withSuccess(
						"""
						{"content":[{"type":"text","text":"```\\n[1,2,3]\\n```"}]}
						""",
						MediaType.APPLICATION_JSON));

		String rawContent = client.callWithText("s", "u");

		assertThat(rawContent).isEqualTo("[1,2,3]");
	}

	@Test
	void una_respuesta_de_error_HTTP_se_propaga_como_AiProviderException() {
		MockRestServiceServer[] serverBox = new MockRestServiceServer[1];
		ClaudeMessagesClient client = newClientWithMockServer(serverBox, "test-api-key");
		serverBox[0].expect(requestTo(BASE_URL + "/v1/messages")).andRespond(withServerError());

		assertThatThrownBy(() -> client.callWithText("s", "u")).isInstanceOf(AiProviderException.class);
	}

	@Test
	void sin_api_key_configurada_falla_explicito_sin_intentar_la_llamada() {
		MockRestServiceServer[] serverBox = new MockRestServiceServer[1];
		ClaudeMessagesClient client = newClientWithMockServer(serverBox, "");

		assertThatThrownBy(() -> client.callWithText("s", "u"))
				.isInstanceOf(AiProviderException.class)
				.hasMessageContaining("ANTHROPIC_API_KEY");
		serverBox[0].verify(); // cero expectativas registradas -- confirma que nunca se intentó la llamada
	}

}
