package com.galgothstudio.backend.project.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.galgothstudio.backend.TestcontainersConfiguration;
import com.galgothstudio.backend.asset.AssetStorageService;
import com.galgothstudio.backend.domain.model.BaseType;
import com.galgothstudio.backend.domain.model.ExportSettings;
import com.galgothstudio.backend.domain.model.FormatVersion;
import com.galgothstudio.backend.domain.model.MobProjectModel;
import com.galgothstudio.backend.domain.model.TextureDocument;
import com.galgothstudio.backend.domain.model.UvLayout;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import javax.imageio.ImageIO;
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
 * reales -- + MockMvc) de `PUT /api/mobs/{mobId}/texture`, ticket 045
 * (`docs/definiciones/galgoth-studio-fase3-textura.md`, Diseño técnico
 * §6). Mismo patrón que {@code MobThumbnailControllerTest} (023).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MobTextureControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private AssetStorageService assetStorageService;

	@Autowired
	private ObjectMapper objectMapper;

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

	/** Modelo mínimo, sin bones/cuboids (irrelevantes para el ticket 066) -- solo el `texture.storageKey` importa. */
	private String modelJsonWithTexture(String mobId, String storageKey) throws Exception {
		TextureDocument texture = new TextureDocument(64, 64, storageKey);
		MobProjectModel model = new MobProjectModel(
				mobId, "project-1", "Test Mob", BaseType.HUMANOID, MobProjectModel.UNITS_MINECRAFT_PIXELS, List.of(), List.of(),
				texture, new UvLayout(64, 64, List.of()), List.of(), new ExportSettings(FormatVersion.V5), List.of());
		return objectMapper.writeValueAsString(model);
	}

	/** Igual que `aProjectAndMob()`, pero además con un draft cuyo `texture.storageKey` es el dado (o `null`). */
	private UUID aMobWithDraft(String textureStorageKeyOrNull) throws Exception {
		UUID mobId = aProjectAndMob();
		String modelJson = modelJsonWithTexture(mobId.toString(), textureStorageKeyOrNull);
		jdbc.update("insert into mob_drafts (mob_id, draft_model_jsonb, draft_version) values (?, ?::jsonb, 1)", mobId, modelJson);
		return mobId;
	}

	/** Mismos bytes canónicos que `TextureService` produciría -- decodifica y re-codifica como PNG, igual que el propio servicio. */
	private static String expectedStorageKeyFor(byte[] rawPngBytes) throws Exception {
		BufferedImage image = ImageIO.read(new ByteArrayInputStream(rawPngBytes));
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(image, "png", out);
		byte[] digest = java.security.MessageDigest.getInstance("SHA-256").digest(out.toByteArray());
		return "textures/" + java.util.HexFormat.of().formatHex(digest) + ".png";
	}

	@Test
	void subir_una_textura_valida_devuelve_el_storageKey_content_addressed_calculado_por_el_backend() throws Exception {
		UUID mobId = aProjectAndMob();
		String expectedStorageKey = expectedStorageKeyFor(TINY_PNG);

		mockMvc.perform(put("/api/mobs/{mobId}/texture", mobId).contentType(MediaType.IMAGE_PNG).content(TINY_PNG))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.storageKey", is(expectedStorageKey)));

		assertThat(assetStorageService.exists(expectedStorageKey)).isTrue();
	}

	@Test
	void subir_la_misma_textura_dos_veces_devuelve_el_mismo_storageKey_y_no_vuelve_a_subir_AC_idempotencia() throws Exception {
		UUID mobId = aProjectAndMob();
		String expectedStorageKey = expectedStorageKeyFor(TINY_PNG);

		mockMvc.perform(put("/api/mobs/{mobId}/texture", mobId).contentType(MediaType.IMAGE_PNG).content(TINY_PNG))
				.andExpect(status().isOk());

		// Sustituye manualmente el contenido de la key ya existente por un
		// "centinela" -- si la segunda subida detecta correctamente que la
		// key YA existe y por lo tanto NO vuelve a subir, el centinela debe
		// sobrevivir intacto (una segunda escritura real lo sobreescribiría
		// con los bytes canónicos de TINY_PNG).
		byte[] sentinel = {9, 9, 9};
		assetStorageService.put(expectedStorageKey, sentinel, "image/png");

		mockMvc.perform(put("/api/mobs/{mobId}/texture", mobId).contentType(MediaType.IMAGE_PNG).content(TINY_PNG))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.storageKey", is(expectedStorageKey)));

		assertThat(assetStorageService.get(expectedStorageKey)).contains(sentinel);
	}

	@Test
	void el_storageKey_devuelto_ignora_por_completo_cualquier_valor_propuesto_por_el_cliente() throws Exception {
		UUID mobId = aProjectAndMob();
		String expectedStorageKey = expectedStorageKeyFor(TINY_PNG);

		// El contrato del endpoint (bytes crudos, sin JSON) no tiene ningún
		// campo para que el cliente proponga un storageKey -- este header
		// deliberadamente incorrecto demuestra que, aunque un cliente lo
		// intentara colar de cualquier forma fuera del contrato, la
		// respuesta sigue siendo exclusivamente la que el backend calculó.
		mockMvc.perform(put("/api/mobs/{mobId}/texture", mobId)
						.contentType(MediaType.IMAGE_PNG)
						.header("X-Client-Computed-Storage-Key", "textures/deliberadamente-incorrecto.png")
						.content(TINY_PNG))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.storageKey", is(expectedStorageKey)));
	}

	@Test
	void subir_bytes_que_no_decodifican_como_png_responde_400_INVALID_TEXTURE() throws Exception {
		UUID mobId = aProjectAndMob();
		byte[] notAPng = {1, 2, 3, 4, 5};

		mockMvc.perform(put("/api/mobs/{mobId}/texture", mobId).contentType(MediaType.IMAGE_PNG).content(notAPng))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error", is("INVALID_TEXTURE")));
	}

	@Test
	void subir_una_textura_a_un_mob_inexistente_responde_404() throws Exception {
		mockMvc.perform(put("/api/mobs/{mobId}/texture", UUID.randomUUID())
						.contentType(MediaType.IMAGE_PNG)
						.content(TINY_PNG))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error", is("MOB_NOT_FOUND")));
	}

	/**
	 * Ticket 066 (hallazgo real, reportado por el PO): sin este endpoint,
	 * el frontend no tenía forma de recuperar la textura ya persistida al
	 * recargar la página -- el editor mostraba un atlas vacío aunque el
	 * mob tuviera una textura real guardada.
	 */
	@Test
	void descargar_la_textura_ya_subida_devuelve_sus_bytes_exactos_AC_hallazgo_real() throws Exception {
		// storageKey content-addressed real (mismo cálculo que TextureService.upload) -- este test
		// no ejercita el upload en sí, solo el download por un storageKey ya vigente en el draft.
		String storageKey = expectedStorageKeyFor(TINY_PNG);
		assetStorageService.put(storageKey, TINY_PNG, "image/png");
		UUID mobId = aMobWithDraft(storageKey);

		mockMvc.perform(get("/api/mobs/{mobId}/texture", mobId))
				.andExpect(status().isOk())
				.andExpect(content().contentType(MediaType.IMAGE_PNG))
				.andExpect(content().bytes(TINY_PNG));
	}

	@Test
	void descargar_la_textura_de_un_mob_con_draft_pero_sin_textura_todavia_responde_404() throws Exception {
		UUID mobId = aMobWithDraft(null);

		mockMvc.perform(get("/api/mobs/{mobId}/texture", mobId)).andExpect(status().isNotFound());
	}

	@Test
	void descargar_la_textura_de_un_mob_sin_ningun_draft_todavia_responde_404() throws Exception {
		UUID mobId = aProjectAndMob(); // sin insertar mob_drafts

		mockMvc.perform(get("/api/mobs/{mobId}/texture", mobId))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error", is("DRAFT_NOT_FOUND")));
	}

	@Test
	void descargar_la_textura_de_un_mob_inexistente_responde_404() throws Exception {
		mockMvc.perform(get("/api/mobs/{mobId}/texture", UUID.randomUUID()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error", is("MOB_NOT_FOUND")));
	}

}
