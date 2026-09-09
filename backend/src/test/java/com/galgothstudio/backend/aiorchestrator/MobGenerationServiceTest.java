package com.galgothstudio.backend.aiorchestrator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.galgothstudio.backend.TestcontainersConfiguration;
import com.galgothstudio.backend.aiorchestrator.persistence.AiJobEntity;
import com.galgothstudio.backend.aiorchestrator.persistence.AiJobEventEntity;
import com.galgothstudio.backend.aiorchestrator.persistence.AiJobEventRepository;
import com.galgothstudio.backend.aiorchestrator.persistence.AiJobRepository;
import com.galgothstudio.backend.aiorchestrator.provider.MockReasoningProvider;
import com.galgothstudio.backend.aiorchestrator.provider.MockVisionProvider;
import com.galgothstudio.backend.aiorchestrator.provider.StructuredReasoningProvider;
import com.galgothstudio.backend.aiorchestrator.provider.VisionModelProvider;
import com.galgothstudio.backend.asset.AssetStorageService;
import com.galgothstudio.backend.domain.model.MobProjectModel;
import com.galgothstudio.backend.project.draft.MobNotFoundException;
import java.io.File;
import java.nio.file.Files;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

/**
 * Integración de punta a punta (Testcontainers -- Postgres Y MinIO
 * reales -- ) de `MobGenerationService`, ticket 028/029. `ai.vision-provider`/
 * `ai.reasoning-provider=mock` (AC #2 del ticket 025: la suite
 * automatizada nunca llama a la API real de Anthropic).
 *
 * <p><b>Deliberadamente SIN `@Transactional`</b> -- a diferencia del
 * resto de los tests `@SpringBootTest` de este proyecto (ticket 029):
 * el pipeline real corre en OTRO hilo (`generationExecutor`), con su
 * propia conexión/transacción de BD -- si el setup de cada test
 * (`aProjectAndMobWithReference`) quedara dentro de una transacción de
 * test que nunca hace commit real (el patrón `@Transactional` estándar,
 * que hace rollback al final), ese hilo nunca vería los datos. Cada
 * escritura de setup (`jdbc.update`) y del propio pipeline ya comitea
 * por su cuenta -- las filas quedan reales en la BD entre tests (mismo
 * costo aceptado que cualquier test no-transaccional de este tipo), sin
 * necesidad de limpieza explícita porque cada test usa UUIDs propios.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = {"ai.vision-provider=mock", "ai.reasoning-provider=mock"})
class MobGenerationServiceTest {

	private static final String VALID_OPERATIONS_JSON =
			"""
			[
			  {"op":"createBone","tempId":"root","name":"body","parentId":null,"pivot":[0,0,0],"rotation":[0,0,0]},
			  {"op":"createCuboid","tempId":"c1","name":"body","boneId":"root","from":[-4,0,-4],"to":[4,8,4],"origin":[0,4,0],"rotation":[0,0,0]}
			]
			""";

	// PNG 1x1 real -- mismo fixture que MobThumbnailControllerTest/MobReferenceImageControllerTest.
	private static final byte[] TINY_PNG =
			Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");

	@Autowired
	private MobGenerationService mobGenerationService;

	@Autowired
	private VisionModelProvider visionModelProvider; // en realidad un MockVisionProvider, ver ai.vision-provider=mock arriba

	@Autowired
	private StructuredReasoningProvider reasoningProvider; // en realidad un MockReasoningProvider

	@Autowired
	private AssetStorageService assetStorageService;

	@Autowired
	private AiJobRepository aiJobRepository;

	@Autowired
	private AiJobEventRepository aiJobEventRepository;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private ObjectMapper objectMapper;

	/** Ver la nota de `MobGenerationServiceTest` (ticket 028) sobre por qué esto hace falta -- ahora además resetea el hook de {@code onCall} (ticket 029) para que el test de cancelación no afecte a los demás. */
	@BeforeEach
	void resetMockProviders() throws Exception {
		MockVisionProvider mockVision = (MockVisionProvider) visionModelProvider;
		mockVision.setNextResponse(Files.readString(new File("../contracts/fixtures/model-intent-example.json").toPath()));
		mockVision.setOnCall(() -> {
		});
		((MockReasoningProvider) reasoningProvider).setNextResponse(VALID_OPERATIONS_JSON);
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

	/** El pipeline real corre en `generationExecutor` (ticket 029) -- se sondea `ai_jobs` (cada `findById` es su propia lectura ya commiteada) en vez de un sleep fijo o un ejecutor síncrono especial de test. */
	private AiJobEntity awaitTerminalStatus(UUID jobId) {
		long deadline = System.currentTimeMillis() + 5000;
		while (System.currentTimeMillis() < deadline) {
			AiJobEntity job = aiJobRepository.findById(jobId).orElseThrow();
			if (!"running".equals(job.getStatus())) {
				return job;
			}
			sleep();
		}
		throw new AssertionError("El job " + jobId + " no alcanzó un estado terminal dentro del timeout.");
	}

	private static void sleep() {
		try {
			Thread.sleep(25);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(e);
		}
	}

	@Test
	void una_generacion_exitosa_aplica_la_geometria_y_persiste_un_ai_job_completed_con_sus_eventos_AC1_AC4() throws Exception {
		UUID mobId = aProjectAndMobWithReference();

		UUID jobId = mobGenerationService.startGeneration(mobId);
		AiJobEntity job = awaitTerminalStatus(jobId);

		assertThat(job.getStatus()).isEqualTo("completed");
		assertThat(job.getJobType()).isEqualTo("generate");
		assertThat(job.getProvider()).isEqualTo("mock");
		assertThat(job.getModel()).isEqualTo("mock-model");
		assertThat(job.getPromptVersion()).isNotBlank();
		assertThat(job.getSchemaVersion()).isNotBlank();
		assertThat(job.getBaseRevisionNumber()).isNull(); // job_type='generate' -- CHECK de ai_jobs (003)
		assertThat(job.getBaseDraftVersion()).isNull();
		assertThat(job.getProposalJson()).isNotBlank();
		assertThat(job.getFinishedAt()).isNotNull();

		// El proposal_jsonb persistido debe ser el MISMO modelo devuelto (030 lo consume tal cual).
		MobProjectModel persistedProposal = objectMapper.readValue(job.getProposalJson(), MobProjectModel.class);
		assertThat(persistedProposal.cuboids()).hasSize(1);

		List<AiJobEventEntity> events = aiJobEventRepository.findByJobIdAndSeqGreaterThanOrderBySeqAsc(jobId, 0);
		assertThat(events).isNotEmpty();
		assertThat(events.getFirst().getStage()).isEqualTo("analizando_referencia");
		assertThat(events.getLast().getStage()).isEqualTo("completado");
		assertThat(events.getLast().getProgressPct()).isEqualTo(100);
		for (int i = 0; i < events.size(); i++) {
			assertThat(events.get(i).getSeq()).isEqualTo(i + 1); // seq estrictamente secuencial desde 1, sin huecos
		}
		// `JsonNode.toString()` (payload_jsonb tal como se persiste) espacia
		// distinto que `ObjectMapper.writeValueAsString`, ej. `"type": "x"`
		// no `"type":"x"` -- se busca solo el valor, no el layout exacto.
		assertThat(events).anySatisfy(e -> assertThat(e.getPayloadJson()).contains("preview_operations"));
		assertThat(events.getLast().getPayloadJson()).contains("preview_snapshot");
	}

	@Test
	void un_ModelIntent_invalido_detiene_el_flujo_y_persiste_un_ai_job_failed_AC1() {
		((MockVisionProvider) visionModelProvider).setNextResponse("{\"silhouette\": \"incompleto\"}");
		UUID mobId = aProjectAndMobWithReference();

		UUID jobId = mobGenerationService.startGeneration(mobId);
		AiJobEntity job = awaitTerminalStatus(jobId);

		assertThat(job.getStatus()).isEqualTo("failed");
		assertThat(job.getError()).isNotBlank();

		List<AiJobEventEntity> events = aiJobEventRepository.findByJobIdAndSeqGreaterThanOrderBySeqAsc(jobId, 0);
		assertThat(events.getLast().getStage()).isEqualTo("fallido");
	}

	@Test
	void una_propuesta_de_geometria_invalida_detiene_el_flujo_y_persiste_un_ai_job_failed_AC2() {
		((MockReasoningProvider) reasoningProvider).setNextResponse("[{\"op\":\"opQueNoExiste\"}]");
		UUID mobId = aProjectAndMobWithReference();

		UUID jobId = mobGenerationService.startGeneration(mobId);
		AiJobEntity job = awaitTerminalStatus(jobId);

		assertThat(job.getStatus()).isEqualTo("failed");
		List<AiJobEventEntity> events = aiJobEventRepository.findByJobIdAndSeqGreaterThanOrderBySeqAsc(jobId, 0);
		assertThat(events.getLast().getStage()).isEqualTo("fallido");
	}

	@Test
	void un_mob_sin_ninguna_imagen_de_referencia_falla_explicito_sin_crear_ningun_job_ni_intentar_llamadas() {
		UUID projectId = UUID.randomUUID();
		jdbc.update("insert into projects (id, name) values (?, ?)", projectId, "Galgoth");
		UUID mobId = UUID.randomUUID();
		jdbc.update(
				"insert into mobs (id, project_id, name, base_type, status) values (?, ?, ?, ?, ?)",
				mobId, projectId, "SinReferencia", "humanoid", "draft");

		assertThatThrownBy(() -> mobGenerationService.startGeneration(mobId)).isInstanceOf(NoReferenceImageException.class);

		Long jobCount = jdbc.queryForObject("select count(*) from ai_jobs where mob_id = ?", Long.class, mobId);
		assertThat(jobCount).isZero();
	}

	@Test
	void un_mob_inexistente_responde_MobNotFoundException() {
		assertThatThrownBy(() -> mobGenerationService.startGeneration(UUID.randomUUID())).isInstanceOf(MobNotFoundException.class);
	}

	/**
	 * Ticket 029, AC #4: cancela un job en pleno vuelo -- usa el hook de
	 * `MockVisionProvider.setOnCall` para bloquear el pipeline (que corre
	 * en OTRO hilo, `generationExecutor`) exactamente en el punto de
	 * control posterior a la llamada de visión, mientras el hilo del
	 * test dispara `requestCancellation` sobre el mismo job -- sin este
	 * hook no habría forma determinista (sin adivinar tiempos) de
	 * "atrapar" el pipeline a mitad de camino.
	 */
	@Test
	void cancelar_un_job_en_curso_lo_marca_cancelled_y_descarta_el_preview_AC4() throws InterruptedException {
		CountDownLatch visionCalled = new CountDownLatch(1);
		CountDownLatch testReadyToProceed = new CountDownLatch(1);
		((MockVisionProvider) visionModelProvider).setOnCall(() -> {
			visionCalled.countDown();
			try {
				testReadyToProceed.await(5, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		});

		UUID mobId = aProjectAndMobWithReference();
		UUID jobId = mobGenerationService.startGeneration(mobId);

		assertThat(visionCalled.await(2, TimeUnit.SECONDS)).as("el pipeline debe haber llegado a la llamada de visión").isTrue();
		mobGenerationService.requestCancellation(jobId);
		testReadyToProceed.countDown();

		AiJobEntity job = awaitTerminalStatus(jobId);

		assertThat(job.getStatus()).isEqualTo("cancelled");
		assertThat(job.getFinishedAt()).isNotNull();
		assertThat(job.getProposalJson()).isNull(); // ningún draft/revisión, ningún preview persistido más allá del log de eventos descartable

		List<AiJobEventEntity> events = aiJobEventRepository.findByJobIdAndSeqGreaterThanOrderBySeqAsc(jobId, 0);
		assertThat(events.getLast().getStage()).isEqualTo("cancelado");
	}

	@Test
	void cancelar_un_job_que_ya_terminó_falla_explícito_AC4() {
		UUID mobId = aProjectAndMobWithReference();
		UUID jobId = mobGenerationService.startGeneration(mobId);
		awaitTerminalStatus(jobId);

		assertThatThrownBy(() -> mobGenerationService.requestCancellation(jobId)).isInstanceOf(InvalidJobStateException.class);
	}

	@Test
	void cancelar_un_job_inexistente_responde_JobNotFoundException() {
		assertThatThrownBy(() -> mobGenerationService.requestCancellation(UUID.randomUUID())).isInstanceOf(JobNotFoundException.class);
	}

}
