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

/** Mismo criterio que `ClaudeVisionProviderTest` -- la mecánica HTTP ya está cubierta en `ClaudeMessagesClientTest`. */
class ClaudeReasoningProviderTest {

	@Test
	@SuppressWarnings({"deprecation", "removal"})
	void reason_delega_al_cliente_y_devuelve_provider_model_y_versiones_correctas() {
		RestClient.Builder builder = RestClient.builder()
				.messageConverters(converters -> converters.add(0, new MappingJackson2HttpMessageConverter(new ObjectMapper())));
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		server.expect(requestTo("https://api.anthropic.com/v1/messages"))
				.andRespond(withSuccess(
						"""
						{"content":[{"type":"text","text":"{\\"summary\\":\\"ok\\"}"}]}
						""",
						MediaType.APPLICATION_JSON));
		ClaudeMessagesClient client =
				new ClaudeMessagesClient(builder, new ObjectMapper(), "https://api.anthropic.com", "test-key", "claude-sonnet-5");
		ClaudeReasoningProvider provider = new ClaudeReasoningProvider(client);

		ReasoningRequest request = new ReasoningRequest("system", "user", "v2", "schema-v2");
		AiProviderResponse response = provider.reason(request);

		assertThat(response.rawContent()).isEqualTo("{\"summary\":\"ok\"}");
		assertThat(response.provider()).isEqualTo("claude");
		assertThat(response.model()).isEqualTo("claude-sonnet-5");
		assertThat(response.promptVersion()).isEqualTo("v2");
		assertThat(response.schemaVersion()).isEqualTo("schema-v2");
		server.verify();
	}

}
