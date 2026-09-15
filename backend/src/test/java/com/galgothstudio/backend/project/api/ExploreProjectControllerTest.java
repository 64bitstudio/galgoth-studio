package com.galgothstudio.backend.project.api;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.galgothstudio.backend.TestcontainersConfiguration;
import java.sql.Timestamp;
import java.time.Instant;
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
 * de "Explorar" (ticket 086, HU-5) -- escaparate público, sin sesión.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ExploreProjectControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbc;

	private UUID aProjectOf(String ownerId, String visibility, String name, String ownerDisplayName) {
		return aProjectOf(ownerId, visibility, name, ownerDisplayName, Instant.now());
	}

	/** Variante con `updatedAt` explícito -- determinismo real para probar el orden "más reciente primero", que insertar 2 filas seguidas por JDBC (mismo `now()` a nivel de milisegundo) no garantiza. */
	private UUID aProjectOf(String ownerId, String visibility, String name, String ownerDisplayName, Instant updatedAt) {
		UUID id = UUID.randomUUID();
		jdbc.update(
				"insert into projects (id, name, owner_ref, visibility, owner_display_name, updated_at) values (?, ?, ?, ?, ?, ?)",
				id, name, ownerId, visibility, ownerDisplayName, Timestamp.from(updatedAt));
		return id;
	}

	@Test
	void lista_proyectos_publicos_de_cualquier_dueno_sin_exigir_sesion() throws Exception {
		String ownerA = UUID.randomUUID().toString();
		String ownerB = UUID.randomUUID().toString();
		Instant now = Instant.now();
		aProjectOf(ownerA, "PUBLIC", "Público de A", "Ada Lovelace", now.minusSeconds(60));
		aProjectOf(ownerB, "PUBLIC", "Público de B", "Grace Hopper", now);

		mockMvc.perform(get("/api/explore/projects"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(2)))
				.andExpect(jsonPath("$[0].name", is("Público de B"))) // más reciente primero (updated_at desc)
				.andExpect(jsonPath("$[0].ownerDisplayName", is("Grace Hopper")))
				.andExpect(jsonPath("$[1].name", is("Público de A")));
	}

	@Test
	void nunca_incluye_un_proyecto_privado() throws Exception {
		String ownerId = UUID.randomUUID().toString();
		aProjectOf(ownerId, "PRIVATE", "Privado", null);

		mockMvc.perform(get("/api/explore/projects")).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
	}

	@Test
	void nunca_incluye_un_proyecto_publico_soft_deleted() throws Exception {
		String ownerId = UUID.randomUUID().toString();
		UUID projectId = aProjectOf(ownerId, "PUBLIC", "Público pero borrado", null);
		jdbc.update("update projects set deleted_at = now() where id = ?", projectId);

		mockMvc.perform(get("/api/explore/projects")).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
	}

}
