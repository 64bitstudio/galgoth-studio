package com.galgothstudio.backend.aiorchestrator.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.galgothstudio.backend.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Hallazgo real de este ticket, verificado acá para que nunca se rompa
 * en silencio: el `RestClient.Builder` que la app INYECTA de verdad
 * (autoconfigurado por `spring-boot-starter-restclient`, ver
 * `AiProviderConfig`) debe incluir el `MappingJackson2HttpMessageConverter`
 * de `JacksonConfig` (ticket 020) -- si no lo tuviera, `ClaudeMessagesClient`
 * enviaría bodies JSON malformados a la API real de Anthropic (confirmado
 * real en verificación manual: un `RestClient.builder()` "a pelo", sin
 * pasar por Spring, serializaba un `ObjectNode` como `{}` vacío, y
 * Anthropic respondía `"model: Field required"`). Este test usa el
 * `RestClient.Builder` REAL de la aplicación (no uno construido a mano
 * en el test) para probar la configuración real, no una simulación de
 * ella.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ClaudeMessagesClientWiringTest {

	@Autowired
	private RestClient.Builder restClientBuilder;

	@Test
	void el_RestClient_Builder_inyectado_por_Spring_serializa_un_ObjectNode_como_JSON_real_no_vacio() {
		MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
		ClaudeMessagesClient client =
				new ClaudeMessagesClient(restClientBuilder, new ObjectMapper(), "https://api.anthropic.com", "test-key", "claude-sonnet-5");

		server.expect(requestTo("https://api.anthropic.com/v1/messages"))
				.andExpect(jsonPath("$.model").value("claude-sonnet-5"))
				.andExpect(jsonPath("$.system").value("system-prompt"))
				.andExpect(jsonPath("$.messages[0].content").value("user-prompt"))
				.andRespond(withSuccess(
						"""
						{"content":[{"type":"text","text":"ok"}]}
						""",
						MediaType.APPLICATION_JSON));

		String rawContent = client.callWithText("system-prompt", "user-prompt");

		assertThat(rawContent).isEqualTo("ok");
		server.verify();
	}

}
