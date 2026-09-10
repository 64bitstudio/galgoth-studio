package com.galgothstudio.backend.aiorchestrator.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.galgothstudio.backend.TestcontainersConfiguration;
import com.galgothstudio.backend.aiorchestrator.persistence.AiJobRepository;
import com.galgothstudio.backend.aiorchestrator.provider.MockVisionProvider;
import com.galgothstudio.backend.aiorchestrator.provider.VisionModelProvider;
import com.galgothstudio.backend.asset.AssetStorageService;
import com.galgothstudio.backend.domain.model.BaseType;
import com.galgothstudio.backend.domain.model.Bone;
import com.galgothstudio.backend.domain.model.Cuboid;
import com.galgothstudio.backend.domain.model.CuboidFaces;
import com.galgothstudio.backend.domain.model.ExportSettings;
import com.galgothstudio.backend.domain.model.Face;
import com.galgothstudio.backend.domain.model.FaceName;
import com.galgothstudio.backend.domain.model.FormatVersion;
import com.galgothstudio.backend.domain.model.MobProjectModel;
import com.galgothstudio.backend.domain.model.ReferenceImage;
import com.galgothstudio.backend.domain.model.TextureDocument;
import com.galgothstudio.backend.domain.model.UvLayout;
import com.galgothstudio.backend.domain.model.UvRegion;
import com.galgothstudio.backend.domain.model.Vec3;
import com.galgothstudio.backend.domain.model.Vec4;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Integración HTTP (Testcontainers + MockMvc) de `TextureGenerationController`
 * -- ticket 054. Contrato real de `POST /api/mobs/{mobId}/ai/generate-texture`,
 * `GET /api/jobs/{jobId}/texture-result` y `POST /api/jobs/{jobId}/apply-texture`.
 * Las reglas de negocio del pipeline (stages SSE, diff, hand-painted overwrite,
 * atomicidad) ya están cubiertas por {@code TextureGenerationServiceTest}/
 * {@code TextureGenerationApplyTest} -- este test se enfoca en el wiring
 * HTTP: rutas, códigos de estado, forma del body, mapeo de errores.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {"ai.vision-provider=mock", "ai.image-provider=mock"})
class TextureGenerationControllerTest {

	private static final String BODY_BONE_ID = "body-1";
	private static final String BODY_CUBOID_ID = "cube-body";
	private static final int REGION_SIZE = 8;
	private static final int ATLAS_WIDTH = 6 * REGION_SIZE;
	private static final int ATLAS_HEIGHT = REGION_SIZE;

	private static final byte[] TINY_PNG =
			Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");

	private static final String VALID_TEXTURE_PLAN =
			"""
			{
			  "boneLabels": [{"boneId": "body-1", "boneName": "body", "semanticLabel": "torso"}],
			  "palette": {"dominantColorHex": "#4A5238", "accentColorHex": "#8B2E2E"},
			  "materialNotes": [{"boneId": "body-1", "boneName": "body", "face": "north", "note": "cuero"}]
			}
			""";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private AssetStorageService assetStorageService;

	@Autowired
	private VisionModelProvider visionModelProvider;

	@Autowired
	private AiJobRepository aiJobRepository;

	@BeforeEach
	void resetMockProviders() {
		((MockVisionProvider) visionModelProvider).setNextResponse(VALID_TEXTURE_PLAN);
	}

	/** `faces.*.uv` REALES (no placeholders `null`) -- `MobProjectModelValidator` (schema) los exige como array; el test de Apply ejercita `applyGenerationProposal`, que SÍ valida contra el schema completo. */
	private static Cuboid cuboidOf(String id, String boneId, List<UvRegion> regions) {
		java.util.Map<FaceName, Face> byFace = new java.util.EnumMap<>(FaceName.class);
		for (UvRegion region : regions) {
			byFace.put(region.face(), new Face(region.rect(), 0));
		}
		CuboidFaces faces = new CuboidFaces(
				byFace.get(FaceName.NORTH), byFace.get(FaceName.SOUTH), byFace.get(FaceName.EAST), byFace.get(FaceName.WEST),
				byFace.get(FaceName.UP), byFace.get(FaceName.DOWN));
		return new Cuboid(id, "cuboid-" + id, boneId, new Vec3(0, 0, 0), new Vec3(4, 4, 4), new Vec3(0, 0, 0), new Vec3(0, 0, 0), faces);
	}

	private static List<UvRegion> regionsFor(String cuboidId) {
		List<UvRegion> regions = new ArrayList<>();
		int slot = 0;
		for (FaceName face : FaceName.values()) {
			regions.add(new UvRegion(cuboidId, face, new Vec4(slot * REGION_SIZE, 0, (slot + 1) * REGION_SIZE, REGION_SIZE)));
			slot++;
		}
		return regions;
	}

	private String modelJson(String mobId) throws Exception {
		Bone body = new Bone(BODY_BONE_ID, "body", null, new Vec3(0, 0, 0), new Vec3(0, 0, 0));
		TextureDocument texture = new TextureDocument(ATLAS_WIDTH, ATLAS_HEIGHT, null);
		ReferenceImage referenceImage = new ReferenceImage("ref-1", "storage-key-placeholder", 100, 100, "image/png");
		List<UvRegion> regions = regionsFor(BODY_CUBOID_ID);
		MobProjectModel model = new MobProjectModel(
				mobId, "project-1", "Test Mob", BaseType.HUMANOID, MobProjectModel.UNITS_MINECRAFT_PIXELS, List.of(body),
				List.of(cuboidOf(BODY_CUBOID_ID, BODY_BONE_ID, regions)), texture, new UvLayout(ATLAS_WIDTH, ATLAS_HEIGHT, regions),
				List.of(), new ExportSettings(FormatVersion.V5), List.of(referenceImage));
		return objectMapper.writeValueAsString(model);
	}

	private UUID aMobReadyForTexture() throws Exception {
		UUID projectId = UUID.randomUUID();
		jdbc.update("insert into projects (id, name) values (?, ?)", projectId, "Galgoth");
		UUID mobId = UUID.randomUUID();
		jdbc.update(
				"insert into mobs (id, project_id, name, base_type, status, current_revision_number) values (?, ?, ?, ?, ?, 1)",
				mobId, projectId, "Carcomido", "humanoid", "draft");

		String modelJson = modelJson(mobId.toString());
		jdbc.update(
				"insert into mob_revisions (id, mob_id, revision_number, model_jsonb, created_by) values (?, ?, 1, ?::jsonb, 'user')",
				UUID.randomUUID(), mobId, modelJson);
		jdbc.update("insert into mob_drafts (mob_id, draft_model_jsonb, draft_version) values (?, ?::jsonb, 1)", mobId, modelJson);

		String storageKey = "mobs/" + mobId + "/references/test.png";
		assetStorageService.put(storageKey, TINY_PNG, "image/png");
		jdbc.update(
				"insert into reference_images (id, mob_id, storage_key, width, height, content_type) values (?, ?, ?, ?, ?, ?)",
				UUID.randomUUID(), mobId, storageKey, 1, 1, "image/png");
		return mobId;
	}

	private UUID startGenerationAndAwaitCompleted(UUID mobId, String body) throws Exception {
		MvcResult started = mockMvc
				.perform(post("/api/mobs/{mobId}/ai/generate-texture", mobId).contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isAccepted())
				.andReturn();
		JsonNode node = objectMapper.readTree(started.getResponse().getContentAsString());
		UUID jobId = UUID.fromString(node.get("jobId").asText());

		Awaitility.await()
				.atMost(Duration.ofSeconds(10))
				.pollInterval(Duration.ofMillis(25))
				.until(() -> !"running".equals(aiJobRepository.findById(jobId).orElseThrow().getStatus()));
		assertThat(aiJobRepository.findById(jobId).orElseThrow().getStatus()).isEqualTo("completed");
		return jobId;
	}

	@Test
	void generar_textura_completa_202_luego_texture_result_200_luego_apply_texture_201() throws Exception {
		UUID mobId = aMobReadyForTexture();

		UUID jobId = startGenerationAndAwaitCompleted(mobId, "{\"style\":\"pixel_art\",\"detailLevel\":\"medium\"}");

		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/jobs/{jobId}/texture-result", jobId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.wholeModel", org.hamcrest.Matchers.is(true)))
				.andExpect(jsonPath("$.touchedFaces", org.hamcrest.Matchers.hasSize(6)));

		mockMvc.perform(post("/api/jobs/{jobId}/apply-texture", jobId))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.revisionNumber", org.hamcrest.Matchers.is(2)))
				.andExpect(jsonPath("$.draftVersion", org.hamcrest.Matchers.is(2)));
	}

	@Test
	void generar_textura_sobre_un_mob_inexistente_responde_404() throws Exception {
		mockMvc.perform(
						post("/api/mobs/{mobId}/ai/generate-texture", UUID.randomUUID())
								.contentType(MediaType.APPLICATION_JSON)
								.content("{\"style\":\"pixel_art\",\"detailLevel\":\"medium\"}"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error", org.hamcrest.Matchers.is("MOB_NOT_FOUND")));
	}

	@Test
	void generar_textura_con_style_invalido_responde_400() throws Exception {
		UUID mobId = aMobReadyForTexture();

		mockMvc.perform(
						post("/api/mobs/{mobId}/ai/generate-texture", mobId)
								.contentType(MediaType.APPLICATION_JSON)
								.content("{\"style\":\"no-existe\",\"detailLevel\":\"medium\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error", org.hamcrest.Matchers.is("INVALID_TEXTURE_GENERATION_REQUEST")));
	}

	@Test
	void generar_textura_con_boneId_inexistente_responde_400() throws Exception {
		UUID mobId = aMobReadyForTexture();

		mockMvc.perform(
						post("/api/mobs/{mobId}/ai/generate-texture", mobId)
								.contentType(MediaType.APPLICATION_JSON)
								.content("{\"style\":\"pixel_art\",\"detailLevel\":\"medium\",\"boneId\":\"no-existe\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error", org.hamcrest.Matchers.is("TEXTURE_TARGET_BONE_NOT_FOUND")));
	}

	@Test
	void generar_textura_sobre_un_mob_sin_ninguna_revision_guardada_responde_400_NO_BASE_REVISION() throws Exception {
		UUID projectId = UUID.randomUUID();
		jdbc.update("insert into projects (id, name) values (?, ?)", projectId, "Galgoth");
		UUID mobId = UUID.randomUUID();
		jdbc.update(
				"insert into mobs (id, project_id, name, base_type, status) values (?, ?, ?, ?, ?)", mobId, projectId, "Carcomido", "humanoid",
				"draft"); // current_revision_number=0 por default

		mockMvc.perform(
						post("/api/mobs/{mobId}/ai/generate-texture", mobId)
								.contentType(MediaType.APPLICATION_JSON)
								.content("{\"style\":\"pixel_art\",\"detailLevel\":\"medium\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error", org.hamcrest.Matchers.is("NO_BASE_REVISION")));
	}

	@Test
	void apply_texture_con_conflicto_responde_409() throws Exception {
		UUID mobId = aMobReadyForTexture();
		UUID jobId = startGenerationAndAwaitCompleted(mobId, "{\"style\":\"pixel_art\",\"detailLevel\":\"medium\"}");

		jdbc.update("update mob_drafts set draft_version = draft_version + 1 where mob_id = ?", mobId);

		mockMvc.perform(post("/api/jobs/{jobId}/apply-texture", jobId))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error", org.hamcrest.Matchers.is("STALE_TEXTURE_BASE")));
	}

	@Test
	void apply_texture_de_un_job_inexistente_responde_404() throws Exception {
		mockMvc.perform(post("/api/jobs/{jobId}/apply-texture", UUID.randomUUID())).andExpect(status().isNotFound());
	}

}
