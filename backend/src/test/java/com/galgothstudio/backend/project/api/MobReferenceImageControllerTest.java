package com.galgothstudio.backend.project.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.galgothstudio.backend.TestcontainersConfiguration;
import java.util.Base64;
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
 * Integración de punta a punta (Testcontainers -- Postgres Y MinIO
 * reales -- + MockMvc) de la subida de imagen de referencia, ticket 024.
 * MinIO se configura globalmente vía `TestcontainersConfiguration` (ver
 * esa clase para el porqué -- mismo hallazgo real del ticket 023).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MobReferenceImageControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private ObjectMapper objectMapper;

	// PNG 1x1 real -- válido de verdad, mínimo posible (mismo fixture que MobThumbnailControllerTest).
	private static final byte[] TINY_PNG =
			Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");

	// JPEG 1x1 real (distinto formato del PNG de arriba -- necesario para probar que ambos content-types se aceptan).
	private static final byte[] TINY_JPEG = Base64.getDecoder()
			.decode(
					"/9j/4AAQSkZJRgABAQAAAQABAAD/2wBDAAMCAgICAgMCAgIDAwMDBAYEBAQEBAgGBgUGCQgKCgkICQkKDA8MCgsOCwkJDRENDg8QEBEQCgwSExIQEw8QEBD/wAALCAABAAEBAREA/8QAFAABAAAAAAAAAAAAAAAAAAAACP/EABQQAQAAAAAAAAAAAAAAAAAAAAD/2gAIAQEAAD8AVt//2Q==");

	private UUID aProjectAndMob() {
		UUID projectId = UUID.randomUUID();
		jdbc.update("insert into projects (id, name) values (?, ?)", projectId, "Galgoth");
		UUID mobId = UUID.randomUUID();
		jdbc.update(
				"insert into mobs (id, project_id, name, base_type, status) values (?, ?, ?, ?, ?)",
				mobId, projectId, "Carcomido", "humanoid", "draft");
		return mobId;
	}

	/** También cubre un hallazgo real: `MockHttpServletRequestBuilder.contentType(...)` agrega `;charset=UTF-8` incluso a un tipo binario -- si el servicio comparara el header crudo por igualdad de string en vez de normalizar tipo/subtipo, esta imagen PNG válida sería rechazada. */
	@Test
	void subir_un_png_valido_devuelve_201_con_ancho_alto_y_content_type_reales_AC1_AC3() throws Exception {
		UUID mobId = aProjectAndMob();

		mockMvc.perform(post("/api/mobs/{mobId}/references", mobId).contentType(MediaType.IMAGE_PNG).content(TINY_PNG))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.width", is(1)))
				.andExpect(jsonPath("$.height", is(1)))
				.andExpect(jsonPath("$.contentType", is("image/png")))
				.andExpect(jsonPath("$.url", startsWith("/api/mobs/" + mobId + "/references/")));
	}

	@Test
	void subir_un_jpeg_valido_tambien_se_acepta() throws Exception {
		UUID mobId = aProjectAndMob();

		mockMvc.perform(post("/api/mobs/{mobId}/references", mobId).contentType(MediaType.IMAGE_JPEG).content(TINY_JPEG))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.contentType", is("image/jpeg")));
	}

	@Test
	void subir_y_descargar_una_referencia_devuelve_los_mismos_bytes_y_su_content_type_real() throws Exception {
		UUID mobId = aProjectAndMob();

		String responseJson = mockMvc.perform(
						post("/api/mobs/{mobId}/references", mobId).contentType(MediaType.IMAGE_PNG).content(TINY_PNG))
				.andReturn()
				.getResponse()
				.getContentAsString();
		String referenceId = objectMapper.readTree(responseJson).get("id").asText();

		mockMvc.perform(get("/api/mobs/{mobId}/references/{referenceId}", mobId, referenceId))
				.andExpect(status().isOk())
				.andExpect(header().string("Content-Type", MediaType.IMAGE_PNG_VALUE))
				.andExpect(result -> assertThat(result.getResponse().getContentAsByteArray()).isEqualTo(TINY_PNG));
	}

	@Test
	void listar_devuelve_las_referencias_en_orden_de_subida() throws Exception {
		UUID mobId = aProjectAndMob();
		mockMvc.perform(post("/api/mobs/{mobId}/references", mobId).contentType(MediaType.IMAGE_PNG).content(TINY_PNG));
		mockMvc.perform(post("/api/mobs/{mobId}/references", mobId).contentType(MediaType.IMAGE_JPEG).content(TINY_JPEG));

		mockMvc.perform(get("/api/mobs/{mobId}/references", mobId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(2)))
				.andExpect(jsonPath("$[0].contentType", is("image/png")))
				.andExpect(jsonPath("$[1].contentType", is("image/jpeg")));
	}

	@Test
	void subir_un_formato_no_soportado_es_rechazado_con_mensaje_claro_AC2() throws Exception {
		UUID mobId = aProjectAndMob();

		mockMvc.perform(post("/api/mobs/{mobId}/references", mobId)
						.contentType(MediaType.IMAGE_GIF)
						.content(new byte[] {1, 2, 3}))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error", is("INVALID_REFERENCE_IMAGE")))
				.andExpect(jsonPath("$.message", containsString("image/gif")));
	}

	@Test
	void subir_un_archivo_que_excede_el_tamano_maximo_es_rechazado_AC2() throws Exception {
		UUID mobId = aProjectAndMob();
		// No hace falta que sea una imagen real -- la validación de tamaño ocurre ANTES de decodificar.
		// 10MB es el máximo soportado (VoBo del PO, ticket 024) -- 1 byte de más basta para rechazar.
		byte[] tooLarge = new byte[10 * 1024 * 1024 + 1];

		mockMvc.perform(post("/api/mobs/{mobId}/references", mobId).contentType(MediaType.IMAGE_PNG).content(tooLarge))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error", is("INVALID_REFERENCE_IMAGE")));
	}

	@Test
	void subir_bytes_no_decodificables_como_imagen_es_rechazado_AC2() throws Exception {
		UUID mobId = aProjectAndMob();

		mockMvc.perform(post("/api/mobs/{mobId}/references", mobId)
						.contentType(MediaType.IMAGE_PNG)
						.content(new byte[] {1, 2, 3, 4, 5}))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error", is("INVALID_REFERENCE_IMAGE")));
	}

	@Test
	void subir_una_referencia_a_un_mob_inexistente_responde_404() throws Exception {
		mockMvc.perform(post("/api/mobs/{mobId}/references", UUID.randomUUID())
						.contentType(MediaType.IMAGE_PNG)
						.content(TINY_PNG))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error", is("MOB_NOT_FOUND")));
	}

	@Test
	void listar_referencias_de_un_mob_inexistente_responde_404() throws Exception {
		mockMvc.perform(get("/api/mobs/{mobId}/references", UUID.randomUUID()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error", is("MOB_NOT_FOUND")));
	}

	@Test
	void descargar_una_referencia_inexistente_responde_404() throws Exception {
		UUID mobId = aProjectAndMob();

		mockMvc.perform(get("/api/mobs/{mobId}/references/{referenceId}", mobId, UUID.randomUUID()))
				.andExpect(status().isNotFound());
	}

}
