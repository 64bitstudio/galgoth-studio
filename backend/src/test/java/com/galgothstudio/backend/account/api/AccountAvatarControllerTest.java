package com.galgothstudio.backend.account.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.galgothstudio.backend.TestcontainersConfiguration;
import java.util.Base64;
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
 * Integración de punta a punta (Testcontainers -- Postgres Y MinIO reales
 * -- + MockMvc) de la subida/descarga de avatar, ticket 091. Mismo
 * patrón que {@link com.galgothstudio.backend.project.api.MobReferenceImageControllerTest}
 * (ticket 024), incluidos los fixtures PNG/JPEG reales.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AccountAvatarControllerTest {

	private static final String USER_ID = "6e2f6b3a-1111-4a2b-9c3d-8f7e6d5c4b3a";

	// PNG 1x1 real -- mismo fixture que MobReferenceImageControllerTest/MobThumbnailControllerTest.
	private static final byte[] TINY_PNG =
			Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");

	@Autowired
	private MockMvc mockMvc;

	private static RequestPostProcessor authenticated() {
		return jwt().jwt(builder -> builder.subject(USER_ID));
	}

	@Test
	void subir_un_avatar_valido_y_descargarlo_devuelve_los_mismos_bytes() throws Exception {
		mockMvc.perform(post("/api/account/avatar").with(authenticated()).contentType(MediaType.IMAGE_PNG).content(TINY_PNG))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.avatarUrl", is("/api/account/avatar/" + USER_ID)));

		mockMvc.perform(get("/api/account/avatar/{userId}", USER_ID))
				.andExpect(status().isOk())
				.andExpect(header().string("Content-Type", MediaType.IMAGE_PNG_VALUE))
				.andExpect(result -> assertThat(result.getResponse().getContentAsByteArray()).isEqualTo(TINY_PNG));
	}

	/** Descargar el avatar es público a propósito (ticket 092, Explorar) -- sin `Authorization`. */
	@Test
	void descargar_un_avatar_no_exige_autenticacion() throws Exception {
		mockMvc.perform(post("/api/account/avatar").with(authenticated()).contentType(MediaType.IMAGE_PNG).content(TINY_PNG));

		mockMvc.perform(get("/api/account/avatar/{userId}", USER_ID)).andExpect(status().isOk());
	}

	@Test
	void descargar_el_avatar_de_un_usuario_sin_avatar_responde_404() throws Exception {
		mockMvc.perform(get("/api/account/avatar/{userId}", USER_ID)).andExpect(status().isNotFound());
	}

	@Test
	void subir_sin_autenticacion_responde_401() throws Exception {
		mockMvc.perform(post("/api/account/avatar").contentType(MediaType.IMAGE_PNG).content(TINY_PNG))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error", is("UNAUTHENTICATED")));
	}

	@Test
	void subir_un_formato_no_soportado_es_rechazado_con_mensaje_claro() throws Exception {
		mockMvc.perform(post("/api/account/avatar").with(authenticated())
						.contentType(MediaType.IMAGE_GIF)
						.content(new byte[] {1, 2, 3}))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error", is("INVALID_AVATAR")))
				.andExpect(jsonPath("$.message", containsString("image/gif")));
	}

	@Test
	void subir_un_archivo_que_excede_el_tamano_maximo_es_rechazado() throws Exception {
		// 5MB es el máximo soportado para avatares (ticket 091) -- menor que
		// el de imágenes de referencia (10MB, ticket 024): un avatar es una
		// foto pequeña, no concept art.
		byte[] tooLarge = new byte[5 * 1024 * 1024 + 1];

		mockMvc.perform(post("/api/account/avatar").with(authenticated())
						.contentType(MediaType.IMAGE_PNG)
						.content(tooLarge))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error", is("INVALID_AVATAR")));
	}

	@Test
	void subir_bytes_no_decodificables_como_imagen_es_rechazado() throws Exception {
		mockMvc.perform(post("/api/account/avatar").with(authenticated())
						.contentType(MediaType.IMAGE_PNG)
						.content(new byte[] {1, 2, 3, 4, 5}))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error", is("INVALID_AVATAR")));
	}

	@Test
	void resubir_un_avatar_reemplaza_el_anterior() throws Exception {
		mockMvc.perform(post("/api/account/avatar").with(authenticated()).contentType(MediaType.IMAGE_PNG).content(TINY_PNG));

		byte[] otroPng = Base64.getDecoder()
				.decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAIAAACQd1PeAAAADElEQVR4nGNgYGAAAAAEAAH2FzhVAAAAAElFTkSuQmCC");
		mockMvc.perform(post("/api/account/avatar").with(authenticated()).contentType(MediaType.IMAGE_PNG).content(otroPng))
				.andExpect(status().isOk());

		mockMvc.perform(get("/api/account/avatar/{userId}", USER_ID))
				.andExpect(status().isOk())
				.andExpect(result -> assertThat(result.getResponse().getContentAsByteArray()).isEqualTo(otroPng));
	}

}
