package com.galgothstudio.backend.aiorchestrator.texture;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.galgothstudio.backend.TestcontainersConfiguration;
import com.galgothstudio.backend.aiorchestrator.persistence.AiJobEntity;
import com.galgothstudio.backend.aiorchestrator.persistence.AiJobEventEntity;
import com.galgothstudio.backend.aiorchestrator.persistence.AiJobEventRepository;
import com.galgothstudio.backend.aiorchestrator.persistence.AiJobRepository;
import com.galgothstudio.backend.aiorchestrator.progress.GenerationStage;
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
import com.galgothstudio.backend.domain.model.UvPaintOrigin;
import com.galgothstudio.backend.domain.model.UvRegion;
import com.galgothstudio.backend.domain.model.UvRegionStatus;
import com.galgothstudio.backend.domain.model.Vec3;
import com.galgothstudio.backend.domain.model.Vec4;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

/**
 * Integración de punta a punta (Testcontainers -- Postgres Y MinIO
 * reales) de {@link TextureGenerationService} -- ticket 054, HU-36 a
 * HU-39. `ai.vision-provider=mock` (paso `TexturePlan`, 052) /
 * `ai.image-provider=mock` (paso de generación de imagen, 051): la suite
 * automatizada nunca llama a ninguna API real de IA.
 *
 * <p>Deliberadamente SIN {@code @Transactional} -- mismo motivo que
 * {@code MobGenerationServiceTest}: el pipeline real corre en OTRO hilo
 * (`generationExecutor`), con su propia conexión/transacción de BD.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = {"ai.vision-provider=mock", "ai.image-provider=mock"})
class TextureGenerationServiceTest {

	private static final String BODY_BONE_ID = "body-1";
	private static final String HEAD_BONE_ID = "head-1";
	private static final String BODY_CUBOID_ID = "cube-body";
	private static final String HEAD_CUBOID_ID = "cube-head";
	private static final int REGION_SIZE = 8;
	private static final int ATLAS_WIDTH = 12 * REGION_SIZE; // 6 caras x 2 cuboids, una fila
	private static final int ATLAS_HEIGHT = REGION_SIZE;

	private static final byte[] TINY_PNG =
			Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");

	private static final String VALID_TEXTURE_PLAN =
			"""
			{
			  "boneLabels": [
			    {"boneId": "body-1", "boneName": "body", "semanticLabel": "torso"},
			    {"boneId": "head-1", "boneName": "head", "semanticLabel": "cabeza"}
			  ],
			  "palette": {"dominantColorHex": "#4A5238", "accentColorHex": "#8B2E2E"},
			  "materialNotes": [
			    {"boneId": "body-1", "boneName": "body", "face": "north", "note": "cuero desgastado"},
			    {"boneId": "head-1", "boneName": "head", "face": "north", "note": "piel agrietada"}
			  ]
			}
			""";

	@Autowired
	private TextureGenerationService textureGenerationService;

	@Autowired
	private VisionModelProvider visionModelProvider; // en realidad un MockVisionProvider (ai.vision-provider=mock)

	@Autowired
	private AssetStorageService assetStorageService;

	@Autowired
	private AiJobRepository aiJobRepository;

	@Autowired
	private AiJobEventRepository aiJobEventRepository;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private ObjectMapper objectMapper;

	@BeforeEach
	void resetMockProviders() {
		((MockVisionProvider) visionModelProvider).setNextResponse(VALID_TEXTURE_PLAN);
	}

	// ---- fixtures ----

	private static List<UvRegion> regionsFor(String cuboidId, int startSlot) {
		List<UvRegion> regions = new ArrayList<>();
		int slot = startSlot;
		for (FaceName face : FaceName.values()) {
			regions.add(new UvRegion(cuboidId, face, new Vec4(slot * REGION_SIZE, 0, (slot + 1) * REGION_SIZE, REGION_SIZE)));
			slot++;
		}
		return regions;
	}

	private static Cuboid cuboidOf(String id, String boneId) {
		Face placeholder = new Face(null, null);
		CuboidFaces faces = new CuboidFaces(placeholder, placeholder, placeholder, placeholder, placeholder, placeholder);
		return new Cuboid(id, "cuboid-" + id, boneId, new Vec3(0, 0, 0), new Vec3(4, 4, 4), new Vec3(0, 0, 0), new Vec3(0, 0, 0), faces);
	}

	private static MobProjectModel modelWithRegions(String mobId, List<UvRegion> regions) {
		Bone body = new Bone(BODY_BONE_ID, "body", null, new Vec3(0, 0, 0), new Vec3(0, 0, 0));
		Bone head = new Bone(HEAD_BONE_ID, "head", BODY_BONE_ID, new Vec3(0, 12, 0), new Vec3(0, 0, 0));
		List<Cuboid> cuboids = List.of(cuboidOf(BODY_CUBOID_ID, BODY_BONE_ID), cuboidOf(HEAD_CUBOID_ID, HEAD_BONE_ID));
		TextureDocument texture = new TextureDocument(ATLAS_WIDTH, ATLAS_HEIGHT, null);
		ReferenceImage referenceImage = new ReferenceImage("ref-1", "storage-key-placeholder", 100, 100, "image/png");
		return new MobProjectModel(
				mobId, "project-1", "Test Mob", BaseType.HUMANOID, MobProjectModel.UNITS_MINECRAFT_PIXELS, List.of(body, head), cuboids, texture,
				new UvLayout(ATLAS_WIDTH, ATLAS_HEIGHT, regions), List.of(), new ExportSettings(FormatVersion.V5), List.of(referenceImage));
	}

	private static List<UvRegion> freshRegions() {
		List<UvRegion> regions = new ArrayList<>(regionsFor(BODY_CUBOID_ID, 0));
		regions.addAll(regionsFor(HEAD_CUBOID_ID, 6));
		return regions;
	}

	/** Mob con geometría ya usable (revision_number=1) + draft + imagen de referencia -- precondición de HU-36/HU-37. */
	private UUID aMobReadyForTexture(List<UvRegion> regions) {
		UUID projectId = UUID.randomUUID();
		jdbc.update("insert into projects (id, name) values (?, ?)", projectId, "Galgoth");
		UUID mobId = UUID.randomUUID();
		jdbc.update(
				"insert into mobs (id, project_id, name, base_type, status, current_revision_number) values (?, ?, ?, ?, ?, 1)",
				mobId, projectId, "Carcomido", "humanoid", "draft");

		MobProjectModel model = modelWithRegions(mobId.toString(), regions);
		String modelJson = writeJson(model);

		jdbc.update(
				"insert into mob_revisions (id, mob_id, revision_number, model_jsonb, created_by) values (?, ?, 1, ?::jsonb, 'user')",
				UUID.randomUUID(), mobId, modelJson);
		jdbc.update(
				"insert into mob_drafts (mob_id, draft_model_jsonb, draft_version) values (?, ?::jsonb, 1)", mobId, modelJson);

		String storageKey = "mobs/" + mobId + "/references/test.png";
		assetStorageService.put(storageKey, TINY_PNG, "image/png");
		jdbc.update(
				"insert into reference_images (id, mob_id, storage_key, width, height, content_type) values (?, ?, ?, ?, ?, ?)",
				UUID.randomUUID(), mobId, storageKey, 1, 1, "image/png");
		return mobId;
	}

	private UUID aMobReadyForTexture() {
		return aMobReadyForTexture(freshRegions());
	}

	private String writeJson(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	private AiJobEntity awaitTerminalStatus(UUID jobId) {
		Awaitility.await()
				.atMost(Duration.ofSeconds(10))
				.pollInterval(Duration.ofMillis(25))
				.until(() -> !"running".equals(aiJobRepository.findById(jobId).orElseThrow().getStatus()));
		return aiJobRepository.findById(jobId).orElseThrow();
	}

	private List<String> stagesOf(UUID jobId) {
		return aiJobEventRepository
				.findByJobIdAndSeqGreaterThanOrderBySeqAsc(jobId, 0)
				.stream()
				.map(AiJobEventEntity::getStage)
				.toList();
	}

	// ---- HU-36: generación del modelo completo ----

	@Test
	void HU36_genera_todos_los_bones_recorre_las_etapas_SSE_nuevas_y_completa_AC1_AC2() {
		UUID mobId = aMobReadyForTexture();

		UUID jobId = textureGenerationService.startGeneration(mobId, new GenerateTextureRequest("pixel_art", "medium", null));
		AiJobEntity job = awaitTerminalStatus(jobId);

		assertThat(job.getStatus()).isEqualTo("completed");
		assertThat(job.getJobType()).isEqualTo("generate_texture");
		assertThat(job.getTargetBoneId()).isNull();
		assertThat(job.getBaseRevisionNumber()).isEqualTo(1);
		assertThat(job.getBaseDraftVersion()).isEqualTo(1);
		assertThat(job.getProvider()).isEqualTo("mock");
		assertThat(job.getModel()).isEqualTo("mock-model");

		List<String> stages = stagesOf(jobId);
		assertThat(stages)
				.contains(
						GenerationStage.ANALIZANDO_PALETA, GenerationStage.MAPEANDO_CARAS, GenerationStage.COMPONIENDO_ATLAS,
						GenerationStage.LIMPIANDO_PIXELES, GenerationStage.COMPLETADO)
				.anyMatch(s -> s.startsWith(GenerationStage.GENERANDO_BONE_PREFIX));
		assertThat(stages.stream().filter(GenerationStage.MAPEANDO_CARAS::equals).count()).isEqualTo(2); // un bone cada uno
	}

	@Test
	void HU37_regenera_solo_el_bone_indicado_job_type_edit_texture_y_target_bone_id() {
		UUID mobId = aMobReadyForTexture();

		UUID jobId = textureGenerationService.startGeneration(mobId, new GenerateTextureRequest("realistic", "high", HEAD_BONE_ID));
		AiJobEntity job = awaitTerminalStatus(jobId);

		assertThat(job.getStatus()).isEqualTo("completed");
		assertThat(job.getJobType()).isEqualTo("edit_texture");
		assertThat(job.getTargetBoneId()).isEqualTo(HEAD_BONE_ID);

		TextureGenerationResultView result = textureGenerationService.getResult(jobId);
		assertThat(result.wholeModel()).isFalse();
		assertThat(result.touchedBoneIds()).containsExactly(HEAD_BONE_ID);
		assertThat(result.touchedFaces()).allMatch(f -> f.cuboidId().equals(HEAD_CUBOID_ID));
	}

	@Test
	void preview_texture_patch_cumple_el_esquema_formal_AC3() throws Exception {
		UUID mobId = aMobReadyForTexture();
		UUID jobId = textureGenerationService.startGeneration(mobId, new GenerateTextureRequest("faithful", "low", null));
		awaitTerminalStatus(jobId);

		List<AiJobEventEntity> componiendoEvents = aiJobEventRepository
				.findByJobIdAndSeqGreaterThanOrderBySeqAsc(jobId, 0)
				.stream()
				.filter(e -> GenerationStage.COMPONIENDO_ATLAS.equals(e.getStage()))
				.toList();
		assertThat(componiendoEvents).isNotEmpty();

		var payload = objectMapper.readTree(componiendoEvents.getFirst().getPayloadJson());
		assertThat(payload.get("type").asText()).isEqualTo("preview_texture_patch");
		assertThat(payload.get("rect").has("x")).isTrue();
		assertThat(payload.get("rect").has("y")).isTrue();
		assertThat(payload.get("rect").has("width")).isTrue();
		assertThat(payload.get("rect").has("height")).isTrue();
		assertThat(payload.get("encoding").asText()).isIn("base64", "asset_url");
		if ("base64".equals(payload.get("encoding").asText())) {
			assertThat(payload.get("data").asText()).isNotBlank();
		} else {
			assertThat(payload.get("url").asText()).startsWith("/api/texture-previews/");
		}
	}

	@Test
	void ningun_preview_toca_mob_drafts_ni_mob_revisions_ni_textures_definitivas_fuera_de_un_apply_real() throws Exception {
		UUID mobId = aMobReadyForTexture();
		Integer draftVersionBefore = jdbc.queryForObject("select draft_version from mob_drafts where mob_id = ?", Integer.class, mobId);
		Integer revisionCountBefore =
				jdbc.queryForObject("select count(*) from mob_revisions where mob_id = ?", Integer.class, mobId);
		Integer currentRevisionBefore =
				jdbc.queryForObject("select current_revision_number from mobs where id = ?", Integer.class, mobId);

		UUID jobId = textureGenerationService.startGeneration(mobId, new GenerateTextureRequest("minecraft_vanilla", "medium", null));
		awaitTerminalStatus(jobId);

		Integer draftVersionAfter = jdbc.queryForObject("select draft_version from mob_drafts where mob_id = ?", Integer.class, mobId);
		Integer revisionCountAfter =
				jdbc.queryForObject("select count(*) from mob_revisions where mob_id = ?", Integer.class, mobId);
		Integer currentRevisionAfter =
				jdbc.queryForObject("select current_revision_number from mobs where id = ?", Integer.class, mobId);

		assertThat(draftVersionAfter).isEqualTo(draftVersionBefore);
		assertThat(revisionCountAfter).isEqualTo(revisionCountBefore);
		assertThat(currentRevisionAfter).isEqualTo(currentRevisionBefore);

		// El bitmap compuesto NUNCA se sube bajo su storageKey content-addressed definitivo (textures/{sha256}.png)
		// -- eso es responsabilidad exclusiva de un Apply real (TextureGenerationService#applyTexture).
		TextureGenerationResultView result = textureGenerationService.getResult(jobId);
		byte[] composedAtlas = Base64.getDecoder().decode(result.afterAtlasPngBase64());
		String sha256 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(composedAtlas));
		assertThat(assetStorageService.exists("textures/" + sha256 + ".png")).isFalse();
	}

	@Test
	void HU38_diff_antes_despues_expone_touchedFaces_y_difiere_del_atlas_original() {
		UUID mobId = aMobReadyForTexture();
		UUID jobId = textureGenerationService.startGeneration(mobId, new GenerateTextureRequest("pixel_art", "medium", null));
		awaitTerminalStatus(jobId);

		TextureGenerationResultView result = textureGenerationService.getResult(jobId);

		assertThat(result.wholeModel()).isTrue();
		assertThat(result.touchedBoneIds()).containsExactlyInAnyOrder(BODY_BONE_ID, HEAD_BONE_ID);
		assertThat(result.touchedFaces()).hasSize(12); // 2 cuboids x 6 caras
		assertThat(result.beforeAtlasPngBase64()).isNotEqualTo(result.afterAtlasPngBase64());
		assertThat(result.hasHandPaintedOverwrite()).isFalse();
	}

	@Test
	void HU37_AC2_senala_explicitamente_sobrescritura_de_contenido_pintado_a_mano() {
		List<UvRegion> regions = new ArrayList<>(regionsFor(BODY_CUBOID_ID, 0));
		for (UvRegion region : regionsFor(HEAD_CUBOID_ID, 6)) {
			if (region.face() == FaceName.NORTH) {
				// pintado a mano (origen desconocido -- paintedBy=null, ver UvPaintOrigin)
				regions.add(new UvRegion(region.cuboidId(), region.face(), region.rect(), UvRegionStatus.PAINTED, null));
			} else {
				regions.add(region);
			}
		}
		UUID mobId = aMobReadyForTexture(regions);

		UUID jobId = textureGenerationService.startGeneration(mobId, new GenerateTextureRequest("pixel_art", "medium", HEAD_BONE_ID));
		awaitTerminalStatus(jobId);

		TextureGenerationResultView result = textureGenerationService.getResult(jobId);
		assertThat(result.hasHandPaintedOverwrite()).isTrue();
		boolean northFlagged = result.touchedFaces()
				.stream()
				.anyMatch(f -> f.cuboidId().equals(HEAD_CUBOID_ID) && f.face() == FaceName.NORTH && f.handPaintedOverwrite());
		assertThat(northFlagged).isTrue();
		boolean otherNotFlagged = result.touchedFaces()
				.stream()
				.filter(f -> f.cuboidId().equals(HEAD_CUBOID_ID) && f.face() != FaceName.NORTH)
				.allMatch(f -> !f.handPaintedOverwrite());
		assertThat(otherNotFlagged).isTrue();
	}

	@Test
	void HU37_AC2_no_senala_sobrescritura_de_mano_cuando_el_origen_ya_era_IA_conocido() {
		List<UvRegion> regions = new ArrayList<>(regionsFor(BODY_CUBOID_ID, 0));
		for (UvRegion region : regionsFor(HEAD_CUBOID_ID, 6)) {
			if (region.face() == FaceName.NORTH) {
				regions.add(new UvRegion(region.cuboidId(), region.face(), region.rect(), UvRegionStatus.PAINTED, UvPaintOrigin.AI));
			} else {
				regions.add(region);
			}
		}
		UUID mobId = aMobReadyForTexture(regions);

		UUID jobId = textureGenerationService.startGeneration(mobId, new GenerateTextureRequest("pixel_art", "medium", HEAD_BONE_ID));
		awaitTerminalStatus(jobId);

		TextureGenerationResultView result = textureGenerationService.getResult(jobId);
		assertThat(result.hasHandPaintedOverwrite()).isFalse();
		assertThat(result.touchedFaces()).allMatch(f -> !f.handPaintedOverwrite());
	}

}
