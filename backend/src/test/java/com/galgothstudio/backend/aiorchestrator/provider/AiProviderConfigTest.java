package com.galgothstudio.backend.aiorchestrator.provider;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * AC #3 del ticket 025: cambiar de proveedor por variable de entorno no
 * requiere tocar código -- `ApplicationContextRunner` (Spring Boot Test,
 * sin dependencia nueva) arma un contexto mínimo real por cada
 * combinación de propiedades y verifica qué implementación concreta
 * queda expuesta bajo cada interfaz.
 */
class AiProviderConfigTest {

	/** `AiProviderConfig` necesita un `RestClient.Builder`/`ObjectMapper` inyectables -- en la app real los aporta Spring Boot (autoconfiguración web + `JacksonConfig`, ticket 020); acá se suplen mínimos, ninguno de estos tests ejercita una llamada HTTP real. */
	@Configuration
	static class MinimalInfrastructureBeans {

		@Bean
		RestClient.Builder restClientBuilder() {
			return RestClient.builder();
		}

		@Bean
		ObjectMapper objectMapper() {
			return new ObjectMapper();
		}
	}

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withUserConfiguration(MinimalInfrastructureBeans.class, AiProviderConfig.class)
			.withPropertyValues(
					"ai.claude.base-url=https://api.anthropic.com",
					"ai.claude.model=claude-sonnet-5",
					"ai.openai.base-url=https://api.openai.com",
					"ai.openai.image-model=gpt-image-2.5-sunburst-2026-09-08");

	@Test
	void sin_configurar_nada_el_default_es_Claude_para_vision_y_reasoning() {
		contextRunner.run(context -> {
			assertThat(context).hasSingleBean(VisionModelProvider.class);
			assertThat(context).hasSingleBean(StructuredReasoningProvider.class);
			assertThat(context.getBean(VisionModelProvider.class)).isInstanceOf(ClaudeVisionProvider.class);
			assertThat(context.getBean(StructuredReasoningProvider.class)).isInstanceOf(ClaudeReasoningProvider.class);
		});
	}

	@Test
	void ai_vision_provider_mock_expone_MockVisionProvider_como_VisionModelProvider() {
		contextRunner.withPropertyValues("ai.vision-provider=mock").run(context -> {
			assertThat(context.getBean(VisionModelProvider.class)).isInstanceOf(MockVisionProvider.class);
			// AC #3: cambiar SOLO el proveedor de visión no afecta el de razonamiento.
			assertThat(context.getBean(StructuredReasoningProvider.class)).isInstanceOf(ClaudeReasoningProvider.class);
		});
	}

	@Test
	void ai_reasoning_provider_mock_expone_MockReasoningProvider_como_StructuredReasoningProvider() {
		contextRunner.withPropertyValues("ai.reasoning-provider=mock").run(context -> {
			assertThat(context.getBean(StructuredReasoningProvider.class)).isInstanceOf(MockReasoningProvider.class);
			assertThat(context.getBean(VisionModelProvider.class)).isInstanceOf(ClaudeVisionProvider.class);
		});
	}

	@Test
	void ambos_proveedores_pueden_apuntar_a_mock_simultaneamente() {
		contextRunner.withPropertyValues("ai.vision-provider=mock", "ai.reasoning-provider=mock").run(context -> {
			assertThat(context.getBean(VisionModelProvider.class)).isInstanceOf(MockVisionProvider.class);
			assertThat(context.getBean(StructuredReasoningProvider.class)).isInstanceOf(MockReasoningProvider.class);
		});
	}

	/** Ticket 051 -- mismo AC de selección por variable de entorno que vision/reasoning, ahora para `ImageGenerationProvider`. */
	@Test
	void sin_configurar_nada_el_default_de_imagen_es_OpenAi() {
		contextRunner.run(context -> {
			assertThat(context).hasSingleBean(ImageGenerationProvider.class);
			assertThat(context.getBean(ImageGenerationProvider.class)).isInstanceOf(OpenAiImageProvider.class);
		});
	}

	@Test
	void ai_image_provider_mock_expone_MockImageProvider_como_ImageGenerationProvider() {
		contextRunner.withPropertyValues("ai.image-provider=mock").run(context -> {
			assertThat(context.getBean(ImageGenerationProvider.class)).isInstanceOf(MockImageProvider.class);
			// AC #3 (mismo criterio que vision/reasoning): cambiar SOLO el proveedor de imagen no afecta los otros dos.
			assertThat(context.getBean(VisionModelProvider.class)).isInstanceOf(ClaudeVisionProvider.class);
			assertThat(context.getBean(StructuredReasoningProvider.class)).isInstanceOf(ClaudeReasoningProvider.class);
		});
	}

}
