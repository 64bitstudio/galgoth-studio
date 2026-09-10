package com.galgothstudio.backend.project.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.galgothstudio.backend.TestcontainersConfiguration;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integración de punta a punta (Testcontainers + Postgres real + MockMvc)
 * del CRUD de mobs, ticket 022 -- mismo patrón que {@link ProjectControllerTest}
 * (ticket 021) y {@link MobDraftControllerTest} (ticket 020).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MobControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private EntityManager entityManager;

	/** Ver la nota en {@link MobDraftControllerTest} (ticket 020) sobre por qué esto hace falta antes de leer vía JDBC crudo dentro de la misma transacción de test. */
	private void flush() {
		entityManager.flush();
	}

	private UUID aProject() {
		UUID id = UUID.randomUUID();
		jdbc.update("insert into projects (id, name) values (?, ?)", id, "Galgoth");
		return id;
	}

	private String createBody(String name, String baseType) {
		return "{\"name\":\"" + name + "\",\"baseType\":\"" + baseType + "\"}";
	}

	@Test
	void crear_un_mob_lo_deja_en_estado_draft_sin_revision_ni_draft_persistido_AC2() throws Exception {
		UUID projectId = aProject();

		mockMvc.perform(post("/api/projects/{projectId}/mobs", projectId)
						.contentType(MediaType.APPLICATION_JSON)
						.content(createBody("Carcomido", "humanoid")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.name", is("Carcomido")))
				.andExpect(jsonPath("$.baseType", is("humanoid")))
				.andExpect(jsonPath("$.status", is("draft")));

		flush();
		Integer currentRevisionNumber = jdbc.queryForObject(
				"select current_revision_number from mobs where project_id = ?", Integer.class, projectId);
		assertThat(currentRevisionNumber).isZero();
		Integer draftCount = jdbc.queryForObject(
				"select count(*) from mob_drafts d join mobs m on m.id = d.mob_id where m.project_id = ?",
				Integer.class, projectId);
		assertThat(draftCount).isZero(); // sin fila en mob_drafts -- no existe hasta el primer commit
	}

	@Test
	void crear_con_nombre_vacio_es_rechazado() throws Exception {
		UUID projectId = aProject();

		mockMvc.perform(post("/api/projects/{projectId}/mobs", projectId)
						.contentType(MediaType.APPLICATION_JSON)
						.content(createBody("", "humanoid")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error", is("INVALID_MOB_REQUEST")));
	}

	@Test
	void crear_con_baseType_invalido_es_rechazado() throws Exception {
		UUID projectId = aProject();

		mockMvc.perform(post("/api/projects/{projectId}/mobs", projectId)
						.contentType(MediaType.APPLICATION_JSON)
						.content(createBody("Algo", "dragon")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error", is("INVALID_MOB_REQUEST")));
	}

	@Test
	void crear_en_un_proyecto_inexistente_responde_404() throws Exception {
		mockMvc.perform(post("/api/projects/{projectId}/mobs", UUID.randomUUID())
						.contentType(MediaType.APPLICATION_JSON)
						.content(createBody("Carcomido", "humanoid")))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error", is("PROJECT_NOT_FOUND")));
	}

	@Test
	void listar_devuelve_todos_los_mobs_del_proyecto_para_el_grid_AC4() throws Exception {
		UUID projectId = aProject();
		mockMvc.perform(post("/api/projects/{projectId}/mobs", projectId)
				.contentType(MediaType.APPLICATION_JSON)
				.content(createBody("Carcomido", "humanoid")));
		mockMvc.perform(post("/api/projects/{projectId}/mobs", projectId)
				.contentType(MediaType.APPLICATION_JSON)
				.content(createBody("Augur", "flying")));

		mockMvc.perform(get("/api/projects/{projectId}/mobs", projectId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(2)))
				.andExpect(jsonPath("$[0].status", is("draft")))
				.andExpect(jsonPath("$[1].status", is("draft")));
	}

	@Test
	void listar_de_un_proyecto_inexistente_responde_404() throws Exception {
		mockMvc.perform(get("/api/projects/{projectId}/mobs", UUID.randomUUID()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error", is("PROJECT_NOT_FOUND")));
	}

	@Test
	void un_mob_eliminado_ticket_039_ya_no_aparece_en_el_listado_del_proyecto() throws Exception {
		UUID projectId = aProject();
		mockMvc.perform(post("/api/projects/{projectId}/mobs", projectId)
				.contentType(MediaType.APPLICATION_JSON)
				.content(createBody("Carcomido", "humanoid")));
		String secondBody = mockMvc
				.perform(post("/api/projects/{projectId}/mobs", projectId)
						.contentType(MediaType.APPLICATION_JSON)
						.content(createBody("Augur", "flying")))
				.andReturn()
				.getResponse()
				.getContentAsString();
		String augurId = objectMapper.readTree(secondBody).get("id").asText();

		mockMvc.perform(delete("/api/mobs/{mobId}", augurId));

		mockMvc.perform(get("/api/projects/{projectId}/mobs", projectId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].name", is("Carcomido")));
	}

}
