package com.galgothstudio.backend.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.galgothstudio.backend.TestcontainersConfiguration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integración de punta a punta (Testcontainers + Postgres real + MockMvc)
 * de la purga de datos al eliminar una cuenta, ticket 091. A diferencia
 * de {@code ProjectControllerTest}/{@code AccountProfileControllerTest},
 * este endpoint NO usa JWT (ver Javadoc de {@link InternalSecretAuthenticator}) --
 * se autentica con el header `X-Internal-Secret` contra
 * `galgoth.internal.secret` (default de test: `application.properties`).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class InternalPurgeControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Value("${galgoth.internal.secret}")
	private String realSecret;

	private UUID aProjectOf(String ownerId, String visibility) {
		UUID projectId = UUID.randomUUID();
		jdbc.update(
				"insert into projects (id, name, owner_ref, visibility) values (?, ?, ?, ?)",
				projectId, "Proyecto de " + ownerId, ownerId, visibility);
		return projectId;
	}

	private String preferencesBody() {
		return "{\"notifyEmail\":false,\"notifyProductNews\":false,\"notifySaveReminders\":false}";
	}

	@Test
	void purgar_con_el_secreto_correcto_soft_deletea_todos_los_proyectos_publicos_y_privados_y_borra_el_perfil()
			throws Exception {
		String ownerId = UUID.randomUUID().toString();
		UUID publicProjectId = aProjectOf(ownerId, "PUBLIC");
		UUID privateProjectId = aProjectOf(ownerId, "PRIVATE");
		mockMvc.perform(patch("/api/account/preferences")
				.with(jwt().jwt(builder -> builder.subject(ownerId)))
				.contentType(MediaType.APPLICATION_JSON)
				.content(preferencesBody()));

		mockMvc.perform(post("/api/internal/users/{userId}/purge-projects", ownerId).header("X-Internal-Secret", realSecret))
				.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/projects/{id}", publicProjectId)).andExpect(status().isNotFound());
		mockMvc.perform(get("/api/projects/{id}", privateProjectId)).andExpect(status().isNotFound());
		Integer profileCount =
				jdbc.queryForObject("select count(*) from user_profile where user_id = ?", Integer.class, UUID.fromString(ownerId));
		assertThat(profileCount).isZero();
	}

	@Test
	void purgar_no_afecta_los_proyectos_de_otro_usuario() throws Exception {
		String ownerId = UUID.randomUUID().toString();
		String otroOwnerId = UUID.randomUUID().toString();
		aProjectOf(ownerId, "PRIVATE");
		UUID ajenoProjectId = aProjectOf(otroOwnerId, "PUBLIC");

		mockMvc.perform(post("/api/internal/users/{userId}/purge-projects", ownerId).header("X-Internal-Secret", realSecret))
				.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/projects/{id}", ajenoProjectId)).andExpect(status().isOk());
	}

	@Test
	void purgar_con_un_secreto_incorrecto_es_rechazado_y_no_toca_nada() throws Exception {
		String ownerId = UUID.randomUUID().toString();
		UUID projectId = aProjectOf(ownerId, "PRIVATE");

		mockMvc.perform(post("/api/internal/users/{userId}/purge-projects", ownerId).header("X-Internal-Secret", "secreto-incorrecto"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error", is("INVALID_INTERNAL_SECRET")));

		// Ticket 085 -- el proyecto es PRIVATE: confirmar que sigue existiendo
		// exige mandar el JWT de su propio dueño (una lectura anónima ahora
		// respondería 404 igual, sin distinguir "no existe" de "no es tuyo").
		mockMvc.perform(get("/api/projects/{id}", projectId).with(jwt().jwt(builder -> builder.subject(ownerId))))
				.andExpect(status().isOk());
	}

	@Test
	void purgar_sin_el_header_es_rechazado() throws Exception {
		String ownerId = UUID.randomUUID().toString();

		mockMvc.perform(post("/api/internal/users/{userId}/purge-projects", ownerId))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error", is("INVALID_INTERNAL_SECRET")));
	}

	@Test
	void purgar_un_usuario_sin_proyectos_ni_perfil_no_falla() throws Exception {
		String ownerId = UUID.randomUUID().toString();

		mockMvc.perform(post("/api/internal/users/{userId}/purge-projects", ownerId).header("X-Internal-Secret", realSecret))
				.andExpect(status().isNoContent());
	}

}
