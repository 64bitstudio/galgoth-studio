package com.galgothstudio.backend.project.api;

import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integración de punta a punta (Testcontainers + Postgres real + MockMvc)
 * de `GET /api/mobs/{mobId}` (ticket 034) -- ruta ya prevista desde el
 * bootstrap del proyecto (`docs/API.md`, "Rutas previstas"), necesaria
 * para que `MobEditor.vue` sepa `name`/`baseType` reales del mob cuando
 * todavía no existe ningún draft con qué arrancar. Ticket 039 agrega
 * `PATCH`/`DELETE` (Renombrar/Eliminar, mismo criterio de soft-delete
 * que `ProjectControllerTest`).
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

	/** Ticket 085 -- `sub` constante para el archivo completo, mismo patrón que {@link ProjectControllerTest}. */
	private static final String OWNER_ID = "4635300a-5049-4cd5-933d-a37b807c83b0";

	private static RequestPostProcessor authenticated() {
		return jwt().jwt(builder -> builder.subject(OWNER_ID));
	}

	/** Ticket 085 -- `owner_ref` real (084) para que el guard de acceso reconozca a {@code OWNER_ID} como dueño. */
	private UUID aProjectAndMob() {
		UUID projectId = UUID.randomUUID();
		jdbc.update("insert into projects (id, name, owner_ref) values (?, ?, ?)", projectId, "Galgoth", OWNER_ID);
		UUID mobId = UUID.randomUUID();
		jdbc.update(
				"insert into mobs (id, project_id, name, base_type, status) values (?, ?, ?, ?, ?)",
				mobId, projectId, "Carcomido", "humanoid", "draft");
		return mobId;
	}

	@Test
	void obtener_un_mob_existente_devuelve_su_resumen_real() throws Exception {
		UUID mobId = aProjectAndMob();

		mockMvc.perform(get("/api/mobs/{mobId}", mobId).with(authenticated()))
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

	// -- PATCH /api/mobs/{mobId} (ticket 039, Renombrar) ---------------------

	@Test
	void renombrar_actualiza_el_nombre_y_se_refleja_en_un_GET_posterior() throws Exception {
		UUID mobId = aProjectAndMob();

		mockMvc.perform(patch("/api/mobs/{mobId}", mobId).with(authenticated())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"Nuevo nombre\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name", is("Nuevo nombre")));

		mockMvc.perform(get("/api/mobs/{mobId}", mobId).with(authenticated())).andExpect(jsonPath("$.name", is("Nuevo nombre")));
	}

	@Test
	void renombrar_con_nombre_vacio_falla_explicito_sin_cambiar_nada() throws Exception {
		UUID mobId = aProjectAndMob();

		mockMvc.perform(patch("/api/mobs/{mobId}", mobId).with(authenticated())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error", is("INVALID_MOB_REQUEST")));

		mockMvc.perform(get("/api/mobs/{mobId}", mobId).with(authenticated())).andExpect(jsonPath("$.name", is("Carcomido")));
	}

	@Test
	void renombrar_un_mob_inexistente_responde_404() throws Exception {
		mockMvc.perform(patch("/api/mobs/{mobId}", UUID.randomUUID()).with(authenticated())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"X\"}"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error", is("MOB_NOT_FOUND")));
	}

	// -- DELETE /api/mobs/{mobId} (ticket 039, soft-delete) ------------------

	@Test
	void eliminar_un_mob_hace_que_deje_de_existir_para_cualquier_consulta_posterior() throws Exception {
		UUID mobId = aProjectAndMob();

		mockMvc.perform(delete("/api/mobs/{mobId}", mobId).with(authenticated())).andExpect(status().isNoContent());

		mockMvc.perform(get("/api/mobs/{mobId}", mobId).with(authenticated())).andExpect(status().isNotFound());
	}

	@Test
	void eliminar_un_mob_inexistente_responde_404() throws Exception {
		mockMvc.perform(delete("/api/mobs/{mobId}", UUID.randomUUID()).with(authenticated())).andExpect(status().isNotFound());
	}

}
