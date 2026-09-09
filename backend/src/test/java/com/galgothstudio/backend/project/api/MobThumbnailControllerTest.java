package com.galgothstudio.backend.project.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.galgothstudio.backend.TestcontainersConfiguration;
import jakarta.persistence.EntityManager;
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
 * reales -- + MockMvc) del pipeline de thumbnails, ticket 023. MinIO no
 * tiene `@ServiceConnection` nativo -- las propiedades
 * `galgoth.storage.minio.*` se publican como System properties en el
 * `static` de `TestcontainersConfiguration` (nunca el MinIO persistente
 * de `docker/docker-compose.yml`, que es solo para dev manual) -- ver esa
 * clase para el hallazgo real de por qué NO se declaran acá con
 * `@DynamicPropertySource` (rompería cualquier otro `@SpringBootTest`
 * ajeno a thumbnails).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MobThumbnailControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private EntityManager entityManager;

	/** Ver la nota en {@link MobDraftControllerTest} (ticket 020) sobre por qué esto hace falta antes de leer vía JDBC crudo dentro de la misma transacción de test. */
	private void flush() {
		entityManager.flush();
	}

	// PNG 1x1 real (no un array de bytes arbitrario) -- válido de verdad, mínimo posible.
	private static final byte[] TINY_PNG =
			Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");

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
	void subir_un_thumbnail_y_descargarlo_devuelve_los_mismos_bytes() throws Exception {
		UUID mobId = aProjectAndMob();

		mockMvc.perform(post("/api/mobs/{mobId}/thumbnail", mobId).contentType(MediaType.IMAGE_PNG).content(TINY_PNG))
				.andExpect(status().isNoContent());

		byte[] downloaded = mockMvc.perform(get("/api/mobs/{mobId}/thumbnail", mobId))
				.andExpect(status().isOk())
				.andExpect(header().string("Content-Type", MediaType.IMAGE_PNG_VALUE))
				.andReturn()
				.getResponse()
				.getContentAsByteArray();

		assertThat(downloaded).isEqualTo(TINY_PNG);
	}

	@Test
	void subir_un_thumbnail_actualiza_mobs_thumbnail_key_con_una_ruta_servible_por_la_api() throws Exception {
		UUID mobId = aProjectAndMob();

		mockMvc.perform(post("/api/mobs/{mobId}/thumbnail", mobId).contentType(MediaType.IMAGE_PNG).content(TINY_PNG));

		flush();
		String thumbnailKey = jdbc.queryForObject("select thumbnail_key from mobs where id = ?", String.class, mobId);
		assertThat(thumbnailKey).isEqualTo("/api/mobs/" + mobId + "/thumbnail");
	}

	@Test
	void descargar_el_thumbnail_de_un_mob_sin_thumbnail_todavia_responde_404_AC4() throws Exception {
		UUID mobId = aProjectAndMob();

		mockMvc.perform(get("/api/mobs/{mobId}/thumbnail", mobId)).andExpect(status().isNotFound());
	}

	@Test
	void subir_un_thumbnail_a_un_mob_inexistente_responde_404() throws Exception {
		mockMvc.perform(post("/api/mobs/{mobId}/thumbnail", UUID.randomUUID())
						.contentType(MediaType.IMAGE_PNG)
						.content(TINY_PNG))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error", is("MOB_NOT_FOUND")));
	}

	@Test
	void descargar_el_thumbnail_de_un_mob_inexistente_responde_404() throws Exception {
		mockMvc.perform(get("/api/mobs/{mobId}/thumbnail", UUID.randomUUID())).andExpect(status().isNotFound());
	}

	@Test
	void subir_un_segundo_thumbnail_sobrescribe_el_anterior() throws Exception {
		UUID mobId = aProjectAndMob();
		mockMvc.perform(post("/api/mobs/{mobId}/thumbnail", mobId).contentType(MediaType.IMAGE_PNG).content(TINY_PNG));

		byte[] secondPng = TINY_PNG.clone();
		secondPng[secondPng.length - 1] = 0; // distinto contenido, sigue siendo bytes válidos para este test
		mockMvc.perform(post("/api/mobs/{mobId}/thumbnail", mobId).contentType(MediaType.IMAGE_PNG).content(secondPng));

		byte[] downloaded = mockMvc.perform(get("/api/mobs/{mobId}/thumbnail", mobId))
				.andReturn()
				.getResponse()
				.getContentAsByteArray();
		assertThat(downloaded).isEqualTo(secondPng);
	}

}
