package com.galgothstudio.backend.aiorchestrator;

import static org.assertj.core.api.Assertions.assertThat;

import com.galgothstudio.backend.TestcontainersConfiguration;
import com.galgothstudio.backend.aiorchestrator.persistence.AiJobEntity;
import com.galgothstudio.backend.aiorchestrator.persistence.AiJobEventEntity;
import com.galgothstudio.backend.aiorchestrator.persistence.AiJobEventRepository;
import com.galgothstudio.backend.aiorchestrator.persistence.AiJobRepository;
import com.galgothstudio.backend.aiorchestrator.provider.AiProviderResponse;
import com.galgothstudio.backend.aiorchestrator.provider.MockVisionProvider;
import com.galgothstudio.backend.aiorchestrator.provider.ReasoningRequest;
import com.galgothstudio.backend.aiorchestrator.provider.StructuredReasoningProvider;
import com.galgothstudio.backend.aiorchestrator.provider.VisionModelProvider;
import com.galgothstudio.backend.asset.AssetStorageService;
import java.io.File;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

/**
 * Ticket 038 -- modo heartbeat (switch operativo `AI_GEOMETRY_STREAMING_ENABLED=false`,
 * la vía de escape si el streaming real empieza a fallar mucho en
 * producción): la llamada al Geometry Planner sigue siendo bloqueante
 * como siempre, pero mientras espera emite un ping periódico HONESTO
 * (mismo stage `detectando_silueta`, mismo 25% -- nunca inventa avance)
 * con el tiempo real transcurrido.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(
		properties = {
				"ai.vision-provider=mock", "ai.reasoning-provider=mock", "ai.geometry-streaming-enabled=false",
				// Acelerado SOLO para este test -- 8s reales harían la suite
				// lenta sin aportar nada más de señal; 1s alcanza para
				// verificar que el heartbeat dispara de verdad mientras la
				// llamada bloqueante está en curso.
				"ai.geometry-heartbeat-initial-delay-seconds=1", "ai.geometry-heartbeat-period-seconds=1"
		})
class MobGenerationServiceHeartbeatTest {

	private static final byte[] TINY_PNG =
			Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");

	private static final String ONE_BONE_ONE_CUBOID_JSON =
			"""
			[
			  {"op":"createBone","tempId":"root","name":"root","parentId":null,"pivot":[0,0,0],"rotation":[0,0,0]},
			  {"op":"createCuboid","tempId":"c0","name":"part0","boneId":"root","from":[0,0,0],"to":[2,2,2],"origin":[1,1,1],"rotation":[0,0,0]}
			]
			""";

	@org.springframework.boot.test.context.TestConfiguration
	static class SlowReasoningProviderConfig {

		@Bean
		@Primary
		SlowReasoningProvider slowReasoningProvider() {
			return new SlowReasoningProvider();
		}

	}

	/** Bloquea `reason()` el tiempo configurado -- simula la llamada real de 70-90s al Geometry Planner sin depender de la red ni de un sleep gigante en el test (usa milisegundos, suficientes para que el heartbeat dispare varias veces con un período corto solo-de-test). */
	static class SlowReasoningProvider implements StructuredReasoningProvider {

		private long blockMillis = 0;

		void blockFor(long millis) {
			this.blockMillis = millis;
		}

		@Override
		public AiProviderResponse reason(ReasoningRequest request) {
			try {
				Thread.sleep(blockMillis);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			return new AiProviderResponse(ONE_BONE_ONE_CUBOID_JSON, "fake-slow", "fake-model", request.promptVersion(), request.schemaVersion());
		}

	}

	@Autowired
	private MobGenerationService mobGenerationService;

	@Autowired
	private VisionModelProvider visionModelProvider;

	@Autowired
	private SlowReasoningProvider slowReasoningProvider;

	@Autowired
	private AssetStorageService assetStorageService;

	@Autowired
	private AiJobRepository aiJobRepository;

	@Autowired
	private AiJobEventRepository aiJobEventRepository;

	@Autowired
	private JdbcTemplate jdbc;

	@BeforeEach
	void resetProviders() throws Exception {
		((MockVisionProvider) visionModelProvider).setNextResponse(Files.readString(new File("../contracts/fixtures/model-intent-example.json").toPath()));
		((MockVisionProvider) visionModelProvider).setOnCall(() -> { });
		slowReasoningProvider.blockFor(0);
	}

	private UUID aProjectAndMobWithReference() {
		UUID projectId = UUID.randomUUID();
		jdbc.update("insert into projects (id, name) values (?, ?)", projectId, "Galgoth");
		UUID mobId = UUID.randomUUID();
		jdbc.update(
				"insert into mobs (id, project_id, name, base_type, status) values (?, ?, ?, ?, ?)",
				mobId, projectId, "Carcomido", "humanoid", "draft");
		String storageKey = "mobs/" + mobId + "/references/test.png";
		assetStorageService.put(storageKey, TINY_PNG, "image/png");
		jdbc.update(
				"insert into reference_images (id, mob_id, storage_key, width, height, content_type) values (?, ?, ?, ?, ?, ?)",
				UUID.randomUUID(), mobId, storageKey, 1, 1, "image/png");
		return mobId;
	}

	private AiJobEntity awaitTerminalStatus(UUID jobId) {
		org.awaitility.Awaitility.await()
				.atMost(Duration.ofSeconds(15))
				.pollInterval(Duration.ofMillis(50))
				.until(() -> !"running".equals(aiJobRepository.findById(jobId).orElseThrow().getStatus()));
		return aiJobRepository.findById(jobId).orElseThrow();
	}

	@Test
	void con_el_switch_apagado_la_llamada_bloqueante_sigue_llegando_a_completado_igual_que_antes() {
		slowReasoningProvider.blockFor(50);
		UUID mobId = aProjectAndMobWithReference();

		UUID jobId = mobGenerationService.startGeneration(mobId);
		AiJobEntity job = awaitTerminalStatus(jobId);

		assertThat(job.getStatus()).isEqualTo("completed");
		List<AiJobEventEntity> events = aiJobEventRepository.findByJobIdAndSeqGreaterThanOrderBySeqAsc(jobId, 0);
		assertThat(events.getLast().getStage()).isEqualTo("completado");
		assertThat(events).anySatisfy(e -> assertThat(e.getStage()).isEqualTo("preparando_resultado"));
		assertThat(events).anySatisfy(e -> assertThat(e.getStage()).isEqualTo("validando_geometria"));
	}

	@Test
	void mientras_la_llamada_bloqueante_esta_en_curso_el_heartbeat_emite_pings_honestos_sin_inventar_avance() {
		// 2.5s de bloqueo, ping cada 1s -> al menos 2 pings reales antes de
		// que la llamada "real" (el mock lento) devuelva algo.
		slowReasoningProvider.blockFor(2500);
		UUID mobId = aProjectAndMobWithReference();

		UUID jobId = mobGenerationService.startGeneration(mobId);
		AiJobEntity job = awaitTerminalStatus(jobId);

		assertThat(job.getStatus()).isEqualTo("completed");
		List<AiJobEventEntity> events = aiJobEventRepository.findByJobIdAndSeqGreaterThanOrderBySeqAsc(jobId, 0);
		List<AiJobEventEntity> heartbeatPings =
				events.stream().filter(e -> "detectando_silueta".equals(e.getStage()) && e.getMessage().contains("llevamos")).toList();

		assertThat(heartbeatPings).as("al menos 2 pings reales durante los 2.5s de espera bloqueante").hasSizeGreaterThanOrEqualTo(2);
		// Honesto: NUNCA cambia de stage ni de % mientras hace ping -- solo el mensaje.
		assertThat(heartbeatPings).allSatisfy(e -> assertThat(e.getProgressPct()).isEqualTo(25));
		// Mensajes distintos entre sí (el tiempo transcurrido realmente avanza, no es el mismo texto repetido).
		assertThat(heartbeatPings.stream().map(AiJobEventEntity::getMessage).distinct().count()).isGreaterThanOrEqualTo(2);
	}

}
