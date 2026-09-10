package com.galgothstudio.backend.aiorchestrator;

import static org.assertj.core.api.Assertions.assertThat;

import com.galgothstudio.backend.TestcontainersConfiguration;
import com.galgothstudio.backend.aiorchestrator.persistence.AiJobEntity;
import com.galgothstudio.backend.aiorchestrator.persistence.AiJobEventEntity;
import com.galgothstudio.backend.aiorchestrator.persistence.AiJobEventRepository;
import com.galgothstudio.backend.aiorchestrator.persistence.AiJobRepository;
import com.galgothstudio.backend.aiorchestrator.provider.AiProviderException;
import com.galgothstudio.backend.aiorchestrator.provider.AiProviderResponse;
import com.galgothstudio.backend.aiorchestrator.provider.MockVisionProvider;
import com.galgothstudio.backend.aiorchestrator.provider.ReasoningRequest;
import com.galgothstudio.backend.aiorchestrator.provider.StructuredReasoningProvider;
import com.galgothstudio.backend.aiorchestrator.provider.VisionModelProvider;
import com.galgothstudio.backend.asset.AssetStorageService;
import java.io.File;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import org.awaitility.Awaitility;
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
 * Ticket 038 -- modo streaming (switch encendido, default) de
 * {@link MobGenerationService}. Usa un {@link FakeStreamingReasoningProvider}
 * dedicado (en vez de {@code MockReasoningProvider}, que resuelve en un
 * solo delta) para poder controlar EXACTAMENTE cuándo llega cada
 * operación real -- necesario para reproducir de forma determinista
 * "error a mitad de stream" y "cancelación a mitad de stream" en el
 * punto exacto que pide el ticket (`generando_cuboides`/64%).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(
		properties = {
				"ai.vision-provider=mock", "ai.reasoning-provider=mock", "ai.geometry-streaming-enabled=true",
				// Acelerado SOLO para el test de heartbeat-durante-streaming --
				// mismo criterio que MobGenerationServiceHeartbeatTest.
				"ai.geometry-heartbeat-initial-delay-seconds=1", "ai.geometry-heartbeat-period-seconds=1"
		})
class MobGenerationServiceStreamingTest {

	// PNG 1x1 real -- mismo fixture que MobGenerationServiceTest.
	private static final byte[] TINY_PNG =
			Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");

	@org.springframework.boot.test.context.TestConfiguration
	static class FakeStreamingReasoningProviderConfig {

		@Bean
		@Primary
		FakeStreamingReasoningProvider fakeStreamingReasoningProvider() {
			return new FakeStreamingReasoningProvider();
		}

	}

	/**
	 * Entrega cada {@code GeometryOperation} de {@link #operationJsons} como
	 * SU PROPIO delta (`reasonStreaming`) -- a diferencia de
	 * `MockReasoningProvider`/el default de la interfaz, que entregan el
	 * contenido completo en un solo fragmento. `failAtIndex`/`onBeforeIndex` permiten a cada
	 * test interceptar el stream EXACTAMENTE en la operación que necesita
	 * (falla real del proveedor, o disparar `requestCancellation` desde
	 * dentro del mismo hilo síncrono del pipeline).
	 */
	static class FakeStreamingReasoningProvider implements StructuredReasoningProvider {

		private List<String> operationJsons = List.of();
		private int failAtIndex = -1;
		private IntConsumer onBeforeIndex = i -> { };
		private long delayBeforeFirstDeltaMillis = 0;

		void configure(List<String> operationJsons, int failAtIndex, IntConsumer onBeforeIndex) {
			this.operationJsons = operationJsons;
			this.failAtIndex = failAtIndex;
			this.onBeforeIndex = onBeforeIndex;
		}

		/** Ticket 038 -- simula el "pensamiento" real de Claude (`thinking_delta`, sin contenido) antes de emitir el primer token real. */
		void delayFirstDeltaBy(long millis) {
			this.delayBeforeFirstDeltaMillis = millis;
		}

		@Override
		public AiProviderResponse reason(ReasoningRequest request) {
			String joined = "[" + String.join(",", operationJsons) + "]";
			return new AiProviderResponse(joined, "fake-streaming", "fake-model", request.promptVersion(), request.schemaVersion());
		}

		@Override
		public AiProviderResponse reasonStreaming(ReasoningRequest request, Consumer<String> onTextDelta) {
			if (delayBeforeFirstDeltaMillis > 0) {
				try {
					Thread.sleep(delayBeforeFirstDeltaMillis); // NOSONAR -- deliberado, ver delayFirstDeltaBy().
				} catch (InterruptedException _) {
					Thread.currentThread().interrupt();
				}
			}
			for (int i = 0; i < operationJsons.size(); i++) {
				onBeforeIndex.accept(i);
				if (i == failAtIndex) {
					throw new AiProviderException("Fallo simulado de proveedor a mitad de stream (test).");
				}
				onTextDelta.accept(operationJsons.get(i));
			}
			// `failAtIndex == operationJsons.size()`: falla DESPUÉS de emitir
			// todas las operaciones reales configuradas -- simula un proveedor
			// que se cae justo cuando iba a seguir generando más (nunca llegó a
			// devolver la respuesta completa), distinto de fallar A MITAD del
			// batch (`failAtIndex < size`, cubierto arriba).
			if (failAtIndex == operationJsons.size()) {
				throw new AiProviderException("Fallo simulado de proveedor a mitad de stream (test).");
			}
			return reason(request);
		}

	}

	@Autowired
	private MobGenerationService mobGenerationService;

	@Autowired
	private VisionModelProvider visionModelProvider; // en realidad un MockVisionProvider

	@Autowired
	private FakeStreamingReasoningProvider reasoningProvider;

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
		reasoningProvider.configure(List.of(), -1, i -> { });
		reasoningProvider.delayFirstDeltaBy(0);
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
		Awaitility.await()
				.atMost(Duration.ofSeconds(5))
				.pollInterval(Duration.ofMillis(25))
				.until(() -> !"running".equals(aiJobRepository.findById(jobId).orElseThrow().getStatus()));
		return aiJobRepository.findById(jobId).orElseThrow();
	}

	/** Un bone raíz + `cuboidCount` cuboids válidos, todos colgando del mismo bone -- fixture reutilizado por varios tests de este archivo. */
	private static List<String> boneAndCuboids(int cuboidCount) {
		List<String> ops = new ArrayList<>();
		ops.add("{\"op\":\"createBone\",\"tempId\":\"root\",\"name\":\"root\",\"parentId\":null,\"pivot\":[0,0,0],\"rotation\":[0,0,0]}");
		for (int i = 0; i < cuboidCount; i++) {
			ops.add(
					"{\"op\":\"createCuboid\",\"tempId\":\"c" + i + "\",\"name\":\"part" + i
							+ "\",\"boneId\":\"root\",\"from\":[0,0,0],\"to\":[2,2,2],\"origin\":[1,1,1],\"rotation\":[0,0,0]}");
		}
		return ops;
	}

	@Test
	void las_2_etapas_nuevas_aparecen_como_eventos_reales_antes_de_completado_AC_ticket_038() {
		reasoningProvider.configure(boneAndCuboids(3), -1, i -> { });
		UUID mobId = aProjectAndMobWithReference();

		UUID jobId = mobGenerationService.startGeneration(mobId);
		AiJobEntity job = awaitTerminalStatus(jobId);

		assertThat(job.getStatus()).isEqualTo("completed");
		List<AiJobEventEntity> events = aiJobEventRepository.findByJobIdAndSeqGreaterThanOrderBySeqAsc(jobId, 0);
		List<String> stages = events.stream().map(AiJobEventEntity::getStage).toList();

		assertThat(stages).contains("preparando_resultado", "validando_geometria");
		// Orden real de emisión (ver MobGenerationService.runPipeline): preparando_resultado
		// (aplica UV final) sucede ANTES de validando_geometria (corre FMM sobre
		// el modelo YA con UV) -- el frontend debe listar las etapas en este
		// mismo orden para que "current"/"done" avance monótonamente.
		assertThat(stages.indexOf("preparando_resultado")).isLessThan(stages.indexOf("validando_geometria"));
		assertThat(stages.indexOf("validando_geometria")).isLessThan(stages.indexOf("completado"));
	}

	@Test
	void cada_operacion_real_del_stream_dispara_su_propio_evento_con_progreso_creciente_no_un_burst_post_hoc() {
		reasoningProvider.configure(boneAndCuboids(5), -1, i -> { });
		UUID mobId = aProjectAndMobWithReference();

		UUID jobId = mobGenerationService.startGeneration(mobId);
		awaitTerminalStatus(jobId);

		List<AiJobEventEntity> events = aiJobEventRepository.findByJobIdAndSeqGreaterThanOrderBySeqAsc(jobId, 0);
		List<AiJobEventEntity> cuboidEvents = events.stream().filter(e -> "generando_cuboides".equals(e.getStage())).toList();

		assertThat(cuboidEvents).hasSize(5); // una por cada createCuboid real del fixture, no un solo evento agregado
		for (int i = 1; i < cuboidEvents.size(); i++) {
			assertThat(cuboidEvents.get(i).getProgressPct()).isGreaterThan(cuboidEvents.get(i - 1).getProgressPct());
		}
	}

	@Test
	void un_fallo_del_proveedor_a_mitad_de_stream_en_generando_cuboides_64_por_ciento_deja_el_job_fallido() {
		// 1 bone + 23 cuboids reales -> progressPct tras el último = min(89, 40+24) = 64.
		List<String> ops = boneAndCuboids(23);
		reasoningProvider.configure(ops, ops.size(), i -> { }); // falla al INTENTAR la operación #24 (índice == ops.size(), nunca llega)
		UUID mobId = aProjectAndMobWithReference();

		UUID jobId = mobGenerationService.startGeneration(mobId);
		AiJobEntity job = awaitTerminalStatus(jobId);

		assertThat(job.getStatus()).isEqualTo("failed");
		List<AiJobEventEntity> events = aiJobEventRepository.findByJobIdAndSeqGreaterThanOrderBySeqAsc(jobId, 0);
		assertThat(events.getLast().getStage()).isEqualTo("fallido");

		AiJobEventEntity lastRealProgress = events.get(events.size() - 2); // el evento real justo antes de "fallido"
		assertThat(lastRealProgress.getStage()).isEqualTo("generando_cuboides");
		assertThat(lastRealProgress.getProgressPct()).isEqualTo(64);
	}

	@Test
	void cancelar_a_mitad_de_stream_en_generando_cuboides_deja_el_job_cancelado_sin_esperar_al_proximo_punto_de_control_de_alto_nivel() {
		List<String> ops = boneAndCuboids(10);
		UUID mobId = aProjectAndMobWithReference();
		UUID[] jobIdBox = new UUID[1];
		// Cancela DESDE DENTRO del mismo hilo síncrono del pipeline, justo antes
		// de la operación #6 (índice 6, ya en generando_cuboides) -- streaming
		// real convierte cada operación en un punto de control nuevo, a
		// diferencia del modo heartbeat (que solo podía cancelar entre etapas).
		reasoningProvider.configure(ops, -1, i -> {
			if (i == 6) {
				mobGenerationService.requestCancellation(jobIdBox[0]);
			}
		});

		jobIdBox[0] = mobGenerationService.startGeneration(mobId);
		AiJobEntity job = awaitTerminalStatus(jobIdBox[0]);

		assertThat(job.getStatus()).as("error=" + job.getError()).isEqualTo("cancelled");
		assertThat(job.getProposalJson()).isNull();
		List<AiJobEventEntity> events = aiJobEventRepository.findByJobIdAndSeqGreaterThanOrderBySeqAsc(jobIdBox[0], 0);
		assertThat(events.getLast().getStage()).isEqualTo("cancelado");
		// Se cortó ANTES de llegar a preparando_resultado/completado -- la
		// cancelación de verdad interrumpió el stream, no dejó que terminara.
		assertThat(events).noneMatch(e -> "completado".equals(e.getStage()));
	}

	@Test
	void ticket_038_hallazgo_real_incluso_en_modo_streaming_el_heartbeat_hace_ping_mientras_claude_todavia_no_emitio_ningun_token_real() {
		// Hallazgo real de la verificación en vivo (job cb867de9, 2026-09-10):
		// Claude puede pasar bastante tiempo en razonamiento extendido antes
		// de emitir el primer token de contenido real -- streaming solo
		// ayuda una vez que ese contenido empieza a llegar. 2.5s de "silencio"
		// simulado, ping cada 1s -> al menos 2 pings reales antes de la
		// primera operación real.
		reasoningProvider.delayFirstDeltaBy(2500);
		reasoningProvider.configure(boneAndCuboids(2), -1, i -> { });
		UUID mobId = aProjectAndMobWithReference();

		UUID jobId = mobGenerationService.startGeneration(mobId);
		AiJobEntity job = awaitTerminalStatus(jobId);

		assertThat(job.getStatus()).isEqualTo("completed");
		List<AiJobEventEntity> events = aiJobEventRepository.findByJobIdAndSeqGreaterThanOrderBySeqAsc(jobId, 0);
		List<AiJobEventEntity> heartbeatPings =
				events.stream().filter(e -> "detectando_silueta".equals(e.getStage()) && e.getMessage().contains("llevamos")).toList();

		assertThat(heartbeatPings).as("al menos 2 pings reales mientras Claude no emitió ningún token real todavía").hasSizeGreaterThanOrEqualTo(2);
		// Apenas llega la primera operación real, el heartbeat se apaga -- no sigue haciendo ping sobre eventos reales ya en curso.
		AiJobEventEntity lastHeartbeat = heartbeatPings.getLast();
		AiJobEventEntity firstRealCuboidEvent = events.stream().filter(e -> "creando_rig".equals(e.getStage())).findFirst().orElseThrow();
		assertThat(lastHeartbeat.getSeq()).isLessThan(firstRealCuboidEvent.getSeq());
	}

}
