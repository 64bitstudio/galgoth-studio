package com.galgothstudio.backend.aiorchestrator.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.galgothstudio.backend.TestcontainersConfiguration;
import com.galgothstudio.backend.aiorchestrator.provider.MockReasoningProvider;
import com.galgothstudio.backend.aiorchestrator.provider.StructuredReasoningProvider;
import jakarta.persistence.EntityManager;
import java.util.UUID;
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
import org.springframework.transaction.annotation.Transactional;

/**
 * Integración de punta a punta (Testcontainers + Postgres real + MockMvc)
 * de `AiEditController` (ticket 031) -- a diferencia de
 * `GenerationJobControllerTest`/`MobGenerationServiceTest` (029), acá SÍ
 * se puede usar `@Transactional` de test normal: `AiEditService` es
 * síncrono, sin ningún pipeline corriendo en otro hilo.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {"ai.vision-provider=mock", "ai.reasoning-provider=mock"})
@Transactional
class AiEditControllerTest {

	private static final String VALID_EDIT_PLAN =
			"""
			{"summary":"Increase both hands","operations":[
			  {"op":"resizeCuboid","target":"hand_right","scale":[1.2,1.2,1.2]},
			  {"op":"resizeCuboid","target":"hand_left","scale":[1.2,1.2,1.2]}
			]}
			""";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private StructuredReasoningProvider reasoningProvider;

	@BeforeEach
	void resetMockProvider() {
		((MockReasoningProvider) reasoningProvider).setNextResponse(VALID_EDIT_PLAN);
	}

	private void flush() {
		entityManager.flush();
	}

	private UUID aProjectAndMob() {
		UUID projectId = UUID.randomUUID();
		jdbc.update("insert into projects (id, name) values (?, ?)", projectId, "Galgoth");
		UUID mobId = UUID.randomUUID();
		jdbc.update(
				"insert into mobs (id, project_id, name, base_type, status) values (?, ?, ?, ?, ?)",
				mobId, projectId, "Carcomido", "humanoid", "draft");
		return mobId;
	}

	private String modelWithHands(UUID mobId, UUID projectId) {
		return """
				{
				  "mobId": "%s", "projectId": "%s", "name": "Carcomido", "baseType": "humanoid", "units": "minecraft_pixels",
				  "bones": [{"id":"body","name":"body","parentId":null,"pivot":[0,0,0],"rotation":[0,0,0]}],
				  "cuboids": [
				    {"id":"hand_right","name":"hand_right","boneId":"body","from":[0,0,0],"to":[2,2,2],"origin":[0,0,0],"rotation":[0,0,0],
				     "faces":{"north":{"uv":[0,0,0,0],"texture":null},"south":{"uv":[0,0,0,0],"texture":null},"east":{"uv":[0,0,0,0],"texture":null},"west":{"uv":[0,0,0,0],"texture":null},"up":{"uv":[0,0,0,0],"texture":null},"down":{"uv":[0,0,0,0],"texture":null}}},
				    {"id":"hand_left","name":"hand_left","boneId":"body","from":[0,0,0],"to":[2,2,2],"origin":[0,0,0],"rotation":[0,0,0],
				     "faces":{"north":{"uv":[0,0,0,0],"texture":null},"south":{"uv":[0,0,0,0],"texture":null},"east":{"uv":[0,0,0,0],"texture":null},"west":{"uv":[0,0,0,0],"texture":null},"up":{"uv":[0,0,0,0],"texture":null},"down":{"uv":[0,0,0,0],"texture":null}}}
				  ],
				  "texture": {"width":64,"height":64,"storageKey":null},
				  "uv": {"textureWidth":64,"textureHeight":64,"regions":[]},
				  "animations": [], "exportSettings": {"preferredFormatVersion":"v5"}, "referenceImages": []
				}
				"""
				.formatted(mobId, projectId);
	}

	/**
	 * Deja el mob con un draft Y una revisión real (via el autosave y
	 * "Guardar" reales de 020) conteniendo `hand_right`/`hand_left` -- los
	 * mismos ids que `MockReasoningProvider` referencia por defecto.
	 * Ambas llamadas hacen falta: "Guardar" deliberadamente NO toca
	 * `mob_drafts` (ver `DraftPersistenceService`, 020), y editar por IA
	 * exige `mobs.current_revision_number > 0` (`fk_ai_jobs_base_revision`,
	 * hallazgo real de este ticket -- ver `NoBaseRevisionException`).
	 */
	private void seedDraft(UUID mobId, UUID projectId) throws Exception {
		mockMvc.perform(
						patch("/api/mobs/{mobId}/draft", mobId).contentType(MediaType.APPLICATION_JSON).content(
								"{\"model\":" + modelWithHands(mobId, projectId) + "}"))
				.andExpect(status().isOk());
		mockMvc.perform(
						post("/api/mobs/{mobId}/revisions", mobId).contentType(MediaType.APPLICATION_JSON).content(
								"{\"model\":" + modelWithHands(mobId, projectId) + "}"))
				.andExpect(status().isCreated());
	}

	private UUID requestPlanAndExtractJobId(UUID mobId) throws Exception {
		MvcResult result = mockMvc
				.perform(
						post("/api/mobs/{mobId}/ai/edit-geometry", mobId)
								.contentType(MediaType.APPLICATION_JSON)
								.content("{\"instruction\":\"Haz las manos más grandes\"}"))
				.andExpect(status().isOk())
				.andReturn();
		JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
		return UUID.fromString(node.get("jobId").asText());
	}

	@Test
	void pedir_un_plan_devuelve_before_after_y_los_elementos_cambiados_sin_tocar_draft_ni_revision_AC1_AC2() throws Exception {
		UUID mobId = aProjectAndMob();
		UUID projectId = UUID.fromString(jdbc.queryForObject("select project_id from mobs where id = ?", String.class, mobId));
		seedDraft(mobId, projectId);

		mockMvc.perform(
						post("/api/mobs/{mobId}/ai/edit-geometry", mobId)
								.contentType(MediaType.APPLICATION_JSON)
								.content("{\"instruction\":\"Haz las manos más grandes\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.summary", is("Increase both hands")))
				.andExpect(jsonPath("$.beforeCuboidCount", is(2)))
				.andExpect(jsonPath("$.afterCuboidCount", is(2)))
				.andExpect(jsonPath("$.changedElements", hasSize(2)))
				.andExpect(jsonPath("$.changedElements[0].changeKind", is("modified")));

		flush();
		Integer draftVersion = jdbc.queryForObject("select draft_version from mob_drafts where mob_id = ?", Integer.class, mobId);
		assertThat(draftVersion).isEqualTo(1); // el autosave del seed, NUNCA avanzó por pedir el plan
		Long revisionCount = jdbc.queryForObject("select count(*) from mob_revisions where mob_id = ?", Long.class, mobId);
		assertThat(revisionCount).isEqualTo(1L); // solo la revisión del seed ("Guardar") -- pedir el plan no creó ninguna nueva
	}

	/**
	 * Hallazgo real de este ticket: `fk_ai_jobs_base_revision` (003) exige
	 * que `base_revision_number` apunte a una fila REAL de `mob_revisions`
	 * -- un mob con draft pero SIN ninguna revisión guardada todavía
	 * (`current_revision_number=0`) rompería esa FK si se intentara
	 * persistir el job igual. `NoBaseRevisionException` lo rechaza ANTES
	 * de gastar una llamada real a la IA.
	 */
	@Test
	void pedir_un_plan_sobre_un_mob_con_draft_pero_sin_ninguna_revision_guardada_responde_400() throws Exception {
		UUID mobId = aProjectAndMob();
		UUID projectId = UUID.fromString(jdbc.queryForObject("select project_id from mobs where id = ?", String.class, mobId));
		mockMvc.perform(
						patch("/api/mobs/{mobId}/draft", mobId).contentType(MediaType.APPLICATION_JSON).content(
								"{\"model\":" + modelWithHands(mobId, projectId) + "}"))
				.andExpect(status().isOk()); // draft SIN ningún "Guardar"/"Usar este modelo" todavía

		mockMvc.perform(
						post("/api/mobs/{mobId}/ai/edit-geometry", mobId)
								.contentType(MediaType.APPLICATION_JSON)
								.content("{\"instruction\":\"cualquiera\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error", is("NO_BASE_REVISION")));
	}

	@Test
	void pedir_un_plan_sobre_un_mob_completamente_nuevo_responde_400_NO_BASE_REVISION() throws Exception {
		UUID mobId = aProjectAndMob(); // sin draft NI revisión -- el chequeo de revisión corre primero (ver NoBaseRevisionException)

		mockMvc.perform(
						post("/api/mobs/{mobId}/ai/edit-geometry", mobId)
								.contentType(MediaType.APPLICATION_JSON)
								.content("{\"instruction\":\"cualquiera\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error", is("NO_BASE_REVISION")));
	}

	/** Caso borde real: `saveRevision` (020) valida/persiste el modelo del BODY de la petición, nunca lee `mob_drafts` -- es posible tener una revisión real sin ningún draft (si nunca se autosaveó). */
	@Test
	void pedir_un_plan_con_revision_pero_sin_ningun_draft_responde_404_DRAFT_NOT_FOUND() throws Exception {
		UUID mobId = aProjectAndMob();
		UUID projectId = UUID.fromString(jdbc.queryForObject("select project_id from mobs where id = ?", String.class, mobId));
		mockMvc.perform(
						post("/api/mobs/{mobId}/revisions", mobId).contentType(MediaType.APPLICATION_JSON).content(
								"{\"model\":" + modelWithHands(mobId, projectId) + "}"))
				.andExpect(status().isCreated()); // "Guardar" directo, sin autosave previo -- current_revision_number=1, mob_drafts sigue vacío

		mockMvc.perform(
						post("/api/mobs/{mobId}/ai/edit-geometry", mobId)
								.contentType(MediaType.APPLICATION_JSON)
								.content("{\"instruction\":\"cualquiera\"}"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error", is("DRAFT_NOT_FOUND")));
	}

	@Test
	void una_propuesta_invalida_de_la_IA_responde_400_sin_persistir_ningun_cambio() throws Exception {
		UUID mobId = aProjectAndMob();
		UUID projectId = UUID.fromString(jdbc.queryForObject("select project_id from mobs where id = ?", String.class, mobId));
		seedDraft(mobId, projectId);
		((MockReasoningProvider) reasoningProvider).setNextResponse("{\"summary\":\"x\",\"operations\":[{\"op\":\"noExiste\"}]}");

		mockMvc.perform(
						post("/api/mobs/{mobId}/ai/edit-geometry", mobId)
								.contentType(MediaType.APPLICATION_JSON)
								.content("{\"instruction\":\"cualquiera\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error", is("INVALID_EDIT_PROPOSAL")));

		flush();
		Integer draftVersion = jdbc.queryForObject("select draft_version from mob_drafts where mob_id = ?", Integer.class, mobId);
		assertThat(draftVersion).isEqualTo(1); // AC #6: el modelo real queda sin cambios
		Long jobCount = jdbc.queryForObject("select count(*) from ai_jobs where mob_id = ? and status = 'failed'", Long.class, mobId);
		assertThat(jobCount).isEqualTo(1L); // el intento SÍ queda registrado, para reproducibilidad (master prompt §20)
	}

	@Test
	void aplicar_un_plan_crea_revision_y_draft_avanzados_AC3_AC4() throws Exception {
		UUID mobId = aProjectAndMob();
		UUID projectId = UUID.fromString(jdbc.queryForObject("select project_id from mobs where id = ?", String.class, mobId));
		seedDraft(mobId, projectId);
		UUID jobId = requestPlanAndExtractJobId(mobId);

		mockMvc.perform(post("/api/jobs/{jobId}/apply-edit", jobId))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.revisionNumber", is(2))) // seedDraft ya dejó la revisión 1 ("Guardar") -- esta es la SIGUIENTE, creada por IA
				.andExpect(jsonPath("$.draftVersion", is(2)));

		flush();
		Long revisionCount = jdbc.queryForObject(
				"select count(*) from mob_revisions where mob_id = ? and revision_number = 2 and created_by = 'ai'", Long.class, mobId);
		assertThat(revisionCount).isEqualTo(1L);
		Integer draftVersion = jdbc.queryForObject("select draft_version from mob_drafts where mob_id = ?", Integer.class, mobId);
		assertThat(draftVersion).isEqualTo(2);
	}

	@Test
	void aplicar_un_plan_cuyo_draft_base_ya_avanzo_responde_409_sin_aplicar_nada_AC5() throws Exception {
		UUID mobId = aProjectAndMob();
		UUID projectId = UUID.fromString(jdbc.queryForObject("select project_id from mobs where id = ?", String.class, mobId));
		seedDraft(mobId, projectId);
		UUID jobId = requestPlanAndExtractJobId(mobId);

		// El draft avanza (otro autosave real) DESPUÉS de generar el plan -- el job quedó con una base ahora vieja.
		mockMvc.perform(
						patch("/api/mobs/{mobId}/draft", mobId)
								.contentType(MediaType.APPLICATION_JSON)
								.content("{\"model\":" + modelWithHands(mobId, projectId).replace("\"Carcomido\"", "\"Carcomido Editado\"") + "}"))
				.andExpect(status().isOk());

		mockMvc.perform(post("/api/jobs/{jobId}/apply-edit", jobId))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error", is("STALE_EDIT_BASE")));

		flush();
		Long revisionCount = jdbc.queryForObject("select count(*) from mob_revisions where mob_id = ?", Long.class, mobId);
		assertThat(revisionCount).isEqualTo(1L); // AC #5: solo la revisión del seed -- no se aplicó ninguna operación nueva
	}

	@Test
	void aplicar_un_job_inexistente_responde_404() throws Exception {
		mockMvc.perform(post("/api/jobs/{jobId}/apply-edit", UUID.randomUUID())).andExpect(status().isNotFound());
	}

	@Test
	void rechazar_no_toca_nada_simplemente_no_se_llama_a_apply() throws Exception {
		UUID mobId = aProjectAndMob();
		UUID projectId = UUID.fromString(jdbc.queryForObject("select project_id from mobs where id = ?", String.class, mobId));
		seedDraft(mobId, projectId);

		requestPlanAndExtractJobId(mobId); // "Reject" en la UI real == nunca llamar a /apply-edit

		flush();
		Long revisionCount = jdbc.queryForObject("select count(*) from mob_revisions where mob_id = ?", Long.class, mobId);
		assertThat(revisionCount).isEqualTo(1L); // solo la revisión del seed -- pedir/no-aplicar un plan nunca crea una nueva
		Integer draftVersion = jdbc.queryForObject("select draft_version from mob_drafts where mob_id = ?", Integer.class, mobId);
		assertThat(draftVersion).isEqualTo(1);
	}

}
