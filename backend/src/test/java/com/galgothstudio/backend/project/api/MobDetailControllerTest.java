package com.galgothstudio.backend.project.api;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.galgothstudio.backend.TestcontainersConfiguration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integración de punta a punta (Testcontainers + Postgres real + MockMvc)
 * de `GET /api/mobs/{mobId}` (ticket 034) -- ruta ya prevista desde el
 * bootstrap del proyecto (`docs/API.md`, "Rutas previstas"), necesaria
 * para que `MobEditor.vue` sepa `name`/`baseType` reales del mob cuando
 * todavía no existe ningún draft con qué arrancar.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MobDetailControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbc;

	private UUID aProjectAndMob() {
		UUID projectId = UUID.randomUUID();
		jdbc.update("insert into projects (id, name) values (?, ?)", projectId, "Galgoth");
		UUID mobId = UUID.randomUUID();
		jdbc.update(
				"insert into mobs (id, project_id, name, base_type, status) values (?, ?, ?, ?, ?)",
				mobId, projectId, "Carcomido", "humanoid", "draft");
		return mobId;
	}

	@Test
	void obtener_un_mob_existente_devuelve_su_resumen_real() throws Exception {
		UUID mobId = aProjectAndMob();

		mockMvc.perform(get("/api/mobs/{mobId}", mobId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id", is(mobId.toString())))
				.andExpect(jsonPath("$.name", is("Carcomido")))
				.andExpect(jsonPath("$.baseType", is("humanoid")))
				.andExpect(jsonPath("$.status", is("draft")));
	}

	@Test
	void obtener_un_mob_inexistente_responde_404() throws Exception {
		mockMvc.perform(get("/api/mobs/{mobId}", UUID.randomUUID()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error", is("MOB_NOT_FOUND")));
	}

}
