package com.galgothstudio.backend.project.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;
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

	/** Ticket 084 -- `sub` constante para el archivo completo, salvo los tests que comparan explícitamente dos dueños distintos (ver "Ownership real"). */
	private static final String OWNER_ID = "4635300a-5049-4cd5-933d-a37b807c83b0";

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

	/** Ticket 084 -- mismo mecanismo que valida `AuthCoreMcJwtDecoderConfig` (ticket 077), aquí simulado sin red vía `spring-security-test`. */
	private static RequestPostProcessor authenticated() {
		return authenticated(OWNER_ID);
	}

	private static RequestPostProcessor authenticated(String ownerId) {
		return jwt().jwt(builder -> builder.subject(ownerId));
	}

	/** Ticket 073 -- body de `PATCH` con `description` explícita (ver docstring de `RenameProjectRequest`: el contrato espera que cualquier caller la reenvíe siempre, aunque no cambie). */
	private String renameBody(String name, String description) {
		String descriptionJson = description == null ? "null" : "\"" + description + "\"";
		return "{\"name\":\"" + name + "\",\"description\":" + descriptionJson + "}";
	}

	private String createBody(String name) {
		return "{\"name\":\"" + name + "\"}";
	}

	/**
	 * Ticket 085 -- inserta el proyecto directo por JDBC en vez de crear
	 * por API y luego mutar `visibility` con un segundo `UPDATE` crudo:
	 * mezclar una escritura JDBC con una lectura JPA subsiguiente dentro
	 * de la MISMA transacción de test devuelve el `ProjectEntity` ya
	 * managed en el contexto de persistencia (caché de primer nivel de
	 * Hibernate), no la fila real recién escrita -- un `entityManager.clear()`
	 * no alcanza a evitarlo de forma confiable. Insertar la fila completa
	 * de una sola vez por JDBC, como ya hace `aMobIn`, evita el problema
	 * por completo.
	 */
	private UUID aProjectOf(String ownerId, String visibility) {
		UUID id = UUID.randomUUID();
		jdbc.update(
				"insert into projects (id, name, owner_ref, visibility) values (?, ?, ?, ?)",
				id, "Proyecto de prueba", ownerId, visibility);
		return id;
	}

	private UUID aMobIn(UUID projectId, String name) {
		return aMobIn(projectId, name, "draft");
	}

	/** Ticket 072 -- variante con `status` configurable, para probar la derivación de `ProjectSummary.status`. */
	private UUID aMobIn(UUID projectId, String name, String status) {
		UUID id = UUID.randomUUID();
		jdbc.update(
				"insert into mobs (id, project_id, name, base_type, status) values (?, ?, ?, ?, ?)",
				id, projectId, name, "humanoid", status);
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
		MvcResult result = mockMvc.perform(post("/api/projects").with(authenticated())
						.contentType(MediaType.APPLICATION_JSON)
						.content(createBody("Galgoth")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.name", is("Galgoth")))
				.andExpect(jsonPath("$.mobCount", is(0)))
				.andReturn();

		JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
		String projectId = body.get("id").asText();

		// Ticket 085 -- un proyecto recién creado nace PRIVATE (084): su
		// propio dueño necesita seguir mandando el JWT para leerlo.
		mockMvc.perform(get("/api/projects/{id}", projectId).with(authenticated()))
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
		MvcResult result = mockMvc.perform(post("/api/projects").with(authenticated())
						.contentType(MediaType.APPLICATION_JSON)
						.content(createBody("Fecha")))
				.andReturn();

		JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
		assertThat(body.get("createdAt").isTextual()).isTrue();
		assertThat(body.get("updatedAt").isTextual()).isTrue();
	}

	@Test
	void crear_sin_nombre_es_rechazado_con_mensaje_claro_AC2() throws Exception {
		mockMvc.perform(post("/api/projects").with(authenticated()).contentType(MediaType.APPLICATION_JSON).content(createBody("")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error", is("INVALID_PROJECT_NAME")));

		mockMvc.perform(post("/api/projects").with(authenticated()).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"   \"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error", is("INVALID_PROJECT_NAME")));
	}

	// -- Ticket 084: ownership real -------------------------------------------

	@Test
	void crear_sin_autenticacion_responde_401() throws Exception {
		mockMvc.perform(post("/api/projects").contentType(MediaType.APPLICATION_JSON).content(createBody("Sin sesión")))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error", is("UNAUTHENTICATED")));
	}

	@Test
	void un_proyecto_nuevo_nace_privado() throws Exception {
		mockMvc.perform(post("/api/projects").with(authenticated()).contentType(MediaType.APPLICATION_JSON).content(createBody("Nuevo")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.visibility", is("PRIVATE")));
	}

	// -- GET /api/projects (HU-02, dashboard) --------------------------------

	@Test
	void listar_sin_autenticacion_responde_401() throws Exception {
		mockMvc.perform(get("/api/projects")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error", is("UNAUTHENTICATED")));
	}

	/** Ticket 084 -- HU-02: "Mis proyectos" deja de ser un listado global, hallazgo real corregido por este ticket. */
	@Test
	void listar_solo_incluye_los_proyectos_del_dueno_autenticado() throws Exception {
		String otroOwnerId = "9c3e3b1a-2222-4d3d-8888-0f1a2b3c4d5e";
		mockMvc.perform(post("/api/projects").with(authenticated()).contentType(MediaType.APPLICATION_JSON).content(createBody("Mío")))
				.andExpect(status().isCreated());
		mockMvc.perform(post("/api/projects")
						.with(authenticated(otroOwnerId))
						.contentType(MediaType.APPLICATION_JSON)
						.content(createBody("Ajeno")))
				.andExpect(status().isCreated());

		mockMvc.perform(get("/api/projects").with(authenticated()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].name", is("Mío")));
	}

	@Test
	void listar_incluye_hasta_3_miniaturas_y_el_conteo_total_de_mobs_AC3() throws Exception {
		MvcResult created = mockMvc.perform(post("/api/projects").with(authenticated())
						.contentType(MediaType.APPLICATION_JSON)
						.content(createBody("Carcomido")))
				.andReturn();
		UUID projectId = UUID.fromString(objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText());
		flush();
		for (int i = 0; i < 5; i++) {
			aMobIn(projectId, "mob-" + i);
		}
		flush();

		mockMvc.perform(get("/api/projects").with(authenticated()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].mobCount", is(5)))
				.andExpect(jsonPath("$[0].mobThumbnails", hasSize(3))); // AC "+N": el frontend calcula N = mobCount - 3
	}

	// -- ticket 072: status derivado (active/draft) --------------------------

	@Test
	void un_proyecto_sin_mobs_es_draft() throws Exception {
		mockMvc.perform(post("/api/projects").with(authenticated()).contentType(MediaType.APPLICATION_JSON).content(createBody("Vacío")));

		mockMvc.perform(get("/api/projects").with(authenticated())).andExpect(jsonPath("$[0].status", is("draft")));
	}

	@Test
	void un_proyecto_con_todos_sus_mobs_en_draft_es_draft() throws Exception {
		MvcResult created = mockMvc.perform(post("/api/projects").with(authenticated())
						.contentType(MediaType.APPLICATION_JSON)
						.content(createBody("Todo en draft")))
				.andReturn();
		UUID projectId = UUID.fromString(objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText());
		flush();
		aMobIn(projectId, "mob-1", "draft");
		aMobIn(projectId, "mob-2", "draft");
		flush();

		mockMvc.perform(get("/api/projects").with(authenticated())).andExpect(jsonPath("$[0].status", is("draft")));
	}

	@Test
	void un_proyecto_con_al_menos_un_mob_fuera_de_draft_es_active() throws Exception {
		MvcResult created = mockMvc.perform(post("/api/projects").with(authenticated())
						.contentType(MediaType.APPLICATION_JSON)
						.content(createBody("Con progreso real")))
				.andReturn();
		UUID projectId = UUID.fromString(objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText());
		flush();
		aMobIn(projectId, "mob-draft", "draft");
		aMobIn(projectId, "mob-en-progreso", "in_progress");
		flush();

		mockMvc.perform(get("/api/projects").with(authenticated())).andExpect(jsonPath("$[0].status", is("active")));
	}

	@Test
	void listar_excluye_mobs_eliminados_del_conteo_y_de_las_miniaturas_ticket_039() throws Exception {
		MvcResult created = mockMvc.perform(post("/api/projects").with(authenticated())
						.contentType(MediaType.APPLICATION_JSON)
						.content(createBody("Con mobs eliminados en el dashboard")))
				.andReturn();
		UUID projectId = UUID.fromString(objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText());
		flush();
		aMobIn(projectId, "mob-vivo");
		UUID mobEliminadoId = aMobIn(projectId, "mob-eliminado");
		flush();
		mockMvc.perform(delete("/api/mobs/{mobId}", mobEliminadoId).with(authenticated())).andExpect(status().isNoContent());

		mockMvc.perform(get("/api/projects").with(authenticated()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].mobCount", is(1)))
				.andExpect(jsonPath("$[0].mobThumbnails", hasSize(1)));
	}

	@Test
	void listar_no_incluye_proyectos_eliminados() throws Exception {
		MvcResult created = mockMvc.perform(post("/api/projects").with(authenticated())
						.contentType(MediaType.APPLICATION_JSON)
						.content(createBody("Temporal")))
				.andReturn();
		String projectId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

		mockMvc.perform(delete("/api/projects/{id}", projectId).with(authenticated())).andExpect(status().isNoContent());
		flush();

		mockMvc.perform(get("/api/projects/{id}", projectId)).andExpect(status().isNotFound());
	}

	// -- PATCH /api/projects/{id} (Rename) -----------------------------------

	@Test
	void renombrar_actualiza_el_nombre() throws Exception {
		MvcResult created = mockMvc.perform(post("/api/projects").with(authenticated())
						.contentType(MediaType.APPLICATION_JSON)
						.content(createBody("Nombre viejo")))
				.andReturn();
		String projectId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

		mockMvc.perform(patch("/api/projects/{id}", projectId).with(authenticated())
						.contentType(MediaType.APPLICATION_JSON)
						.content(createBody("Nombre nuevo")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name", is("Nombre nuevo")));
	}

	@Test
	void editar_la_descripcion_la_actualiza_y_persiste() throws Exception {
		MvcResult created = mockMvc.perform(post("/api/projects").with(authenticated())
						.contentType(MediaType.APPLICATION_JSON)
						.content(createBody("Galgoth")))
				.andReturn();
		String projectId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

		mockMvc.perform(patch("/api/projects/{id}", projectId).with(authenticated())
						.contentType(MediaType.APPLICATION_JSON)
						.content(renameBody("Galgoth", "Universo de criaturas oscuras y corrompidas.")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.description", is("Universo de criaturas oscuras y corrompidas.")));

		mockMvc.perform(get("/api/projects/{id}", projectId).with(authenticated()))
				.andExpect(jsonPath("$.description", is("Universo de criaturas oscuras y corrompidas.")));
	}

	@Test
	void un_proyecto_recien_creado_no_tiene_descripcion() throws Exception {
		mockMvc.perform(post("/api/projects").with(authenticated())
						.contentType(MediaType.APPLICATION_JSON)
						.content(createBody("Sin descripción todavía")))
				.andExpect(status().isCreated());

		mockMvc.perform(get("/api/projects").with(authenticated()))
				.andExpect(jsonPath("$[0].description", is(nullValue())));
	}

	@Test
	void renombrar_sin_nombre_es_rechazado() throws Exception {
		MvcResult created = mockMvc.perform(post("/api/projects").with(authenticated())
						.contentType(MediaType.APPLICATION_JSON)
						.content(createBody("Nombre")))
				.andReturn();
		String projectId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

		mockMvc.perform(patch("/api/projects/{id}", projectId).with(authenticated())
						.contentType(MediaType.APPLICATION_JSON)
						.content(createBody("")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error", is("INVALID_PROJECT_NAME")));
	}

	// -- DELETE /api/projects/{id} --------------------------------------------

	@Test
	void eliminar_un_proyecto_inexistente_responde_404() throws Exception {
		mockMvc.perform(delete("/api/projects/{id}", UUID.randomUUID()).with(authenticated())).andExpect(status().isNotFound());
	}

	// -- POST /api/projects/{id}/duplicate (copia profunda) ------------------

	@Test
	void duplicar_copia_el_proyecto_y_todos_sus_mobs_con_su_historial_de_revisiones() throws Exception {
		MvcResult created = mockMvc.perform(post("/api/projects").with(authenticated())
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

		MvcResult duplicated = mockMvc.perform(post("/api/projects/{id}/duplicate", projectId).with(authenticated()))
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
		mockMvc.perform(post("/api/projects/{id}/duplicate", UUID.randomUUID()).with(authenticated()))
				.andExpect(status().isNotFound());
	}

	// -- Ticket 039: un mob eliminado (soft-delete) no debe contarse ni copiarse --
	// Hallazgo real de la verificación en vivo: countByProjectId/findByProjectIdOrderByUpdatedAtDesc
	// (sin filtrar deletedAt) seguían usándose acá para el conteo de "criaturas"
	// y para Duplicate -- antes de este ticket `mobs` no tenía soft-delete, así
	// que nunca hizo falta filtrar.

	@Test
	void el_conteo_de_mobs_del_detalle_excluye_los_eliminados() throws Exception {
		MvcResult created = mockMvc.perform(post("/api/projects").with(authenticated())
						.contentType(MediaType.APPLICATION_JSON)
						.content(createBody("Con mob eliminado")))
				.andReturn();
		UUID projectId = UUID.fromString(objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText());
		flush();
		aMobIn(projectId, "mob-vivo");
		UUID mobEliminadoId = aMobIn(projectId, "mob-eliminado");
		flush();

		mockMvc.perform(delete("/api/mobs/{mobId}", mobEliminadoId).with(authenticated())).andExpect(status().isNoContent());

		mockMvc.perform(get("/api/projects/{id}", projectId).with(authenticated())).andExpect(jsonPath("$.mobCount", is(1)));
	}

	@Test
	void duplicar_un_proyecto_no_copia_sus_mobs_eliminados() throws Exception {
		MvcResult created = mockMvc.perform(post("/api/projects").with(authenticated())
						.contentType(MediaType.APPLICATION_JSON)
						.content(createBody("Con mob eliminado para duplicar")))
				.andReturn();
		UUID projectId = UUID.fromString(objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText());
		flush();
		aMobIn(projectId, "mob-vivo");
		UUID mobEliminadoId = aMobIn(projectId, "mob-eliminado");
		flush();
		mockMvc.perform(delete("/api/mobs/{mobId}", mobEliminadoId).with(authenticated())).andExpect(status().isNoContent());

		MvcResult duplicated = mockMvc.perform(post("/api/projects/{id}/duplicate", projectId).with(authenticated()))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.mobCount", is(1)))
				.andReturn();
		flush();

		String newProjectId = objectMapper.readTree(duplicated.getResponse().getContentAsString()).get("id").asText();
		Integer newMobCount =
				jdbc.queryForObject("select count(*) from mobs where project_id = ?", Integer.class, UUID.fromString(newProjectId));
		assertThat(newMobCount).isEqualTo(1); // solo el mob vivo se copió, no el eliminado
	}

	// -- Ticket 085: enforcement dueño/público/privado -----------------------

	@Test
	void un_usuario_distinto_al_dueno_no_puede_leer_un_proyecto_privado_ajeno() throws Exception {
		String otroOwnerId = "9c3e3b1a-2222-4d3d-8888-0f1a2b3c4d5e";
		MvcResult created = mockMvc.perform(post("/api/projects").with(authenticated())
						.contentType(MediaType.APPLICATION_JSON)
						.content(createBody("Privado ajeno")))
				.andReturn();
		String projectId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

		mockMvc.perform(get("/api/projects/{id}", projectId).with(authenticated(otroOwnerId))).andExpect(status().isNotFound());
	}

	@Test
	void una_lectura_sin_autorizacion_de_un_proyecto_privado_responde_404() throws Exception {
		MvcResult created = mockMvc.perform(post("/api/projects").with(authenticated())
						.contentType(MediaType.APPLICATION_JSON)
						.content(createBody("Privado")))
				.andReturn();
		String projectId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

		mockMvc.perform(get("/api/projects/{id}", projectId)).andExpect(status().isNotFound());
	}

	@Test
	void una_lectura_sin_autorizacion_de_un_proyecto_publico_funciona() throws Exception {
		String ownerId = UUID.randomUUID().toString();
		UUID projectId = aProjectOf(ownerId, "PUBLIC");

		mockMvc.perform(get("/api/projects/{id}", projectId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name", is("Proyecto de prueba")));
	}

	@Test
	void un_usuario_distinto_al_dueno_no_puede_mutar_un_proyecto_ajeno_publico_o_privado() throws Exception {
		String ownerId = UUID.randomUUID().toString();
		String otroOwnerId = "9c3e3b1a-2222-4d3d-8888-0f1a2b3c4d5e";
		UUID projectId = aProjectOf(ownerId, "PUBLIC");

		mockMvc.perform(patch("/api/projects/{id}", projectId).with(authenticated(otroOwnerId))
						.contentType(MediaType.APPLICATION_JSON)
						.content(createBody("Hackeado")))
				.andExpect(status().isNotFound());
		mockMvc.perform(delete("/api/projects/{id}", projectId).with(authenticated(otroOwnerId))).andExpect(status().isNotFound());
	}

	@Test
	void el_dueno_real_sigue_accediendo_sin_problema_a_cada_operacion_de_su_propio_proyecto() throws Exception {
		MvcResult created = mockMvc.perform(post("/api/projects").with(authenticated())
						.contentType(MediaType.APPLICATION_JSON)
						.content(createBody("Mío de verdad")))
				.andReturn();
		String projectId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

		mockMvc.perform(get("/api/projects/{id}", projectId).with(authenticated())).andExpect(status().isOk());
		mockMvc.perform(patch("/api/projects/{id}", projectId).with(authenticated())
						.contentType(MediaType.APPLICATION_JSON)
						.content(createBody("Mío de verdad, renombrado")))
				.andExpect(status().isOk());
		mockMvc.perform(post("/api/projects/{id}/duplicate", projectId).with(authenticated())).andExpect(status().isCreated());
		mockMvc.perform(delete("/api/projects/{id}", projectId).with(authenticated())).andExpect(status().isNoContent());
	}

	// -- Ticket 086: cambio de visibilidad + owner_display_name ----------------

	@Test
	void crear_un_proyecto_con_ownerDisplayName_lo_graba_y_lo_devuelve_en_el_detalle() throws Exception {
		mockMvc.perform(post("/api/projects").with(authenticated())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"Con autor\",\"ownerDisplayName\":\"Ada Lovelace\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.ownerDisplayName", is("Ada Lovelace")));
	}

	@Test
	void crear_un_proyecto_sin_ownerDisplayName_lo_deja_null() throws Exception {
		mockMvc.perform(post("/api/projects").with(authenticated())
						.contentType(MediaType.APPLICATION_JSON)
						.content(createBody("Sin autor")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.ownerDisplayName", is(nullValue())));
	}

	@Test
	void el_dueno_puede_publicar_y_despublicar_su_proyecto() throws Exception {
		MvcResult created = mockMvc.perform(post("/api/projects").with(authenticated())
						.contentType(MediaType.APPLICATION_JSON)
						.content(createBody("Publicable")))
				.andReturn();
		String projectId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

		mockMvc.perform(patch("/api/projects/{id}/visibility", projectId).with(authenticated())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"visibility\":\"PUBLIC\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.visibility", is("PUBLIC")));
		mockMvc.perform(get("/api/projects/{id}", projectId)).andExpect(status().isOk()); // ahora legible sin sesión

		mockMvc.perform(patch("/api/projects/{id}/visibility", projectId).with(authenticated())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"visibility\":\"PRIVATE\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.visibility", is("PRIVATE")));
		mockMvc.perform(get("/api/projects/{id}", projectId)).andExpect(status().isNotFound()); // vuelve a ocultarse
	}

	@Test
	void un_usuario_distinto_al_dueno_no_puede_cambiar_la_visibilidad_de_un_proyecto_ajeno() throws Exception {
		String ownerId = UUID.randomUUID().toString();
		String otroOwnerId = "9c3e3b1a-2222-4d3d-8888-0f1a2b3c4d5e";
		UUID projectId = aProjectOf(ownerId, "PRIVATE");

		mockMvc.perform(patch("/api/projects/{id}/visibility", projectId).with(authenticated(otroOwnerId))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"visibility\":\"PUBLIC\"}"))
				.andExpect(status().isNotFound());
	}

	@Test
	void cambiar_visibilidad_sin_autenticacion_responde_401() throws Exception {
		String ownerId = UUID.randomUUID().toString();
		UUID projectId = aProjectOf(ownerId, "PRIVATE");

		mockMvc.perform(patch("/api/projects/{id}/visibility", projectId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"visibility\":\"PUBLIC\"}"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void un_valor_de_visibilidad_invalido_es_rechazado_sin_cambiar_nada() throws Exception {
		MvcResult created = mockMvc.perform(post("/api/projects").with(authenticated())
						.contentType(MediaType.APPLICATION_JSON)
						.content(createBody("Original")))
				.andReturn();
		String projectId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

		mockMvc.perform(patch("/api/projects/{id}/visibility", projectId).with(authenticated())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"visibility\":\"HIDDEN\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error", is("INVALID_VISIBILITY")));

		mockMvc.perform(get("/api/projects/{id}", projectId).with(authenticated()))
				.andExpect(jsonPath("$.visibility", is("PRIVATE")));
	}

	// -- CORS (docs/definiciones/galgoth-studio-mvp.md §9 -- origen local de desarrollo) --

	@Test
	void habilita_cors_para_el_origen_local_de_desarrollo_del_frontend() throws Exception {
		mockMvc.perform(get("/api/projects").with(authenticated()).header("Origin", "http://localhost:5173"))
				.andExpect(status().isOk())
				.andExpect(result -> assertThat(result.getResponse().getHeader("Access-Control-Allow-Origin"))
						.isEqualTo("http://localhost:5173"));
	}

}
