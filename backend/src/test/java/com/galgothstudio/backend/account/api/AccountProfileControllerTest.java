package com.galgothstudio.backend.account.api;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.galgothstudio.backend.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integración de punta a punta (Testcontainers + Postgres real + MockMvc)
 * del perfil de producto, ticket 091. Mismo patrón que
 * {@link com.galgothstudio.backend.project.api.ProjectControllerTest}
 * (ticket 084), incluido el JWT simulado sin red vía `spring-security-test`.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AccountProfileControllerTest {

	private static final String USER_ID = "6e2f6b3a-1111-4a2b-9c3d-8f7e6d5c4b3a";

	@Autowired
	private MockMvc mockMvc;

	private static RequestPostProcessor authenticated() {
		return authenticated(USER_ID);
	}

	private static RequestPostProcessor authenticated(String userId) {
		return jwt().jwt(builder -> builder.subject(userId));
	}

	private String preferencesBody(boolean notifyEmail, boolean notifyProductNews, boolean notifySaveReminders) {
		return "{\"notifyEmail\":" + notifyEmail + ",\"notifyProductNews\":" + notifyProductNews + ",\"notifySaveReminders\":"
				+ notifySaveReminders + "}";
	}

	@Test
	void un_usuario_sin_perfil_todavia_obtiene_los_defaults_sin_crear_ninguna_fila() throws Exception {
		mockMvc.perform(get("/api/account/profile").with(authenticated()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.avatarUrl", is(nullValue())))
				.andExpect(jsonPath("$.notifyEmail", is(true)))
				.andExpect(jsonPath("$.notifyProductNews", is(true)))
				.andExpect(jsonPath("$.notifySaveReminders", is(true)));
	}

	@Test
	void obtener_el_perfil_sin_autenticacion_responde_401() throws Exception {
		mockMvc.perform(get("/api/account/profile"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error", is("UNAUTHENTICATED")));
	}

	@Test
	void guardar_preferencias_se_refleja_en_el_get_subsecuente() throws Exception {
		mockMvc.perform(patch("/api/account/preferences").with(authenticated())
						.contentType(MediaType.APPLICATION_JSON)
						.content(preferencesBody(false, false, true)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.notifyEmail", is(false)))
				.andExpect(jsonPath("$.notifyProductNews", is(false)))
				.andExpect(jsonPath("$.notifySaveReminders", is(true)));

		mockMvc.perform(get("/api/account/profile").with(authenticated()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.notifyEmail", is(false)))
				.andExpect(jsonPath("$.notifyProductNews", is(false)))
				.andExpect(jsonPath("$.notifySaveReminders", is(true)));
	}

	@Test
	void guardar_preferencias_sin_autenticacion_responde_401() throws Exception {
		mockMvc.perform(patch("/api/account/preferences")
						.contentType(MediaType.APPLICATION_JSON)
						.content(preferencesBody(true, true, true)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error", is("UNAUTHENTICATED")));
	}

	@Test
	void las_preferencias_de_un_usuario_no_afectan_las_de_otro() throws Exception {
		String otroUserId = "9c3e3b1a-2222-4d3d-8888-0f1a2b3c4d5e";
		mockMvc.perform(patch("/api/account/preferences").with(authenticated())
				.contentType(MediaType.APPLICATION_JSON)
				.content(preferencesBody(false, false, false)));

		mockMvc.perform(get("/api/account/profile").with(authenticated(otroUserId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.notifyEmail", is(true)))
				.andExpect(jsonPath("$.notifyProductNews", is(true)))
				.andExpect(jsonPath("$.notifySaveReminders", is(true)));
	}

}
