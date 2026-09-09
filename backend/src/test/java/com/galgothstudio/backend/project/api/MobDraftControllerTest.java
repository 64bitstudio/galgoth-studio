package com.galgothstudio.backend.project.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.galgothstudio.backend.TestcontainersConfiguration;
import com.galgothstudio.backend.domain.model.MobProjectModel;
import jakarta.persistence.EntityManager;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integración de punta a punta (Testcontainers + Postgres real + MockMvc)
 * de los 3 endpoints del ticket 020, uno por AC. Mismo patrón
 * transaccional (rollback automático) que {@link
 * com.galgothstudio.backend.SchemaConstraintsTest} del ticket 003.
 *
 * VoBo explícito del Product Owner (2026-09-08): este ticket es
 * backend-only -- el harness de desarrollo (ticket 008) sigue cargando
 * el fixture estático como hoy; el Gate M2 de la épica 015 se demuestra
 * a nivel de API aquí, y se termina de conectar a una pantalla real
 * cuando el ticket 021 (CRUD de mobs) exista.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MobDraftControllerTest {

	private static final File FIXTURE_FILE = new File("../contracts/fixtures/model-spec-example.json");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private EntityManager entityManager;

	/**
	 * `@Transactional` en la clase de test hace que TODA la petición HTTP
	 * (controller -> service -> Hibernate) participe de la MISMA
	 * transacción de nivel test -- Hibernate por diseño difiere sus
	 * `INSERT`/`UPDATE` hasta el flush, y una transacción "participante"
	 * (no la que abrió realmente la conexión) no fuerza ese flush al
	 * terminar. `jdbc` (JDBC crudo, fuera de la sesión de Hibernate) por
	 * lo tanto NO ve los cambios recién guardados por el repositorio
	 * hasta que se pide un flush explícito -- solo un artefacto de este
	 * test (mezclar JPA + JDBC crudo dentro de una transacción que nunca
	 * comitea de verdad), no un bug de producción: en un request real, el
	 * `@Transactional` del servicio SÍ es la transacción más externa y
	 * Spring flushea/comitea al terminar.
	 */
	private void flush() {
		entityManager.flush();
	}

	private UUID aProject() {
		UUID id = UUID.randomUUID();
		jdbc.update("insert into projects (id, name) values (?, ?)", id, "Galgoth");
		return id;
	}

	private UUID aMob(UUID projectId) {
		UUID id = UUID.randomUUID();
		jdbc.update(
				"insert into mobs (id, project_id, name, base_type, status) values (?, ?, ?, ?, ?)",
				id, projectId, "Carcomido", "humanoid", "draft");
		return id;
	}

	private String fixtureJson() throws IOException {
		return Files.readString(FIXTURE_FILE.toPath());
	}

	/** Misma fixture real, con un campo cambiado -- para producir un modelo materialmente distinto sin reescribir el record a mano. */
	private String fixtureJsonWithName(String newName) throws IOException {
		ObjectNode node = (ObjectNode) objectMapper.readTree(fixtureJson());
		node.put("name", newName);
		return objectMapper.writeValueAsString(node);
	}

	/** La misma fixture, pero con `texture` explícitamente null -- el schema lo requiere como objeto, así que esto SIEMPRE falla la validación (AC #6). */
	private String fixtureJsonWithoutTexture() throws IOException {
		ObjectNode node = (ObjectNode) objectMapper.readTree(fixtureJson());
		node.putNull("texture");
		return objectMapper.writeValueAsString(node);
	}

	private String draftRequestBody(String modelJson) {
		return "{\"model\":" + modelJson + "}";
	}

	// -- GET /draft --------------------------------------------------------

	@Test
	void getDraft_de_un_mob_sin_draft_responde_404_explicito() throws Exception {
		UUID mobId = aMob(aProject());

		mockMvc.perform(get("/api/mobs/{mobId}/draft", mobId))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error", is("DRAFT_NOT_FOUND")));
	}

	@Test
	void getDraft_de_un_mob_inexistente_responde_404_mob_not_found() throws Exception {
		mockMvc.perform(get("/api/mobs/{mobId}/draft", UUID.randomUUID()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error", is("MOB_NOT_FOUND")));
	}

	@Test
	void getDraft_tras_un_autosave_refleja_el_mismo_draftVersion_y_modelo() throws Exception {
		UUID mobId = aMob(aProject());
		mockMvc.perform(patch("/api/mobs/{mobId}/draft", mobId)
						.contentType(MediaType.APPLICATION_JSON)
						.content(draftRequestBody(fixtureJson())))
				.andExpect(status().isOk());

		MvcResult result = mockMvc.perform(get("/api/mobs/{mobId}/draft", mobId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.draftVersion", is(1)))
				.andReturn();

		JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
		MobProjectModel persisted = objectMapper.treeToValue(body.get("model"), MobProjectModel.class);
		MobProjectModel original = objectMapper.readValue(fixtureJson(), MobProjectModel.class);
		assertThat(persisted).isEqualTo(original);
	}

	// -- PATCH /draft (autosave) --------------------------------------------

	@Test
	void autosave_con_contenido_nuevo_incrementa_draftVersion_en_1_AC1() throws Exception {
		UUID mobId = aMob(aProject());
		mockMvc.perform(patch("/api/mobs/{mobId}/draft", mobId)
						.contentType(MediaType.APPLICATION_JSON)
						.content(draftRequestBody(fixtureJsonWithName("Carcomido_v1"))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.changed", is(true)))
				.andExpect(jsonPath("$.draftVersion", is(1)));

		mockMvc.perform(patch("/api/mobs/{mobId}/draft", mobId)
						.contentType(MediaType.APPLICATION_JSON)
						.content(draftRequestBody(fixtureJsonWithName("Carcomido_v2"))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.changed", is(true)))
				.andExpect(jsonPath("$.draftVersion", is(2)));
	}

	@Test
	void autosave_con_contenido_identico_no_escribe_nada_ni_incrementa_draftVersion_AC2() throws Exception {
		UUID mobId = aMob(aProject());
		String modelJson = fixtureJsonWithName("Carcomido_estable");

		mockMvc.perform(patch("/api/mobs/{mobId}/draft", mobId)
				.contentType(MediaType.APPLICATION_JSON)
				.content(draftRequestBody(modelJson)));
		flush();
		String updatedAtAfterFirst =
				jdbc.queryForObject("select updated_at::text from mob_drafts where mob_id = ?", String.class, mobId);

		mockMvc.perform(patch("/api/mobs/{mobId}/draft", mobId)
						.contentType(MediaType.APPLICATION_JSON)
						.content(draftRequestBody(modelJson)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.changed", is(false)))
				.andExpect(jsonPath("$.draftVersion", is(1)));

		flush();
		String updatedAtAfterSecond =
				jdbc.queryForObject("select updated_at::text from mob_drafts where mob_id = ?", String.class, mobId);
		assertThat(updatedAtAfterSecond).isEqualTo(updatedAtAfterFirst); // no se escribió nada
	}

	@Test
	void autosave_de_un_mob_inexistente_responde_404() throws Exception {
		mockMvc.perform(patch("/api/mobs/{mobId}/draft", UUID.randomUUID())
						.contentType(MediaType.APPLICATION_JSON)
						.content(draftRequestBody(fixtureJson())))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error", is("MOB_NOT_FOUND")));
	}

	// -- POST /revisions (Guardar) -------------------------------------------

	@Test
	void guardar_crea_la_primera_revision_y_actualiza_current_revision_number_AC4() throws Exception {
		UUID mobId = aMob(aProject());

		mockMvc.perform(post("/api/mobs/{mobId}/revisions", mobId)
						.contentType(MediaType.APPLICATION_JSON)
						.content(draftRequestBody(fixtureJson())))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.created", is(true)))
				.andExpect(jsonPath("$.revisionNumber", is(1)));

		flush();
		Integer currentRevisionNumber =
				jdbc.queryForObject("select current_revision_number from mobs where id = ?", Integer.class, mobId);
		assertThat(currentRevisionNumber).isEqualTo(1);
		Integer revisionCount = jdbc.queryForObject(
				"select count(*) from mob_revisions where mob_id = ?", Integer.class, mobId);
		assertThat(revisionCount).isEqualTo(1);
	}

	@Test
	void guardar_sin_cambios_desde_la_ultima_revision_no_duplica_AC5() throws Exception {
		UUID mobId = aMob(aProject());
		String modelJson = fixtureJson();
		mockMvc.perform(post("/api/mobs/{mobId}/revisions", mobId)
				.contentType(MediaType.APPLICATION_JSON)
				.content(draftRequestBody(modelJson)));

		mockMvc.perform(post("/api/mobs/{mobId}/revisions", mobId)
						.contentType(MediaType.APPLICATION_JSON)
						.content(draftRequestBody(modelJson)))
				.andExpect(status().isOk()) // no 201 -- no se creó nada nuevo
				.andExpect(jsonPath("$.created", is(false)))
				.andExpect(jsonPath("$.revisionNumber", is(1)));

		flush();
		Integer revisionCount = jdbc.queryForObject(
				"select count(*) from mob_revisions where mob_id = ?", Integer.class, mobId);
		assertThat(revisionCount).isEqualTo(1); // sin duplicado
	}

	@Test
	void guardar_con_cambios_reales_crea_la_revision_2_tras_la_1() throws Exception {
		UUID mobId = aMob(aProject());
		mockMvc.perform(post("/api/mobs/{mobId}/revisions", mobId)
				.contentType(MediaType.APPLICATION_JSON)
				.content(draftRequestBody(fixtureJsonWithName("Carcomido_r1"))));

		mockMvc.perform(post("/api/mobs/{mobId}/revisions", mobId)
						.contentType(MediaType.APPLICATION_JSON)
						.content(draftRequestBody(fixtureJsonWithName("Carcomido_r2"))))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.created", is(true)))
				.andExpect(jsonPath("$.revisionNumber", is(2)));
	}

	@Test
	void guardar_con_un_modelo_invalido_responde_400_y_no_crea_revision_AC6() throws Exception {
		UUID mobId = aMob(aProject());

		mockMvc.perform(post("/api/mobs/{mobId}/revisions", mobId)
						.contentType(MediaType.APPLICATION_JSON)
						.content(draftRequestBody(fixtureJsonWithoutTexture())))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error", is("INVALID_DRAFT")))
				.andExpect(jsonPath("$.details").isNotEmpty());

		flush();
		Integer currentRevisionNumber =
				jdbc.queryForObject("select current_revision_number from mobs where id = ?", Integer.class, mobId);
		assertThat(currentRevisionNumber).isZero();
		Integer revisionCount = jdbc.queryForObject(
				"select count(*) from mob_revisions where mob_id = ?", Integer.class, mobId);
		assertThat(revisionCount).isZero();
	}

	@Test
	void guardar_de_un_mob_inexistente_responde_404() throws Exception {
		mockMvc.perform(post("/api/mobs/{mobId}/revisions", UUID.randomUUID())
						.contentType(MediaType.APPLICATION_JSON)
						.content(draftRequestBody(fixtureJson())))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error", is("MOB_NOT_FOUND")));
	}

}
