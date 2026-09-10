package com.galgothstudio.backend.aiorchestrator.texture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;

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
import com.galgothstudio.backend.project.draft.ApplyGenerationResponse;
import com.galgothstudio.backend.project.persistence.MobRepository;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * Apply/Reject/409/atomicidad del pipeline de textura por IA -- ticket
 * 054, HU-38, Diseño técnico §10/§16. Clase separada de
 * {@link TextureGenerationServiceTest} porque {@link #atomicidadTest}
 * usa {@code @MockitoSpyBean} sobre {@link MobRepository} (fuerza un
 * fallo A MITAD de la transacción de Apply) -- overridear un bean cambia
 * el contexto de Spring cacheado, mejor aislado en su propia clase para
 * no invalidar el contexto (más pesado, Testcontainers) del resto de la
 * suite de generación.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = {"ai.vision-provider=mock", "ai.image-provider=mock"})
class TextureGenerationApplyTest {

	private static final String BODY_BONE_ID = "body-1";
	private static final String HEAD_BONE_ID = "head-1";
	private static final String BODY_CUBOID_ID = "cube-body";
	private static final String HEAD_CUBOID_ID = "cube-head";
	private static final int REGION_SIZE = 8;
	private static final int ATLAS_WIDTH = 12 * REGION_SIZE;
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
			    {"boneId": "body-1", "boneName": "body", "face": "north", "note": "cuero desgastado"}
			  ]
			}
			""";

	@Autowired
	private TextureGenerationService textureGenerationService;

	@Autowired
	private VisionModelProvider visionModelProvider;

	@Autowired
	private AssetStorageService assetStorageService;

	@Autowired
	private AiJobRepository aiJobRepository;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoSpyBean
	private MobRepository mobRepository;

	@BeforeEach
	void resetMockProviders() {
		((MockVisionProvider) visionModelProvider).setNextResponse(VALID_TEXTURE_PLAN);
		Mockito.reset(mobRepository);
	}

	private static List<UvRegion> regionsFor(String cuboidId, int startSlot) {
		List<UvRegion> regions = new ArrayList<>();
		int slot = startSlot;
		for (FaceName face : FaceName.values()) {
			regions.add(new UvRegion(cuboidId, face, new Vec4(slot * REGION_SIZE, 0, (slot + 1) * REGION_SIZE, REGION_SIZE)));
			slot++;
		}
		return regions;
	}

	/** `faces.*.uv` REALES (no placeholders `null`) -- `MobProjectModelValidator` (schema) los exige como array, y `applyGenerationProposal` (Apply) SÍ valida contra el schema completo, a diferencia del resto del pipeline de textura (que nunca toca `Cuboid.faces`, solo `uv.regions`). */
	private static Cuboid cuboidOf(String id, String boneId, List<UvRegion> cuboidRegions) {
		java.util.Map<FaceName, Face> byFace = new java.util.EnumMap<>(FaceName.class);
		for (UvRegion region : cuboidRegions) {
			byFace.put(region.face(), new Face(region.rect(), 0));
		}
		CuboidFaces faces = new CuboidFaces(
				byFace.get(FaceName.NORTH), byFace.get(FaceName.SOUTH), byFace.get(FaceName.EAST), byFace.get(FaceName.WEST),
				byFace.get(FaceName.UP), byFace.get(FaceName.DOWN));
		return new Cuboid(id, "cuboid-" + id, boneId, new Vec3(0, 0, 0), new Vec3(4, 4, 4), new Vec3(0, 0, 0), new Vec3(0, 0, 0), faces);
	}

	private static MobProjectModel modelWithRegions(String mobId, List<UvRegion> regions) {
		Bone body = new Bone(BODY_BONE_ID, "body", null, new Vec3(0, 0, 0), new Vec3(0, 0, 0));
		Bone head = new Bone(HEAD_BONE_ID, "head", BODY_BONE_ID, new Vec3(0, 12, 0), new Vec3(0, 0, 0));
		List<UvRegion> bodyRegions = regions.stream().filter(r -> r.cuboidId().equals(BODY_CUBOID_ID)).toList();
		List<UvRegion> headRegions = regions.stream().filter(r -> r.cuboidId().equals(HEAD_CUBOID_ID)).toList();
		List<Cuboid> cuboids = List.of(
				cuboidOf(BODY_CUBOID_ID, BODY_BONE_ID, bodyRegions), cuboidOf(HEAD_CUBOID_ID, HEAD_BONE_ID, headRegions));
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

	private UUID aMobReadyForTexture() {
		UUID projectId = UUID.randomUUID();
		jdbc.update("insert into projects (id, name) values (?, ?)", projectId, "Galgoth");
		UUID mobId = UUID.randomUUID();
		jdbc.update(
				"insert into mobs (id, project_id, name, base_type, status, current_revision_number) values (?, ?, ?, ?, ?, 1)",
				mobId, projectId, "Carcomido", "humanoid", "draft");

		MobProjectModel model = modelWithRegions(mobId.toString(), freshRegions());
		String modelJson = writeJson(model);

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

	private String writeJson(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	private UUID aCompletedTextureJob(UUID mobId) {
		UUID jobId = textureGenerationService.startGeneration(mobId, new GenerateTextureRequest("pixel_art", "medium", null));
		Awaitility.await()
				.atMost(Duration.ofSeconds(10))
				.pollInterval(Duration.ofMillis(25))
				.until(() -> !"running".equals(aiJobRepository.findById(jobId).orElseThrow().getStatus()));
		assertThat(aiJobRepository.findById(jobId).orElseThrow().getStatus()).isEqualTo("completed");
		return jobId;
	}

	@Test
	void apply_exitoso_es_atomico_y_un_GET_posterior_obtiene_exactamente_la_textura_aplicada_HU38_AC3() {
		UUID mobId = aMobReadyForTexture();
		UUID jobId = aCompletedTextureJob(mobId);

		ApplyGenerationResponse response = textureGenerationService.applyTexture(jobId);

		assertThat(response.revisionNumber()).isEqualTo(2);
		assertThat(response.draftVersion()).isEqualTo(2);

		Integer currentRevisionNumber = jdbc.queryForObject("select current_revision_number from mobs where id = ?", Integer.class, mobId);
		assertThat(currentRevisionNumber).isEqualTo(2);

		String draftModelJson = jdbc.queryForObject("select draft_model_jsonb from mob_drafts where mob_id = ?", String.class, mobId);
		String revisionModelJson =
				jdbc.queryForObject("select model_jsonb from mob_revisions where mob_id = ? and revision_number = 2", String.class, mobId);

		String draftStorageKey = extractStorageKey(draftModelJson);
		String revisionStorageKey = extractStorageKey(revisionModelJson);
		assertThat(draftStorageKey)
				.isNotNull()
				.startsWith("textures/")
				.endsWith(".png")
				.isEqualTo(revisionStorageKey); // "GET posterior... obtiene EXACTAMENTE la textura aplicada"
		assertThat(assetStorageService.exists(draftStorageKey)).isTrue();
	}

	@Test
	void apply_con_conflicto_409_descarta_la_propuesta_sin_tocar_nada_HU38_AC3() {
		UUID mobId = aMobReadyForTexture();
		UUID jobId = aCompletedTextureJob(mobId);

		// Simula CUALQUIER avance del draft compartido (geometría o textura, ambas viven en el mismo
		// mob_drafts) desde que se generó la propuesta -- Diseño técnico §16.
		jdbc.update("update mob_drafts set draft_version = draft_version + 1 where mob_id = ?", mobId);

		assertThatThrownBy(() -> textureGenerationService.applyTexture(jobId)).isInstanceOf(StaleTextureBaseException.class);

		Integer currentRevisionNumber = jdbc.queryForObject("select current_revision_number from mobs where id = ?", Integer.class, mobId);
		Integer revisionCount = jdbc.queryForObject("select count(*) from mob_revisions where mob_id = ?", Integer.class, mobId);
		assertThat(currentRevisionNumber).isEqualTo(1); // nunca avanzó
		assertThat(revisionCount).isEqualTo(1); // ninguna revisión nueva
	}

	@Test
	void reject_no_modifica_ni_el_draft_ni_ninguna_revision_HU38_AC2() {
		UUID mobId = aMobReadyForTexture();
		aCompletedTextureJob(mobId); // "Reject" = simplemente nunca invocar apply-texture

		Integer draftVersion = jdbc.queryForObject("select draft_version from mob_drafts where mob_id = ?", Integer.class, mobId);
		Integer revisionCount = jdbc.queryForObject("select count(*) from mob_revisions where mob_id = ?", Integer.class, mobId);
		Integer currentRevisionNumber = jdbc.queryForObject("select current_revision_number from mobs where id = ?", Integer.class, mobId);

		assertThat(draftVersion).isEqualTo(1);
		assertThat(revisionCount).isEqualTo(1);
		assertThat(currentRevisionNumber).isEqualTo(1);
	}

	/**
	 * Diseño técnico §10, AC explícito del ticket 054: "test de integración
	 * que fuerza un fallo a mitad de la transacción y confirma que NINGUNA
	 * fila queda escrita a medias". {@code applyGenerationProposal}
	 * (reutilizado de {@code DraftPersistenceService}) escribe, en ESTE
	 * orden dentro de la MISMA transacción: `mob_revisions` (insert) ->
	 * `mob_drafts` (update) -> `mobs.current_revision_number` (update,
	 * vía {@code mobRepository.save}). Se espía el ÚLTIMO de los 3 para
	 * forzar el fallo DESPUÉS de que los 2 anteriores ya se ejecutaron
	 * (todavía sin commit) -- si la transacción no fuera atómica, el
	 * insert de `mob_revisions` sobreviviría solo; con
	 * {@code @Transactional} real, el rollback deshace los 3.
	 */
	@Test
	void apply_fuerza_un_fallo_a_mitad_de_la_transaccion_y_no_deja_ninguna_fila_escrita_a_medias() {
		UUID mobId = aMobReadyForTexture();
		UUID jobId = aCompletedTextureJob(mobId);

		doThrow(new RuntimeException("fallo forzado a mitad de la transacción de Apply"))
				.when(mobRepository)
				.save(Mockito.argThat(mob -> mob != null && mob.getId().equals(mobId)));

		assertThatThrownBy(() -> textureGenerationService.applyTexture(jobId)).isInstanceOf(RuntimeException.class);

		Integer currentRevisionNumber = jdbc.queryForObject("select current_revision_number from mobs where id = ?", Integer.class, mobId);
		Integer revisionCount = jdbc.queryForObject("select count(*) from mob_revisions where mob_id = ?", Integer.class, mobId);
		Integer draftVersion = jdbc.queryForObject("select draft_version from mob_drafts where mob_id = ?", Integer.class, mobId);

		// Ni el insert de mob_revisions(revision_number=2) ni el update de mob_drafts que ocurrieron
		// ANTES del fallo forzado sobrevivieron -- el rollback de @Transactional deshizo los 3 juntos.
		assertThat(currentRevisionNumber).isEqualTo(1);
		assertThat(revisionCount).isEqualTo(1);
		assertThat(draftVersion).isEqualTo(1);
	}

	private String extractStorageKey(String modelJson) {
		try {
			return objectMapper.readTree(modelJson).path("texture").path("storageKey").asText(null);
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

}
