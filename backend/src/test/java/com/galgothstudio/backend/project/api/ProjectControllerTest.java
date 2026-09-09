package com.galgothstudio.backend.project.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integración de punta a punta (Testcontainers + Postgres real + MockMvc)
 * del CRUD de proyectos, ticket 021. Mismo patrón que
 * {@link com.galgothstudio.backend.project.api.MobDraftControllerTest}
 * (ticket 020) -- incluido el `entityManager.flush()` antes de leer vía
 * JDBC crudo (ver esa clase para el porqué, es un artefacto de mezclar
 * JPA + JDBC dentro de la misma transacción de test, no de producción).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProjectControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private EntityManager entityManager;

	private void flush() {
		entityManager.flush();
	}

	private String createBody(String name) {
		return "{\"name\":\"" + name + "\"}";
	}

	private UUID aMobIn(UUID projectId, String name) {
		UUID id = UUID.randomUUID();
		jdbc.update(
				"insert into mobs (id, project_id, name, base_type, status) values (?, ?, ?, ?, ?)",
				id, projectId, name, "humanoid", "draft");
		return id;
	}

	private UUID aRevisionOf(UUID mobId, int revisionNumber) {
		UUID id = UUID.randomUUID();
		jdbc.update(
				"insert into mob_revisions (id, mob_id, revision_number, model_jsonb, created_by) values (?, ?, ?, ?::jsonb, ?)",
				id, mobId, revisionNumber, "{\"mobId\":\"" + mobId + "\"}", "user");
		return id;
	}

	// -- POST /api/projects (HU-01) ------------------------------------------

	@Test
	void crear_con_nombre_valido_devuelve_201_y_redirige_a_su_detalle_AC1() throws Exception {
		MvcResult result = mockMvc.perform(post("/api/projects")
						.contentType(MediaType.APPLICATION_JSON)
						.content(createBody("Galgoth")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.name", is("Galgoth")))
				.andExpect(jsonPath("$.mobCount", is(0)))
				.andReturn();

		JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
		String projectId = body.get("id").asText();

		mockMvc.perform(get("/api/projects/{id}", projectId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name", is("Galgoth")));
	}

	@Test
	void createdAt_y_updatedAt_serializan_como_texto_ISO8601_no_como_epoch_numerico() throws Exception {
		// Hallazgo real (verificado en vivo, no solo en tests): sin
		// deshabilitar WRITE_DATES_AS_TIMESTAMPS en el ObjectMapper
		// construido a mano (ver JacksonConfig), un Instant serializa
		// como epoch-seconds fraccionario (ej. 1788924939.428339) --
		// un número, no un string ISO-8601 parseable por Date en el frontend.
		MvcResult result = mockMvc.perform(post("/api/projects")
						.contentType(MediaType.APPLICATION_JSON)
						.content(createBody("Fecha")))
				.andReturn();

		JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
		assertThat(body.get("createdAt").isTextual()).isTrue();
		assertThat(body.get("updatedAt").isTextual()).isTrue();
	}

	@Test
	void crear_sin_nombre_es_rechazado_con_mensaje_claro_AC2() throws Exception {
		mockMvc.perform(post("/api/projects").contentType(MediaType.APPLICATION_JSON).content(createBody("")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error", is("INVALID_PROJECT_NAME")));

		mockMvc.perform(post("/api/projects").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"   \"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error", is("INVALID_PROJECT_NAME")));
	}

	// -- GET /api/projects (HU-02, dashboard) --------------------------------

	@Test
	void listar_incluye_hasta_3_miniaturas_y_el_conteo_total_de_mobs_AC3() throws Exception {
		MvcResult created = mockMvc.perform(post("/api/projects")
						.contentType(MediaType.APPLICATION_JSON)
						.content(createBody("Carcomido")))
				.andReturn();
		UUID projectId = UUID.fromString(objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText());
		flush();
		for (int i = 0; i < 5; i++) {
			aMobIn(projectId, "mob-" + i);
		}
		flush();

		mockMvc.perform(get("/api/projects"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].mobCount", is(5)))
				.andExpect(jsonPath("$[0].mobThumbnails", hasSize(3))); // AC "+N": el frontend calcula N = mobCount - 3
	}

	@Test
	void listar_no_incluye_proyectos_eliminados() throws Exception {
		MvcResult created = mockMvc.perform(post("/api/projects")
						.contentType(MediaType.APPLICATION_JSON)
						.content(createBody("Temporal")))
				.andReturn();
		String projectId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

		mockMvc.perform(delete("/api/projects/{id}", projectId)).andExpect(status().isNoContent());
		flush();

		mockMvc.perform(get("/api/projects/{id}", projectId)).andExpect(status().isNotFound());
	}

	// -- PATCH /api/projects/{id} (Rename) -----------------------------------

	@Test
	void renombrar_actualiza_el_nombre() throws Exception {
		MvcResult created = mockMvc.perform(post("/api/projects")
						.contentType(MediaType.APPLICATION_JSON)
						.content(createBody("Nombre viejo")))
				.andReturn();
		String projectId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

		mockMvc.perform(patch("/api/projects/{id}", projectId)
						.contentType(MediaType.APPLICATION_JSON)
						.content(createBody("Nombre nuevo")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name", is("Nombre nuevo")));
	}

	@Test
	void renombrar_sin_nombre_es_rechazado() throws Exception {
		MvcResult created = mockMvc.perform(post("/api/projects")
						.contentType(MediaType.APPLICATION_JSON)
						.content(createBody("Nombre")))
				.andReturn();
		String projectId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

		mockMvc.perform(patch("/api/projects/{id}", projectId)
						.contentType(MediaType.APPLICATION_JSON)
						.content(createBody("")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error", is("INVALID_PROJECT_NAME")));
	}

	// -- DELETE /api/projects/{id} --------------------------------------------

	@Test
	void eliminar_un_proyecto_inexistente_responde_404() throws Exception {
		mockMvc.perform(delete("/api/projects/{id}", UUID.randomUUID())).andExpect(status().isNotFound());
	}

	// -- POST /api/projects/{id}/duplicate (copia profunda) ------------------

	@Test
	void duplicar_copia_el_proyecto_y_todos_sus_mobs_con_su_historial_de_revisiones() throws Exception {
		MvcResult created = mockMvc.perform(post("/api/projects")
						.contentType(MediaType.APPLICATION_JSON)
						.content(createBody("Original")))
				.andReturn();
		UUID projectId = UUID.fromString(objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText());
		flush();
		UUID mobId = aMobIn(projectId, "mob-original");
		aRevisionOf(mobId, 1);
		aRevisionOf(mobId, 2);
		jdbc.update("update mobs set current_revision_number = 2 where id = ?", mobId);
		flush();

		MvcResult duplicated = mockMvc.perform(post("/api/projects/{id}/duplicate", projectId))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.name", is("Original (copia)")))
				.andExpect(jsonPath("$.mobCount", is(1)))
				.andReturn();
		flush();

		String newProjectId = objectMapper.readTree(duplicated.getResponse().getContentAsString()).get("id").asText();
		Integer newMobCount =
				jdbc.queryForObject("select count(*) from mobs where project_id = ?", Integer.class, UUID.fromString(newProjectId));
		assertThat(newMobCount).isEqualTo(1);
		UUID newMobId = jdbc.queryForObject("select id from mobs where project_id = ?", UUID.class, UUID.fromString(newProjectId));
		Integer newRevisionCount =
				jdbc.queryForObject("select count(*) from mob_revisions where mob_id = ?", Integer.class, newMobId);
		assertThat(newRevisionCount).isEqualTo(2); // el historial completo se copió, no solo la última revisión
		Integer newMobCurrentRevision =
				jdbc.queryForObject("select current_revision_number from mobs where id = ?", Integer.class, newMobId);
		assertThat(newMobCurrentRevision).isEqualTo(2);
	}

	@Test
	void duplicar_un_proyecto_inexistente_responde_404() throws Exception {
		mockMvc.perform(post("/api/projects/{id}/duplicate", UUID.randomUUID())).andExpect(status().isNotFound());
	}

	// -- CORS (docs/definiciones/galgoth-studio-mvp.md §9 -- origen local de desarrollo) --

	@Test
	void habilita_cors_para_el_origen_local_de_desarrollo_del_frontend() throws Exception {
		mockMvc.perform(get("/api/projects").header("Origin", "http://localhost:5173"))
				.andExpect(status().isOk())
				.andExpect(result -> assertThat(result.getResponse().getHeader("Access-Control-Allow-Origin"))
						.isEqualTo("http://localhost:5173"));
	}

}
