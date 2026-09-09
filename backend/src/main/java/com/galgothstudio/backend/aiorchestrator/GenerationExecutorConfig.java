package com.galgothstudio.backend.aiorchestrator;

import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Ejecutor del pipeline de generación asíncrono (ticket 029) -- un
 * `ThreadPoolTaskExecutor` real, tanto en producción como en tests
 * (`MobGenerationServiceTest` espera la finalización sondeando
 * `ai_jobs`/`ai_job_events` con un timeout acotado, en vez de un
 * ejecutor síncrono especial de test -- más fiel al comportamiento real,
 * y necesario de todas formas para el test de cancelación, que necesita
 * que el pipeline siga corriendo en OTRO hilo mientras el test dispara
 * la cancelación desde el suyo).
 */
@Configuration
public class GenerationExecutorConfig {

	@Bean
	@Qualifier("generationExecutor")
	public Executor generationExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(2);
		executor.setMaxPoolSize(4);
		executor.setQueueCapacity(50);
		executor.setThreadNamePrefix("ai-generation-");
		executor.initialize();
		return executor;
	}

}
