package com.galgothstudio.backend.project.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.galgothstudio.backend.TestcontainersConfiguration;
import com.galgothstudio.backend.asset.AssetStorageService;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integración de punta a punta (Testcontainers + Postgres real + MockMvc)
 * de `MobExportController` (ticket 032, HU-19). Síncrono, sin ningún
 * pipeline en otro hilo -- `@Transactional` de test normal, mismo
 * criterio que `AiEditControllerTest` (031).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MobExportControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private AssetStorageService assetStorageService;

	// PNG 1x1 real (mismo fixture que `MobTextureControllerTest`) -- válido de verdad, mínimo posible.
	private static final byte[] TINY_PNG =
			Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");

	private UUID aProjectAndMob(String name) {
		UUID projectId = UUID.randomUUID();
		jdbc.update("insert into projects (id, name) values (?, ?)", projectId, "Galgoth");
		UUID mobId = UUID.randomUUID();
		jdbc.update(
				"insert into mobs (id, project_id, name, base_type, status) values (?, ?, ?, ?, ?)",
				mobId, projectId, name, "humanoid", "draft");
		return mobId;
	}

	private String model(UUID mobId, UUID projectId, int cuboidSize) {
		return modelWithTexture(mobId, projectId, cuboidSize, null);
	}

	/** Ticket 056 (HU-43) -- variante con `texture.storageKey` seteado, para probar el export con textura real. */
	private String modelWithTexture(UUID mobId, UUID projectId, int cuboidSize, String storageKey) {
		String storageKeyJson = storageKey == null ? "null" : "\"" + storageKey + "\"";
		return """
				{
				  "mobId": "%s", "projectId": "%s", "name": "Carcomido", "baseType": "humanoid", "units": "minecraft_pixels",
				  "bones": [{"id":"body","name":"body","parentId":null,"pivot":[0,0,0],"rotation":[0,0,0]}],
				  "cuboids": [
				    {"id":"torso","name":"torso","boneId":"body","from":[0,0,0],"to":[%d,%d,%d],"origin":[0,0,0],"rotation":[0,0,0],
				     "faces":{"north":{"uv":[0,0,0,0],"texture":null},"south":{"uv":[0,0,0,0],"texture":null},"east":{"uv":[0,0,0,0],"texture":null},"west":{"uv":[0,0,0,0],"texture":null},"up":{"uv":[0,0,0,0],"texture":null},"down":{"uv":[0,0,0,0],"texture":null}}}
				  ],
				  "texture": {"width":64,"height":64,"storageKey":%s},
				  "uv": {"textureWidth":64,"textureHeight":64,"regions":[]},
				  "animations": [], "exportSettings": {"preferredFormatVersion":"v5"}, "referenceImages": []
				}
				"""
				.formatted(mobId, projectId, cuboidSize, cuboidSize, cuboidSize, storageKeyJson);
	}

	private UUID projectIdOf(UUID mobId) {
		return UUID.fromString(jdbc.queryForObject("select project_id from mobs where id = ?", String.class, mobId));
	}

	@Test
	void un_mob_completamente_nuevo_sin_draft_ni_revision_no_tiene_nada_que_exportar() throws Exception {
		UUID mobId = aProjectAndMob("Nuevo");

		mockMvc.perform(get("/api/mobs/{mobId}/export/status", mobId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.hasSavedRevision", is(false)))
				.andExpect(jsonPath("$.hasUnsavedChanges", is(false)))
				.andExpect(jsonPath("$.fmmCompatible").doesNotExist());

		mockMvc.perform(get("/api/mobs/{mobId}/export/bbmodel", mobId))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error", is("NO_SAVED_REVISION")));
	}

	@Test
	void un_mob_con_draft_pero_ninguna_revision_guardada_todavia_marca_cambios_sin_guardar_AC2() throws Exception {
		UUID mobId = aProjectAndMob("Recien creado");
		UUID projectId = projectIdOf(mobId);
		mockMvc.perform(patch("/api/mobs/{mobId}/draft", mobId).contentType(MediaType.APPLICATION_JSON).content(
						"{\"model\":" + model(mobId, projectId, 2) + "}"))
				.andExpect(status().isOk());

		mockMvc.perform(get("/api/mobs/{mobId}/export/status", mobId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.hasSavedRevision", is(false)))
				.andExpect(jsonPath("$.hasUnsavedChanges", is(true)))
				.andExpect(jsonPath("$.fmmCompatible").doesNotExist());

		mockMvc.perform(get("/api/mobs/{mobId}/export/bbmodel", mobId)).andExpect(status().isNotFound());
	}

	@Test
	void draft_identico_a_la_ultima_revision_no_marca_cambios_sin_guardar_y_expone_compatibilidad_real_AC1() throws Exception {
		UUID mobId = aProjectAndMob("Carcomido");
		UUID projectId = projectIdOf(mobId);
		String modelJson = model(mobId, projectId, 4);
		mockMvc.perform(patch("/api/mobs/{mobId}/draft", mobId).contentType(MediaType.APPLICATION_JSON).content("{\"model\":" + modelJson + "}"))
				.andExpect(status().isOk());
		mockMvc.perform(post("/api/mobs/{mobId}/revisions", mobId).contentType(MediaType.APPLICATION_JSON).content("{\"model\":" + modelJson + "}"))
				.andExpect(status().isCreated());

		mockMvc.perform(get("/api/mobs/{mobId}/export/status", mobId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.hasSavedRevision", is(true)))
				.andExpect(jsonPath("$.hasUnsavedChanges", is(false)))
				.andExpect(jsonPath("$.fmmCompatible", is(true)))
				.andExpect(jsonPath("$.fmmIssues", hasSize(0)));
	}

	@Test
	void draft_distinto_de_la_ultima_revision_marca_cambios_sin_guardar_AC2() throws Exception {
		UUID mobId = aProjectAndMob("Carcomido");
		UUID projectId = projectIdOf(mobId);
		mockMvc.perform(patch("/api/mobs/{mobId}/draft", mobId).contentType(MediaType.APPLICATION_JSON).content(
						"{\"model\":" + model(mobId, projectId, 4) + "}"))
				.andExpect(status().isOk());
		mockMvc.perform(post("/api/mobs/{mobId}/revisions", mobId).contentType(MediaType.APPLICATION_JSON).content(
						"{\"model\":" + model(mobId, projectId, 4) + "}"))
				.andExpect(status().isCreated());
		// autosave posterior con contenido DISTINTO -- el draft avanza, la revisión ya guardada no
		mockMvc.perform(patch("/api/mobs/{mobId}/draft", mobId).contentType(MediaType.APPLICATION_JSON).content(
						"{\"model\":" + model(mobId, projectId, 6) + "}"))
				.andExpect(status().isOk());

		mockMvc.perform(get("/api/mobs/{mobId}/export/status", mobId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.hasSavedRevision", is(true)))
				.andExpect(jsonPath("$.hasUnsavedChanges", is(true)));
	}

	@Test
	void una_revision_guardada_sin_ningun_draft_no_marca_cambios_sin_guardar() throws Exception {
		// "Guardar" llamado directo sin autosave previo (mismo edge case ya
		// documentado en 020/031) -- ninguna fila en mob_drafts todavía.
		UUID mobId = aProjectAndMob("Carcomido");
		UUID projectId = projectIdOf(mobId);
		mockMvc.perform(post("/api/mobs/{mobId}/revisions", mobId).contentType(MediaType.APPLICATION_JSON).content(
						"{\"model\":" + model(mobId, projectId, 4) + "}"))
				.andExpect(status().isCreated());

		mockMvc.perform(get("/api/mobs/{mobId}/export/status", mobId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.hasSavedRevision", is(true)))
				.andExpect(jsonPath("$.hasUnsavedChanges", is(false)));
	}

	@Test
	void exportar_bbmodel_devuelve_el_json_real_de_la_ultima_revision_con_filename_seguro_y_nunca_toca_el_draft() throws Exception {
		UUID mobId = aProjectAndMob("Carcomido Real!! 2");
		UUID projectId = projectIdOf(mobId);
		String modelJson = model(mobId, projectId, 4);
		mockMvc.perform(patch("/api/mobs/{mobId}/draft", mobId).contentType(MediaType.APPLICATION_JSON).content("{\"model\":" + modelJson + "}"))
				.andExpect(status().isOk());
		mockMvc.perform(post("/api/mobs/{mobId}/revisions", mobId).contentType(MediaType.APPLICATION_JSON).content("{\"model\":" + modelJson + "}"))
				.andExpect(status().isCreated());
		entityManager.flush();
		Integer draftVersionBefore = jdbc.queryForObject("select draft_version from mob_drafts where mob_id = ?", Integer.class, mobId);

		MvcResult result = mockMvc.perform(get("/api/mobs/{mobId}/export/bbmodel", mobId))
				.andExpect(status().isOk())
				.andExpect(header().string("Content-Disposition", containsString("Carcomido_Real_2.bbmodel")))
				.andReturn();

		JsonNode bbmodel = objectMapper.readTree(result.getResponse().getContentAsByteArray());
		assertThat(bbmodel.get("meta").get("format_version").asText()).isEqualTo("5.0");
		assertThat(bbmodel.get("elements")).hasSize(1);
		assertThat(bbmodel.get("elements").get(0).get("name").asText()).isEqualTo("torso");

		entityManager.flush();
		Integer draftVersionAfter = jdbc.queryForObject("select draft_version from mob_drafts where mob_id = ?", Integer.class, mobId);
		assertThat(draftVersionAfter).isEqualTo(draftVersionBefore); // exportar NUNCA toca mob_drafts
	}

	@Test
	void exportar_bbmodel_con_textura_real_persistida_la_embebe_en_vez_del_placeholder_AC2_ticket_056() throws Exception {
		UUID mobId = aProjectAndMob("Con textura real");
		UUID projectId = projectIdOf(mobId);

		MvcResult uploadResult = mockMvc.perform(put("/api/mobs/{mobId}/texture", mobId).contentType(MediaType.IMAGE_PNG).content(TINY_PNG))
				.andExpect(status().isOk())
				.andReturn();
		String storageKey = objectMapper.readTree(uploadResult.getResponse().getContentAsByteArray()).get("storageKey").asText();

		String modelJson = modelWithTexture(mobId, projectId, 4, storageKey);
		mockMvc.perform(patch("/api/mobs/{mobId}/draft", mobId).contentType(MediaType.APPLICATION_JSON).content("{\"model\":" + modelJson + "}"))
				.andExpect(status().isOk());
		mockMvc.perform(post("/api/mobs/{mobId}/revisions", mobId).contentType(MediaType.APPLICATION_JSON).content("{\"model\":" + modelJson + "}"))
				.andExpect(status().isCreated());

		// AC HU-20 (056): la validación FMM sigue sin errores pendientes, ahora con contenido de textura real.
		mockMvc.perform(get("/api/mobs/{mobId}/export/status", mobId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.fmmCompatible", is(true)))
				.andExpect(jsonPath("$.fmmIssues", hasSize(0)));

		MvcResult exportResult = mockMvc.perform(get("/api/mobs/{mobId}/export/bbmodel", mobId))
				.andExpect(status().isOk())
				.andReturn();
		JsonNode bbmodel = objectMapper.readTree(exportResult.getResponse().getContentAsByteArray());
		JsonNode texture = bbmodel.get("textures").get(0);
		assertThat(texture.get("name").asText()).isEqualTo("texture"); // nunca "placeholder" con storageKey real
		String dataUri = texture.get("source").asText();
		byte[] embeddedBytes = Base64.getDecoder().decode(dataUri.substring(dataUri.indexOf(',') + 1));
		// El backend re-codifica canónicamente el PNG antes de persistirlo
		// (`TextureService.decodeAndReencode`) -- se compara contra esos
		// MISMOS bytes ya persistidos en el storage, no contra `TINY_PNG` crudo.
		assertThat(embeddedBytes).isEqualTo(assetStorageService.get(storageKey).orElseThrow());
	}

	@Test
	void un_mobId_inexistente_responde_404_MOB_NOT_FOUND_en_ambos_endpoints() throws Exception {
		UUID randomId = UUID.randomUUID();

		mockMvc.perform(get("/api/mobs/{mobId}/export/status", randomId))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error", is("MOB_NOT_FOUND")));
		mockMvc.perform(get("/api/mobs/{mobId}/export/bbmodel", randomId))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error", is("MOB_NOT_FOUND")));
	}

}
