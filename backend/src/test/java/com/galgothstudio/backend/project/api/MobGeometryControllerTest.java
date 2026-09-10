package com.galgothstudio.backend.project.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.galgothstudio.backend.TestcontainersConfiguration;
import jakarta.persistence.EntityManager;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integración de punta a punta (Testcontainers + Postgres real + MockMvc)
 * de {@code POST /api/mobs/{mobId}/geometry/apply} (ticket 043, Diseño
 * técnico §2/§15 de `docs/definiciones/galgoth-studio-fase3-textura.md`) --
 * uno por AC del ticket. Mismo patrón que {@link MobDraftControllerTest}.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MobGeometryControllerTest {

	private static final File FIXTURE_FILE = new File("../contracts/fixtures/model-spec-example.json");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private EntityManager entityManager;

	/** Ver el comentario equivalente en {@link MobDraftControllerTest} -- mismo artefacto de mezclar JPA + JDBC crudo dentro de la MISMA transacción de test. */
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

	/** Misma fixture real, con la región `head_main`/`north` marcada PAINTED -- para forzar el caso de confirmación. */
	private String fixtureJsonWithHeadNorthPainted() throws IOException {
		ObjectNode node = (ObjectNode) objectMapper.readTree(fixtureJson());
		ArrayNode regions = (ArrayNode) node.path("uv").path("regions");
		for (JsonNode region : regions) {
			if (region.path("cuboidId").asText().equals("head_main") && region.path("face").asText().equals("north")) {
				((ObjectNode) region).put("status", "painted");
			}
		}
		return objectMapper.writeValueAsString(node);
	}

	private void seedDraft(UUID mobId, String modelJson) throws Exception {
		mockMvc.perform(patch("/api/mobs/{mobId}/draft", mobId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"model\":" + modelJson + "}"))
				.andExpect(status().isOk());
	}

	private String applyRequestBody(String operationsJson, boolean confirmPaintLoss) {
		return "{\"operations\":" + operationsJson + ",\"confirmPaintLoss\":" + confirmPaintLoss + "}";
	}

	// -- resizeCuboid sin caras PAINTED: se aplica server-side y devuelve 200 --

	@Test
	void resizeCuboid_sinCarasPainted_seAplicaServerSideYDevuelve200ConLaUvResultante() throws Exception {
		UUID mobId = aMob(aProject());
		seedDraft(mobId, fixtureJson());
		String operations = "[{\"op\":\"resizeCuboid\",\"target\":\"head_main\",\"scale\":[1.5,1.5,1.5]}]";

		MvcResult result = mockMvc.perform(post("/api/mobs/{mobId}/geometry/apply", mobId)
						.contentType(MediaType.APPLICATION_JSON)
						.content(applyRequestBody(operations, false)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.draftVersion", is(2)))
				.andReturn();

		JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
		JsonNode headMain = null;
		for (JsonNode cuboid : body.path("model").path("cuboids")) {
			if (cuboid.path("id").asText().equals("head_main")) {
				headMain = cuboid;
			}
		}
		assertThat(headMain).isNotNull();
		assertThat(headMain.path("to").get(0).asDouble() - headMain.path("from").get(0).asDouble()).isEqualTo(12.0);
	}

	// -- resizeCuboid que afecta una cara PAINTED, sin confirmar: 4xx con el detalle --

	@Test
	void resizeCuboid_queAfectaUnaCaraPainted_sinConfirmar_respondeConflictConElDetalleYNoAplicaNada() throws Exception {
		UUID mobId = aMob(aProject());
		seedDraft(mobId, fixtureJsonWithHeadNorthPainted());
		String operations = "[{\"op\":\"resizeCuboid\",\"target\":\"head_main\",\"scale\":[2,2,2]}]";

		mockMvc.perform(post("/api/mobs/{mobId}/geometry/apply", mobId)
						.contentType(MediaType.APPLICATION_JSON)
						.content(applyRequestBody(operations, false)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error", is("PAINTED_REGION_RESIZE_CONFIRMATION_REQUIRED")))
				.andExpect(jsonPath("$.details[0]", is("head_main:north")));

		// No se aplicó nada -- el draft sigue en su versión inicial (1, del seedDraft).
		flush();
		Integer draftVersion =
				jdbc.queryForObject("select draft_version from mob_drafts where mob_id = ?", Integer.class, mobId);
		assertThat(draftVersion).isEqualTo(1);
	}

	// -- Mismo caso, reenviado con confirmPaintLoss=true: se aplica y devuelve 200 --

	@Test
	void resizeCuboid_queAfectaUnaCaraPainted_confirmado_seAplicaYDevuelve200() throws Exception {
		UUID mobId = aMob(aProject());
		seedDraft(mobId, fixtureJsonWithHeadNorthPainted());
		String operations = "[{\"op\":\"resizeCuboid\",\"target\":\"head_main\",\"scale\":[2,2,2]}]";

		MvcResult result = mockMvc.perform(post("/api/mobs/{mobId}/geometry/apply", mobId)
						.contentType(MediaType.APPLICATION_JSON)
						.content(applyRequestBody(operations, true)))
				.andExpect(status().isOk())
				.andReturn();

		JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
		assertThat(body.path("model").path("uv").path("reservations")).hasSize(1);
		assertThat(body.path("model").path("uv").path("reservations").get(0).path("sourceCuboidId").asText())
				.isEqualTo("head_main");
	}

	// -- createCuboid: sigue las reglas de 041 a través de este endpoint --

	@Test
	void createCuboid_seAplicaServerSideYQuedaPersistidoEnElDraft() throws Exception {
		UUID mobId = aMob(aProject());
		seedDraft(mobId, fixtureJson());
		String operations = "[{\"op\":\"createCuboid\",\"tempId\":\"tmp-1\",\"name\":\"nuevo\",\"boneId\":\"head\","
				+ "\"from\":[-1,0,-1],\"to\":[1,2,1],\"origin\":[0,1,0],\"rotation\":[0,0,0]}]";

		mockMvc.perform(post("/api/mobs/{mobId}/geometry/apply", mobId)
						.contentType(MediaType.APPLICATION_JSON)
						.content(applyRequestBody(operations, false)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.model.cuboids.length()", is(4)));
	}

	// -- removeCuboid: sigue las reglas de 041 a través de este endpoint --

	@Test
	void removeCuboid_seAplicaServerSideYQuedaPersistidoEnElDraft() throws Exception {
		UUID mobId = aMob(aProject());
		seedDraft(mobId, fixtureJson());
		String operations = "[{\"op\":\"removeCuboid\",\"target\":\"arm_right_upper\"}]";

		mockMvc.perform(post("/api/mobs/{mobId}/geometry/apply", mobId)
						.contentType(MediaType.APPLICATION_JSON)
						.content(applyRequestBody(operations, false)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.model.cuboids.length()", is(2)));
	}

	// -- createCuboid sin espacio libre: UvAtlasOverflowException traducida a 400 --

	@Test
	void createCuboid_sinEspacioLibreEnElAtlas_responde400UvAtlasOverflow() throws Exception {
		UUID mobId = aMob(aProject());
		seedDraft(mobId, fixtureJson());
		// Footprint enorme (200x200x200) -- nunca cabe en el atlas de 128x128 de la fixture.
		String operations = "[{\"op\":\"createCuboid\",\"tempId\":\"tmp-1\",\"name\":\"gigante\",\"boneId\":\"head\","
				+ "\"from\":[-100,-100,-100],\"to\":[100,100,100],\"origin\":[0,0,0],\"rotation\":[0,0,0]}]";

		mockMvc.perform(post("/api/mobs/{mobId}/geometry/apply", mobId)
						.contentType(MediaType.APPLICATION_JSON)
						.content(applyRequestBody(operations, false)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error", is("UV_ATLAS_OVERFLOW")));
	}

	// -- Whitelist cerrada: moveCuboid/rotateCuboid/pivot NUNCA pasan por este endpoint --

	@Test
	void moveCuboid_esRechazadoExplicitamenteConUnErrorClaro() throws Exception {
		UUID mobId = aMob(aProject());
		seedDraft(mobId, fixtureJson());
		String operations = "[{\"op\":\"moveCuboid\",\"target\":\"head_main\",\"delta\":[1,0,0]}]";

		mockMvc.perform(post("/api/mobs/{mobId}/geometry/apply", mobId)
						.contentType(MediaType.APPLICATION_JSON)
						.content(applyRequestBody(operations, false)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error", is("UNSUPPORTED_GEOMETRY_OPERATION")));
	}

	@Test
	void rotateCuboid_esRechazadoExplicitamenteConUnErrorClaro() throws Exception {
		UUID mobId = aMob(aProject());
		seedDraft(mobId, fixtureJson());
		String operations = "[{\"op\":\"rotateCuboid\",\"target\":\"head_main\",\"rotationDeg\":[0,15,0]}]";

		mockMvc.perform(post("/api/mobs/{mobId}/geometry/apply", mobId)
						.contentType(MediaType.APPLICATION_JSON)
						.content(applyRequestBody(operations, false)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error", is("UNSUPPORTED_GEOMETRY_OPERATION")));
	}

	@Test
	void setBonePivot_pivotEsRechazadoExplicitamenteConUnErrorClaro() throws Exception {
		UUID mobId = aMob(aProject());
		seedDraft(mobId, fixtureJson());
		String operations = "[{\"op\":\"setBonePivot\",\"target\":\"head\",\"pivot\":[0,25,0]}]";

		mockMvc.perform(post("/api/mobs/{mobId}/geometry/apply", mobId)
						.contentType(MediaType.APPLICATION_JSON)
						.content(applyRequestBody(operations, false)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error", is("UNSUPPORTED_GEOMETRY_OPERATION")));
	}

	// -- Mob/draft inexistentes: mismos códigos ya establecidos por 020 --

	@Test
	void applyDeUnMobInexistente_responde404MobNotFound() throws Exception {
		String operations = "[{\"op\":\"resizeCuboid\",\"target\":\"head_main\",\"scale\":[1.5,1.5,1.5]}]";

		mockMvc.perform(post("/api/mobs/{mobId}/geometry/apply", UUID.randomUUID())
						.contentType(MediaType.APPLICATION_JSON)
						.content(applyRequestBody(operations, false)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error", is("MOB_NOT_FOUND")));
	}

	@Test
	void applyDeUnMobSinDraftTodavia_responde404DraftNotFound() throws Exception {
		UUID mobId = aMob(aProject());
		String operations = "[{\"op\":\"resizeCuboid\",\"target\":\"head_main\",\"scale\":[1.5,1.5,1.5]}]";

		mockMvc.perform(post("/api/mobs/{mobId}/geometry/apply", mobId)
						.contentType(MediaType.APPLICATION_JSON)
						.content(applyRequestBody(operations, false)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error", is("DRAFT_NOT_FOUND")));
	}

}
