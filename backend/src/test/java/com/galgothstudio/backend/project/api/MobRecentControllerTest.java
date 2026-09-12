package com.galgothstudio.backend.project.api;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.galgothstudio.backend.TestcontainersConfiguration;
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
 * de "mobs recientes cruzando proyectos" (ticket 071, "Continuar
 * trabajando" de Inicio) -- mismo patrón que {@link MobControllerTest}.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MobRecentControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private ObjectMapper objectMapper;

	private UUID aProject(String name) {
		UUID id = UUID.randomUUID();
		jdbc.update("insert into projects (id, name) values (?, ?)", id, name);
		return id;
	}

	private String createBody(String name, String baseType) {
		return "{\"name\":\"" + name + "\",\"baseType\":\"" + baseType + "\"}";
	}

	private String createMob(UUID projectId, String name, String baseType) throws Exception {
		String body = mockMvc
				.perform(post("/api/projects/{projectId}/mobs", projectId)
						.contentType(MediaType.APPLICATION_JSON)
						.content(createBody(name, baseType)))
				.andReturn()
				.getResponse()
				.getContentAsString();
		return objectMapper.readTree(body).get("id").asText();
	}

	@Test
	void devuelve_mobs_de_distintos_proyectos_ordenados_por_actualizacion_mas_reciente_primero() throws Exception {
		UUID projectA = aProject("Galgoth");
		UUID projectB = aProject("Criaturas del Nether");

		// Orden de inserción: Carcomido (A), Augur (B), Tejedora (A) -- el más
		// reciente por updated_at debe ser Tejedora, sin importar de qué
		// proyecto sea ni el orden en que se crearon los proyectos.
		createMob(projectA, "Carcomido", "humanoid");
		createMob(projectB, "Augur", "flying");
		createMob(projectA, "Tejedora", "arachnid");

		mockMvc.perform(get("/api/mobs/recent"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(3)))
				.andExpect(jsonPath("$[0].name", is("Tejedora")))
				.andExpect(jsonPath("$[0].projectId", is(projectA.toString())))
				.andExpect(jsonPath("$[1].name", is("Augur")))
				.andExpect(jsonPath("$[2].name", is("Carcomido")));
	}

	@Test
	void respeta_el_parametro_limit() throws Exception {
		UUID projectId = aProject("Galgoth");
		createMob(projectId, "Carcomido", "humanoid");
		createMob(projectId, "Augur", "flying");
		createMob(projectId, "Tejedora", "arachnid");

		mockMvc.perform(get("/api/mobs/recent").param("limit", "2"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(2)))
				.andExpect(jsonPath("$[0].name", is("Tejedora")))
				.andExpect(jsonPath("$[1].name", is("Augur")));
	}

	@Test
	void sin_limit_usa_el_default_de_3() throws Exception {
		UUID projectId = aProject("Galgoth");
		createMob(projectId, "Carcomido", "humanoid");
		createMob(projectId, "Augur", "flying");
		createMob(projectId, "Tejedora", "arachnid");
		createMob(projectId, "Áugur II", "flying");

		mockMvc.perform(get("/api/mobs/recent"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(3)));
	}

	@Test
	void un_limit_invalido_cae_al_default_en_vez_de_400() throws Exception {
		UUID projectId = aProject("Galgoth");
		createMob(projectId, "Carcomido", "humanoid");

		mockMvc.perform(get("/api/mobs/recent").param("limit", "0"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)));
	}

	@Test
	void excluye_mobs_eliminados_y_mobs_de_proyectos_eliminados() throws Exception {
		UUID keptProject = aProject("Galgoth");
		UUID deletedProject = aProject("Proyecto descartado");
		String deletedMobId = createMob(keptProject, "Carcomido", "humanoid");
		createMob(deletedProject, "Fantasma", "custom");
		createMob(keptProject, "Tejedora", "arachnid");

		mockMvc.perform(delete("/api/mobs/{mobId}", deletedMobId));
		jdbc.update("update projects set deleted_at = now() where id = ?", deletedProject);

		mockMvc.perform(get("/api/mobs/recent"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].name", is("Tejedora")));
	}

}
