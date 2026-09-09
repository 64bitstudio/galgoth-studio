package com.galgothstudio.backend.aiorchestrator.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
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
import java.io.File;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Integración HTTP (Testcontainers + MockMvc) de `GenerationJobController`
 * (ticket 029) -- contrato real de `POST /api/mobs/{mobId}/generate`,
 * `GET /api/jobs/{jobId}/events` (SSE, incluyendo reanudación vía
 * `Last-Event-ID`, AC #3) y `POST /api/jobs/{jobId}/cancel`. Sin
 * `@Transactional`, misma razón que `MobGenerationServiceTest` -- el
 * pipeline corre en otro hilo con su propia transacción.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {"ai.vision-provider=mock", "ai.reasoning-provider=mock"})
class GenerationJobControllerTest {

	private static final byte[] TINY_PNG =
			Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private AssetStorageService assetStorageService;

	@Autowired
	private VisionModelProvider visionModelProvider;

	@Autowired
	private StructuredReasoningProvider reasoningProvider;

	@Autowired
	private AiJobRepository aiJobRepository;

	@Autowired
	private AiJobEventRepository aiJobEventRepository;

	private static final String VALID_OPERATIONS_JSON =
			"""
			[
			  {"op":"createBone","tempId":"root","name":"body","parentId":null,"pivot":[0,0,0],"rotation":[0,0,0]},
			  {"op":"createCuboid","tempId":"c1","name":"body","boneId":"root","from":[-4,0,-4],"to":[4,8,4],"origin":[0,4,0],"rotation":[0,0,0]}
			]
			""";

	@BeforeEach
	void resetMockProviders() throws Exception {
		((MockVisionProvider) visionModelProvider)
				.setNextResponse(Files.readString(new File("../contracts/fixtures/model-intent-example.json").toPath()));
		((MockVisionProvider) visionModelProvider).setOnCall(() -> {
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

	private UUID startGenerationAndExtractJobId(UUID mobId) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/mobs/{mobId}/generate", mobId)).andExpect(status().isAccepted()).andReturn();
		JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
		return UUID.fromString(node.get("jobId").asText());
	}

	private AiJobEntity awaitTerminalStatus(UUID jobId) {
		Awaitility.await()
				.atMost(Duration.ofSeconds(5))
				.pollInterval(Duration.ofMillis(25))
				.until(() -> !"running".equals(aiJobRepository.findById(jobId).orElseThrow().getStatus()));
		return aiJobRepository.findById(jobId).orElseThrow();
	}

	@Test
	void iniciar_generacion_devuelve_202_con_un_jobId_real_AC1() throws Exception {
		UUID mobId = aProjectAndMobWithReference();

		UUID jobId = startGenerationAndExtractJobId(mobId);

		awaitTerminalStatus(jobId); // deja terminar el pipeline real antes de que el test siguiente arranque
		assertThat(aiJobRepository.existsById(jobId)).isTrue();
	}

	@Test
	void iniciar_generacion_sobre_un_mob_inexistente_responde_404() throws Exception {
		mockMvc.perform(post("/api/mobs/{mobId}/generate", UUID.randomUUID())).andExpect(status().isNotFound());
	}

	@Test
	void eventos_de_un_job_inexistente_responde_404() throws Exception {
		mockMvc.perform(get("/api/jobs/{jobId}/events", UUID.randomUUID())).andExpect(status().isNotFound());
	}

	@Test
	void eventos_de_un_job_ya_completado_reproduce_el_backlog_completo_y_cierra_el_stream_AC1() throws Exception {
		UUID mobId = aProjectAndMobWithReference();
		UUID jobId = startGenerationAndExtractJobId(mobId);
		awaitTerminalStatus(jobId);

		MvcResult mvcResult = mockMvc.perform(get("/api/jobs/{jobId}/events", jobId)).andExpect(request().asyncStarted()).andReturn();

		mockMvc.perform(asyncDispatch(mvcResult))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
				.andExpect(content().string(containsString("event:progress")))
				.andExpect(content().string(containsString("\"stage\":\"analizando_referencia\"")))
				.andExpect(content().string(containsString("\"stage\":\"completado\"")));
	}

	@Test
	void reconectar_con_Last_Event_ID_solo_reproduce_eventos_posteriores_AC3() throws Exception {
		UUID mobId = aProjectAndMobWithReference();
		UUID jobId = startGenerationAndExtractJobId(mobId);
		awaitTerminalStatus(jobId);

		List<AiJobEventEntity> allEvents = aiJobEventRepository.findByJobIdAndSeqGreaterThanOrderBySeqAsc(jobId, 0);
		int firstSeq = allEvents.getFirst().getSeq();

		MvcResult mvcResult = mockMvc
				.perform(get("/api/jobs/{jobId}/events", jobId).header("Last-Event-ID", String.valueOf(firstSeq)))
				.andExpect(request().asyncStarted())
				.andReturn();

		mockMvc.perform(asyncDispatch(mvcResult))
				.andExpect(status().isOk())
				.andExpect(content().string(not(containsString("id:" + firstSeq + "\n"))))
				.andExpect(content().string(containsString("id:" + (firstSeq + 1))));
	}

	@Test
	void cancelar_un_job_inexistente_responde_404() throws Exception {
		mockMvc.perform(post("/api/jobs/{jobId}/cancel", UUID.randomUUID())).andExpect(status().isNotFound());
	}

	@Test
	void cancelar_un_job_ya_terminado_responde_409_AC4() throws Exception {
		UUID mobId = aProjectAndMobWithReference();
		UUID jobId = startGenerationAndExtractJobId(mobId);
		awaitTerminalStatus(jobId);

		mockMvc.perform(post("/api/jobs/{jobId}/cancel", jobId))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("INVALID_JOB_STATE"));
	}

}
