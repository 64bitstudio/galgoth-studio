package com.galgothstudio.backend.aiorchestrator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.galgothstudio.backend.TestcontainersConfiguration;
import com.galgothstudio.backend.aiorchestrator.persistence.AiJobEntity;
import com.galgothstudio.backend.aiorchestrator.persistence.AiJobRepository;
import com.galgothstudio.backend.aiorchestrator.planner.InvalidGeometryProposalException;
import com.galgothstudio.backend.aiorchestrator.provider.MockReasoningProvider;
import com.galgothstudio.backend.aiorchestrator.provider.MockVisionProvider;
import com.galgothstudio.backend.aiorchestrator.provider.StructuredReasoningProvider;
import com.galgothstudio.backend.aiorchestrator.provider.VisionModelProvider;
import com.galgothstudio.backend.aiorchestrator.vision.InvalidModelIntentException;
import com.galgothstudio.backend.asset.AssetStorageService;
import com.galgothstudio.backend.domain.model.MobProjectModel;
import com.galgothstudio.backend.project.draft.MobNotFoundException;
import jakarta.persistence.EntityManager;
import java.io.File;
import java.nio.file.Files;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integración de punta a punta (Testcontainers -- Postgres Y MinIO
 * reales -- ) de `MobGenerationService`, ticket 028. `ai.vision-provider`/
 * `ai.reasoning-provider=mock` (AC #2 del ticket 025: la suite
 * automatizada nunca llama a la API real de Anthropic) -- `AiProviderConfig`
 * expone las mismas instancias `MockVisionProvider`/`MockReasoningProvider`
 * que este test autowirea y configura antes de ejercitar el servicio.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = {"ai.vision-provider=mock", "ai.reasoning-provider=mock"})
@Transactional
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
	private JdbcTemplate jdbc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private EntityManager entityManager;

	/**
	 * `MockVisionProvider`/`MockReasoningProvider` son beans Spring
	 * SINGLETON en este `@SpringBootTest` -- su `nextResponse` mutable
	 * sobrevive entre métodos `@Test` de esta clase (confirmado real: un
	 * test que configura una respuesta inválida hacía fallar el
	 * siguiente test, que nunca la reconfiguró). Se resetea a valores
	 * válidos conocidos antes de CADA test, para que cada uno controle
	 * explícitamente solo lo que le importa.
	 */
	@BeforeEach
	void resetMockProviders() throws Exception {
		((MockVisionProvider) visionModelProvider)
				.setNextResponse(Files.readString(new File("../contracts/fixtures/model-intent-example.json").toPath()));
		((MockReasoningProvider) reasoningProvider).setNextResponse(VALID_OPERATIONS_JSON);
	}

	/** Ver la nota en {@link com.galgothstudio.backend.project.api.MobDraftControllerTest} (ticket 020) sobre por qué esto hace falta antes de leer vía JDBC crudo dentro de la misma transacción de test. */
	private void flush() {
		entityManager.flush();
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

	@Test
	void una_generacion_exitosa_aplica_la_geometria_y_persiste_un_ai_job_completed_AC4() throws Exception {
		((MockReasoningProvider) reasoningProvider).setNextResponse(VALID_OPERATIONS_JSON);
		UUID mobId = aProjectAndMobWithReference();

		GenerationResult result = mobGenerationService.generate(mobId);

		assertThat(result.model().bones()).hasSize(1);
		assertThat(result.model().cuboids()).hasSize(1);

		AiJobEntity job = aiJobRepository.findById(result.jobId()).orElseThrow();
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
	}

	@Test
	void un_ModelIntent_invalido_detiene_el_flujo_y_persiste_un_ai_job_failed_AC1() {
		((MockVisionProvider) visionModelProvider).setNextResponse("{\"silhouette\": \"incompleto\"}");
		UUID mobId = aProjectAndMobWithReference();

		assertThatThrownBy(() -> mobGenerationService.generate(mobId)).isInstanceOf(InvalidModelIntentException.class);

		flush();
		JsonNode row = objectMapper.valueToTree(
				jdbc.queryForMap("select status, error from ai_jobs where mob_id = ?", mobId));
		assertThat(row.get("status").asText()).isEqualTo("failed");
		assertThat(row.get("error").asText()).isNotBlank();
	}

	@Test
	void una_propuesta_de_geometria_invalida_detiene_el_flujo_y_persiste_un_ai_job_failed_AC2() {
		((MockReasoningProvider) reasoningProvider).setNextResponse("[{\"op\":\"opQueNoExiste\"}]");
		UUID mobId = aProjectAndMobWithReference();

		assertThatThrownBy(() -> mobGenerationService.generate(mobId)).isInstanceOf(InvalidGeometryProposalException.class);

		flush();
		Long failedCount = jdbc.queryForObject("select count(*) from ai_jobs where mob_id = ? and status = 'failed'", Long.class, mobId);
		assertThat(failedCount).isEqualTo(1L);
	}

	@Test
	void un_mob_sin_ninguna_imagen_de_referencia_falla_explicito_sin_intentar_ninguna_llamada() {
		UUID projectId = UUID.randomUUID();
		jdbc.update("insert into projects (id, name) values (?, ?)", projectId, "Galgoth");
		UUID mobId = UUID.randomUUID();
		jdbc.update(
				"insert into mobs (id, project_id, name, base_type, status) values (?, ?, ?, ?, ?)",
				mobId, projectId, "SinReferencia", "humanoid", "draft");

		assertThatThrownBy(() -> mobGenerationService.generate(mobId)).isInstanceOf(NoReferenceImageException.class);

		Long jobCount = jdbc.queryForObject("select count(*) from ai_jobs where mob_id = ?", Long.class, mobId);
		assertThat(jobCount).isZero();
	}

	@Test
	void un_mob_inexistente_responde_MobNotFoundException() {
		assertThatThrownBy(() -> mobGenerationService.generate(UUID.randomUUID())).isInstanceOf(MobNotFoundException.class);
	}

}
