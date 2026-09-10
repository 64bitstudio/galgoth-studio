package com.galgothstudio.backend.aiorchestrator.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Selección de proveedor de IA por variable de entorno (AC del ticket
 * 025, master prompt §20): `AI_VISION_PROVIDER`/`AI_REASONING_PROVIDER`
 * (relaxed binding de Spring las mapea a `ai.vision-provider`/
 * `ai.reasoning-provider`) eligen entre Claude (default) y Mock --
 * ningún código de `ai-orchestrator` ni del dominio depende de cuál esté
 * activo, solo inyecta la interfaz ({@link VisionModelProvider}/
 * {@link StructuredReasoningProvider}).
 *
 * **Hallazgo real de wiring, por qué `ClaudeVisionProvider`/
 * `ClaudeReasoningProvider`/`MockVisionProvider`/`MockReasoningProvider`
 * son clases SEPARADAS por interfaz** (en vez de una sola clase
 * implementando ambas, como un primer intento de este ticket): Spring
 * resuelve `getBean(Interfaz.class)`/`@Autowired Interfaz` inspeccionando
 * el TIPO REAL de cualquier singleton ya instanciado, no solo el tipo
 * declarado del método `@Bean` que lo creó -- así que una única clase
 * `ClaudeProvider implements VisionModelProvider, StructuredReasoningProvider`,
 * expuesta bajo cada interfaz vía dos `@Bean` distintos, seguía siendo
 * candidata de AMBAS interfaces sin importar cuál bean la expuso
 * (confirmado real: con las dos propiedades en "mock",
 * `getBean(VisionModelProvider.class)` encontraba tanto el bean de
 * vision como el de reasoning, porque la MISMA instancia satisfacía las
 * dos). Ni `@Primary` en las 4 fábricas lo resolvía (dos `@Primary`
 * genuinos terminaban compitiendo entre sí). La única solución real es
 * que cada interfaz la implemente una clase que NO implementa ninguna
 * otra interfaz de proveedor -- de ahí `ClaudeMessagesClient` (mecánica
 * HTTP compartida, sin interfaz propia) + un wrapper delgado por
 * interfaz.
 */
@Configuration
public class AiProviderConfig {

	@Bean
	public ClaudeMessagesClient claudeMessagesClient(
			RestClient.Builder restClientBuilder,
			ObjectMapper objectMapper,
			@Value("${ai.claude.base-url}") String baseUrl,
			@Value("${ai.claude.api-key:}") String apiKey,
			@Value("${ai.claude.model}") String model) {
		return new ClaudeMessagesClient(restClientBuilder, objectMapper, baseUrl, apiKey, model);
	}

	@Bean
	@ConditionalOnProperty(name = "ai.vision-provider", havingValue = "claude", matchIfMissing = true)
	public VisionModelProvider visionModelProvider(ClaudeMessagesClient claudeMessagesClient) {
		return new ClaudeVisionProvider(claudeMessagesClient);
	}

	@Bean
	@ConditionalOnProperty(name = "ai.vision-provider", havingValue = "mock")
	public VisionModelProvider mockVisionModelProvider() {
		return new MockVisionProvider();
	}

	@Bean
	@ConditionalOnProperty(name = "ai.reasoning-provider", havingValue = "claude", matchIfMissing = true)
	public StructuredReasoningProvider structuredReasoningProvider(ClaudeMessagesClient claudeMessagesClient) {
		return new ClaudeReasoningProvider(claudeMessagesClient);
	}

	@Bean
	@ConditionalOnProperty(name = "ai.reasoning-provider", havingValue = "mock")
	public StructuredReasoningProvider mockStructuredReasoningProvider() {
		return new MockReasoningProvider();
	}

	/** Ticket 051, mismo mecanismo que vision/reasoning arriba: `AI_IMAGE_PROVIDER` (relaxed binding -&gt; `ai.image-provider`) elige entre OpenAI (default) y Mock. `ImageGenerationProvider` es una interfaz distinta de `VisionModelProvider`/`StructuredReasoningProvider` y `OpenAiImageProvider`/`MockImageProvider` no implementan ninguna de esas otras dos -- el hallazgo de ambigüedad documentado arriba no aplica acá. */
	@Bean
	@ConditionalOnProperty(name = "ai.image-provider", havingValue = "openai", matchIfMissing = true)
	public ImageGenerationProvider openAiImageProvider(
			RestClient.Builder restClientBuilder,
			ObjectMapper objectMapper,
			@Value("${ai.openai.base-url}") String baseUrl,
			@Value("${ai.openai.api-key:}") String apiKey,
			@Value("${ai.openai.image-model}") String model) {
		return new OpenAiImageProvider(restClientBuilder, objectMapper, baseUrl, apiKey, model);
	}

	@Bean
	@ConditionalOnProperty(name = "ai.image-provider", havingValue = "mock")
	public ImageGenerationProvider mockImageProvider() {
		return new MockImageProvider();
	}

}
