package com.galgothstudio.backend.aiorchestrator.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * La mecánica HTTP completa (forma del request, parseo de respuesta,
 * errores) ya está cubierta en `ClaudeMessagesClientTest` -- este test
 * verifica SOLO que `ClaudeVisionProvider` delega correctamente y arma
 * un `AiProviderResponse` con los metadatos correctos (AC #1/#4 del
 * ticket 025).
 */
class ClaudeVisionProviderTest {

	@Test
	@SuppressWarnings({"deprecation", "removal"})
	void analyzeReferenceImage_delega_al_cliente_y_devuelve_provider_model_y_versiones_correctas() {
		RestClient.Builder builder = RestClient.builder()
				.messageConverters(converters -> converters.add(0, new MappingJackson2HttpMessageConverter(new ObjectMapper())));
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		server.expect(requestTo("https://api.anthropic.com/v1/messages"))
				.andRespond(withSuccess(
						"""
						{"content":[{"type":"text","text":"{\\"silhouette\\":\\"test\\"}"}]}
						""",
						MediaType.APPLICATION_JSON));
		ClaudeMessagesClient client =
				new ClaudeMessagesClient(builder, new ObjectMapper(), "https://api.anthropic.com", "test-key", "claude-sonnet-5");
		ClaudeVisionProvider provider = new ClaudeVisionProvider(client);

		VisionAnalysisRequest request =
				new VisionAnalysisRequest(new byte[] {1, 2, 3}, "image/png", "system", "user", "v1", "schema-v1");
		AiProviderResponse response = provider.analyzeReferenceImage(request);

		assertThat(response.rawContent()).isEqualTo("{\"silhouette\":\"test\"}");
		assertThat(response.provider()).isEqualTo("claude");
		assertThat(response.model()).isEqualTo("claude-sonnet-5");
		assertThat(response.promptVersion()).isEqualTo("v1");
		assertThat(response.schemaVersion()).isEqualTo("schema-v1");
		server.verify();
	}

}
