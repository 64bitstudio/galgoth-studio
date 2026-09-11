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
import com.galgothstudio.backend.aiorchestrator.provider.ImageGenerationProvider.TextureGenerationSheetRequest;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.RequestMatcher;
import org.springframework.web.client.RestClient;

/**
 * `OpenAiImageProvider` NUNCA llama a la API real de OpenAI en este test
 * -- mismo patrón que `ClaudeMessagesClientTest` (`MockRestServiceServer`,
 * sin dependencia nueva, sin red de verdad).
 */
class OpenAiImageProviderTest {

	private static final String BASE_URL = "https://api.openai.com";
	/** Snapshot fechado real (§12 del diseño técnico) -- usado en varios tests para evitar un literal "cualquiera" que oculte que este ES el valor de negocio, sin repetirlo como 7 literales sueltos (Sonar S1192). */
	private static final String CONFIGURED_MODEL = "gpt-image-2.5-sunburst-2026-09-08";
	/** Un segundo snapshot fechado distinto, solo para el test que confirma que el modelo es por-instancia y no un estado compartido/estático. */
	private static final String OTHER_CONFIGURED_MODEL = "gpt-image-2.5-sunburst-2027-01-01";
	/** Modelo "genérico" para los tests a los que el valor exacto no les importa (solo que viaje tal cual). */
	private static final String GENERIC_MODEL = "modelo-x";
	private static final String TEST_API_KEY = "test-api-key";

	/** Ver el Javadoc de `ClaudeMessagesClientTest#newClientWithMockServer` -- mismo hallazgo raíz (Jackson 3 por defecto en Spring Framework 7 vs. `JsonNode` de Jackson 2 que este proveedor pide). */
	@SuppressWarnings({"deprecation", "removal"})
	private OpenAiImageProvider newProviderWithMockServer(MockRestServiceServer[] serverOut, String apiKey, String model) {
		RestClient.Builder builder = RestClient.builder()
				.messageConverters(converters -> converters.add(0, new MappingJackson2HttpMessageConverter(new ObjectMapper())));
		serverOut[0] = MockRestServiceServer.bindTo(builder).build();
		return new OpenAiImageProvider(builder, new ObjectMapper(), BASE_URL, apiKey, model);
	}

	private String base64Png(byte[] rawBytes) {
		return Base64.getEncoder().encodeToString(rawBytes);
	}

	@Test
	void generateTextureSheet_sin_imagen_de_referencia_llama_generations_con_el_modelo_configurado() {
		MockRestServiceServer[] serverBox = new MockRestServiceServer[1];
		OpenAiImageProvider provider = newProviderWithMockServer(serverBox, TEST_API_KEY, CONFIGURED_MODEL);
		byte[] resultBytes = {10, 20, 30};

		serverBox[0]
				.expect(requestTo(BASE_URL + "/v1/images/generations"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(header("Authorization", "Bearer test-api-key"))
				.andExpect(jsonPath("$.model").value(CONFIGURED_MODEL))
				.andExpect(jsonPath("$.prompt").value("cabeza de goblin\n\nStyle: pixel-art"))
				.andExpect(jsonPath("$.size").value("64x32"))
				.andExpect(jsonPath("$.n").value(1))
				.andRespond(withSuccess(
						"""
						{"data":[{"b64_json":"%s"}]}
						""".formatted(base64Png(resultBytes)),
						MediaType.APPLICATION_JSON));

		byte[] actual = provider.generateTextureSheet(new TextureGenerationSheetRequest("cabeza de goblin", null, 64, 32, "pixel-art"));

		assertThat(actual).isEqualTo(resultBytes);
		serverBox[0].verify();
	}

	@Test
	void generateTextureSheet_con_imagen_de_referencia_llama_edits_en_vez_de_generations() {
		MockRestServiceServer[] serverBox = new MockRestServiceServer[1];
		OpenAiImageProvider provider = newProviderWithMockServer(serverBox, TEST_API_KEY, CONFIGURED_MODEL);
		byte[] resultBytes = {1, 2, 3, 4};
		byte[] referenceImage = {5, 6, 7};

		RequestMatcher multipartContainsModelAndPrompt = request -> {
			String body = ((MockClientHttpRequest) request).getBodyAsString(StandardCharsets.UTF_8);
			assertThat(body).contains("name=\"model\"").contains(CONFIGURED_MODEL);
			assertThat(body).contains("name=\"prompt\"").contains("torso de goblin");
			assertThat(body).contains("name=\"image\"");
		};

		serverBox[0]
				.expect(requestTo(BASE_URL + "/v1/images/edits"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(header("Authorization", "Bearer test-api-key"))
				.andExpect(multipartContainsModelAndPrompt)
				.andRespond(withSuccess(
						"""
						{"data":[{"b64_json":"%s"}]}
						""".formatted(base64Png(resultBytes)),
						MediaType.APPLICATION_JSON));

		byte[] actual = provider.generateTextureSheet(
				new TextureGenerationSheetRequest("torso de goblin", referenceImage, 128, 128, null));

		assertThat(actual).isEqualTo(resultBytes);
		serverBox[0].verify();
	}

	/**
	 * Consolida en un solo `@ParameterizedTest` (hallazgo real de Sonar,
	 * S5976 -- "Replace these 5 tests with a single Parameterized one",
	 * mismo patrón ya establecido en `ModelIntentValidatorTest`) los 5
	 * casos de ajuste de `size` encontrados en vivo contra `studio-dev`:
	 * ticket 059 (múltiplo de 16) y ticket 060 (aspect ratio máximo 3:1)
	 * -- ver `tamanosDeSheetYElResultadoEsperado` para el detalle de cada
	 * caso real.
	 */
	@ParameterizedTest(name = "{0}")
	@MethodSource("tamanosDeSheetYElResultadoEsperado")
	void generateTextureSheet_ajusta_el_size_a_las_restricciones_reales_de_la_API_de_OpenAI(
			String caso, int width, int height, String expectedSize) {
		MockRestServiceServer[] serverBox = new MockRestServiceServer[1];
		OpenAiImageProvider provider = newProviderWithMockServer(serverBox, TEST_API_KEY, GENERIC_MODEL);

		serverBox[0]
				.expect(requestTo(BASE_URL + "/v1/images/generations"))
				.andExpect(jsonPath("$.size").value(expectedSize))
				.andRespond(withSuccess(
						"""
						{"data":[{"b64_json":"%s"}]}
						""".formatted(base64Png(new byte[] {1})),
						MediaType.APPLICATION_JSON));

		provider.generateTextureSheet(new TextureGenerationSheetRequest("prompt", null, width, height, null));

		serverBox[0].verify();
	}

	private static Stream<Arguments> tamanosDeSheetYElResultadoEsperado() {
		return Stream.of(
				// Ticket 059 (hallazgo real, verificación en vivo contra `studio-dev`):
				// dimensiones EXACTAS del sheet real que la API rechazó primero con
				// "Invalid size '58x8'. Width and height must both be divisible by 16." --
				// ningún test anterior lo detectó porque todos usaban dimensiones ya
				// múltiplos de 16 (mismo patrón de fixture-no-realista de
				// `TextureGenerationSheetPlanner`). El resultado final combina también
				// el hallazgo de 060 (58->64, 8->16->32: 16 por sí solo da ratio 4:1,
				// rechazado por separado).
				Arguments.of("58x8 real (no múltiplo de 16 + ratio excedido tras redondear) -> 64x32", 58, 8, "64x32"),
				// Ticket 060 (segundo hallazgo real, misma verificación): 64x16 ya es
				// múltiplo de 16 pero ratio 4:1 -- la API real lo rechazó por separado
				// con "The maximum supported aspect ratio is 3:1." Aislado de 058x8 para
				// no mezclar los dos hallazgos en un solo caso.
				Arguments.of("64x16 (ratio 4:1, ya múltiplo de 16) -> alto agrandado a 32", 64, 16, "64x32"),
				// Mismo ajuste, orientación invertida -- confirma que el clamp de
				// aspect ratio es simétrico (ancho agrandado, no el alto).
				Arguments.of("16x64 (ratio 1:4, vertical) -> ancho agrandado a 32", 16, 64, "32x64"),
				// Un ratio 3:1 exacto (el límite mismo) NO debe alterarse -- confirma
				// que el clamp es "> 3", no "3 inclusive". Nota: la API real solo
				// confirmó que 4:1 se rechaza; que 3:1 exacto SÍ se acepta es una
				// lectura razonable del mensaje de error, no verificada en vivo -- si
				// un futuro intento real muestra lo contrario, este caso debe ajustarse.
				Arguments.of("48x16 (ratio exactamente 3:1) sin alterar", 48, 16, "48x16"),
				// Caso base: ya múltiplo de 16 y dentro del ratio permitido -- ningún
				// ajuste debería aplicarse.
				Arguments.of("32x32 (ya múltiplo de 16, ratio 1:1) sin alterar", 32, 32, "32x32"));
	}

	@Test
	void generateTextureSheet_con_bytes_de_referencia_vacios_se_trata_como_sin_referencia() {
		MockRestServiceServer[] serverBox = new MockRestServiceServer[1];
		OpenAiImageProvider provider = newProviderWithMockServer(serverBox, TEST_API_KEY, GENERIC_MODEL);

		serverBox[0]
				.expect(requestTo(BASE_URL + "/v1/images/generations"))
				.andRespond(withSuccess(
						"""
						{"data":[{"b64_json":"%s"}]}
						""".formatted(base64Png(new byte[] {1})),
						MediaType.APPLICATION_JSON));

		provider.generateTextureSheet(new TextureGenerationSheetRequest("prompt", new byte[0], 16, 16, null));

		serverBox[0].verify();
	}

	@Test
	void model_reporta_exactamente_el_valor_configurado_por_instancia_sin_estado_compartido() {
		MockRestServiceServer[] serverBoxA = new MockRestServiceServer[1];
		MockRestServiceServer[] serverBoxB = new MockRestServiceServer[1];
		OpenAiImageProvider providerA = newProviderWithMockServer(serverBoxA, "key-a", CONFIGURED_MODEL);
		OpenAiImageProvider providerB = newProviderWithMockServer(serverBoxB, "key-b", OTHER_CONFIGURED_MODEL);

		assertThat(providerA.model()).isEqualTo(CONFIGURED_MODEL);
		assertThat(providerB.model()).isEqualTo(OTHER_CONFIGURED_MODEL);
		assertThat(providerA.provider()).isEqualTo("openai");

		serverBoxA[0]
				.expect(requestTo(BASE_URL + "/v1/images/generations"))
				.andExpect(jsonPath("$.model").value(CONFIGURED_MODEL))
				.andRespond(withSuccess(
						"""
						{"data":[{"b64_json":"%s"}]}
						""".formatted(base64Png(new byte[] {1})),
						MediaType.APPLICATION_JSON));
		serverBoxB[0]
				.expect(requestTo(BASE_URL + "/v1/images/generations"))
				.andExpect(jsonPath("$.model").value(OTHER_CONFIGURED_MODEL))
				.andRespond(withSuccess(
						"""
						{"data":[{"b64_json":"%s"}]}
						""".formatted(base64Png(new byte[] {2})),
						MediaType.APPLICATION_JSON));

		providerA.generateTextureSheet(new TextureGenerationSheetRequest("prompt", null, 16, 16, null));
		providerB.generateTextureSheet(new TextureGenerationSheetRequest("prompt", null, 16, 16, null));

		serverBoxA[0].verify();
		serverBoxB[0].verify();
	}

	@Test
	void generateImage_llama_generations_con_tamano_default_1024x1024() {
		MockRestServiceServer[] serverBox = new MockRestServiceServer[1];
		OpenAiImageProvider provider = newProviderWithMockServer(serverBox, TEST_API_KEY, GENERIC_MODEL);

		serverBox[0]
				.expect(requestTo(BASE_URL + "/v1/images/generations"))
				.andExpect(jsonPath("$.size").value("1024x1024"))
				.andExpect(jsonPath("$.prompt").value("un atardecer"))
				.andRespond(withSuccess(
						"""
						{"data":[{"b64_json":"%s"}]}
						""".formatted(base64Png(new byte[] {42})),
						MediaType.APPLICATION_JSON));

		byte[] actual = provider.generateImage("un atardecer");

		assertThat(actual).isEqualTo(new byte[] {42});
		serverBox[0].verify();
	}

	@Test
	void respuesta_sin_b64_json_falla_explicito() {
		MockRestServiceServer[] serverBox = new MockRestServiceServer[1];
		OpenAiImageProvider provider = newProviderWithMockServer(serverBox, TEST_API_KEY, GENERIC_MODEL);
		serverBox[0]
				.expect(requestTo(BASE_URL + "/v1/images/generations"))
				.andRespond(withSuccess("""
						{"data":[]}
						""", MediaType.APPLICATION_JSON));

		assertThatThrownBy(() -> provider.generateImage("prompt"))
				.isInstanceOf(AiProviderException.class)
				.hasMessageContaining("sin datos");
	}

	@Test
	void una_respuesta_de_error_HTTP_se_propaga_como_AiProviderException() {
		MockRestServiceServer[] serverBox = new MockRestServiceServer[1];
		OpenAiImageProvider provider = newProviderWithMockServer(serverBox, TEST_API_KEY, GENERIC_MODEL);
		serverBox[0].expect(requestTo(BASE_URL + "/v1/images/generations")).andRespond(withServerError());

		assertThatThrownBy(() -> provider.generateImage("prompt")).isInstanceOf(AiProviderException.class);
	}

	@Test
	void sin_api_key_configurada_falla_explicito_sin_intentar_la_llamada() {
		MockRestServiceServer[] serverBox = new MockRestServiceServer[1];
		OpenAiImageProvider provider = newProviderWithMockServer(serverBox, "", GENERIC_MODEL);

		assertThatThrownBy(() -> provider.generateImage("prompt"))
				.isInstanceOf(AiProviderException.class)
				.hasMessageContaining("OPENAI_API_KEY");
		serverBox[0].verify(); // cero expectativas registradas -- confirma que nunca se intentó la llamada
	}

}
