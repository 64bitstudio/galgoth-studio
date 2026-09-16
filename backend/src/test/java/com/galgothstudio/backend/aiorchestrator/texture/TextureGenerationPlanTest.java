package com.galgothstudio.backend.aiorchestrator.texture;

import static org.assertj.core.api.Assertions.assertThat;

import com.galgothstudio.backend.domain.model.BaseType;
import com.galgothstudio.backend.domain.model.Bone;
import com.galgothstudio.backend.domain.model.BoneSemanticLabel;
import com.galgothstudio.backend.domain.model.Cuboid;
import com.galgothstudio.backend.domain.model.CuboidFaces;
import com.galgothstudio.backend.domain.model.ExportSettings;
import com.galgothstudio.backend.domain.model.Face;
import com.galgothstudio.backend.domain.model.FaceMaterialNote;
import com.galgothstudio.backend.domain.model.FaceName;
import com.galgothstudio.backend.domain.model.FormatVersion;
import com.galgothstudio.backend.domain.model.MobProjectModel;
import com.galgothstudio.backend.domain.model.TextureDocument;
import com.galgothstudio.backend.domain.model.TexturePalette;
import com.galgothstudio.backend.domain.model.TexturePlan;
import com.galgothstudio.backend.domain.model.UvLayout;
import com.galgothstudio.backend.domain.model.UvRegion;
import com.galgothstudio.backend.domain.model.Vec3;
import com.galgothstudio.backend.domain.model.Vec4;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * `TextureGenerationPlan` -- ticket 101 (HU-7). Reutiliza el
 * `TextureGenerationSheetPlanner` real (ya cubierto por su propio test)
 * para construir un {@link TextureGenerationSheet} real y verificar que
 * {@link TextureGenerationPlan#forSheet} une correctamente
 * {@code semanticPart} persistido (099), la nota de material del
 * {@link TexturePlan} (052) y la región de atlas ya resuelta (053), una
 * entrada por {@code (cuboidId, face)}.
 */
class TextureGenerationPlanTest {

	private static final String BONE_ID = "head-1";
	private static final String REFERENCE_IMAGE_ID = "ref-1";

	private final TextureGenerationSheetPlanner planner = new TextureGenerationSheetPlanner();

	private static Bone bone(String id, String name) {
		return new Bone(id, name, null, new Vec3(0, 0, 0), new Vec3(0, 0, 0));
	}

	private static Cuboid cuboidOf(String id, String boneId, String semanticPart) {
		Face placeholder = new Face(null, null);
		CuboidFaces emptyFaces = new CuboidFaces(placeholder, placeholder, placeholder, placeholder, placeholder, placeholder);
		return new Cuboid(
				id, "cuboid-" + id, boneId, new Vec3(0, 0, 0), new Vec3(4, 4, 4), new Vec3(0, 0, 0), new Vec3(0, 0, 0), emptyFaces,
				semanticPart);
	}

	private static List<UvRegion> regionsFor(String cuboidId, int size) {
		List<UvRegion> regions = new ArrayList<>();
		for (FaceName face : FaceName.values()) {
			regions.add(new UvRegion(cuboidId, face, new Vec4(0, 0, size, size)));
		}
		return regions;
	}

	private static MobProjectModel modelWith(List<Cuboid> cuboids, List<UvRegion> regions) {
		Bone head = bone(BONE_ID, "head");
		TextureDocument texture = new TextureDocument(64, 64, null);
		return new MobProjectModel(
				"mob-1", "project-1", "Test Mob", BaseType.HUMANOID, MobProjectModel.UNITS_MINECRAFT_PIXELS, List.of(head), cuboids, texture,
				new UvLayout(64, 64, regions), List.of(), new ExportSettings(FormatVersion.V5), List.of());
	}

	private static TexturePlan texturePlan() {
		return new TexturePlan(
				List.of(new BoneSemanticLabel(BONE_ID, "head", "cabeza")), new TexturePalette("#111111", "#222222"),
				List.of(new FaceMaterialNote(BONE_ID, "head", FaceName.NORTH, "cuero desgastado con parches")));
	}

	@Test
	void unaEntradaPorPlacementDeLaSheet_ConCuboidIdYFaceQueCoincidenConElPlacement() {
		List<Cuboid> cuboids = List.of(cuboidOf("cube-a", BONE_ID, "HEAD"));
		MobProjectModel model = modelWith(cuboids, regionsFor("cube-a", 8));
		TextureGenerationSheet sheet = planner.plan(model, texturePlan(), BONE_ID, REFERENCE_IMAGE_ID).getFirst();

		TextureGenerationPlan plan = TextureGenerationPlan.forSheet(model, texturePlan(), sheet);

		assertThat(plan.entries()).hasSize(sheet.placements().size());
		for (CuboidFacePlacement placement : sheet.placements()) {
			assertThat(plan.find(placement.cuboidId(), placement.face())).isPresent();
		}
	}

	@Test
	void ancladaAlSemanticPartPersistidoDelCuboid_nuncaReinferido() {
		List<Cuboid> cuboids = List.of(cuboidOf("cube-a", BONE_ID, "HEAD_MAIN"));
		MobProjectModel model = modelWith(cuboids, regionsFor("cube-a", 8));
		TextureGenerationSheet sheet = planner.plan(model, texturePlan(), BONE_ID, REFERENCE_IMAGE_ID).getFirst();

		TextureGenerationPlan plan = TextureGenerationPlan.forSheet(model, texturePlan(), sheet);

		Optional<TextureGenerationPlan.Entry> entry = plan.find("cube-a", FaceName.NORTH);
		assertThat(entry).isPresent();
		assertThat(entry.get().semanticPart()).isEqualTo("HEAD_MAIN");
	}

	@Test
	void semanticPartNuloEnElCuboid_quedaNuloEnLaEntrada_nuncaFallaNiInventaUnValor() {
		List<Cuboid> cuboids = List.of(cuboidOf("cube-a", BONE_ID, null));
		MobProjectModel model = modelWith(cuboids, regionsFor("cube-a", 8));
		TextureGenerationSheet sheet = planner.plan(model, texturePlan(), BONE_ID, REFERENCE_IMAGE_ID).getFirst();

		TextureGenerationPlan plan = TextureGenerationPlan.forSheet(model, texturePlan(), sheet);

		assertThat(plan.find("cube-a", FaceName.NORTH).orElseThrow().semanticPart()).isNull();
	}

	@Test
	void laNotaDeMaterialSeUneDesdeElTexturePlanPorBoneIdYFace() {
		List<Cuboid> cuboids = List.of(cuboidOf("cube-a", BONE_ID, "HEAD"));
		MobProjectModel model = modelWith(cuboids, regionsFor("cube-a", 8));
		TextureGenerationSheet sheet = planner.plan(model, texturePlan(), BONE_ID, REFERENCE_IMAGE_ID).getFirst();

		TextureGenerationPlan plan = TextureGenerationPlan.forSheet(model, texturePlan(), sheet);

		assertThat(plan.find("cube-a", FaceName.NORTH).orElseThrow().materialNote()).isEqualTo("cuero desgastado con parches");
		// SOUTH no tiene FaceMaterialNote en el fixture -- queda null, no inventa una nota.
		assertThat(plan.find("cube-a", FaceName.SOUTH).orElseThrow().materialNote()).isNull();
	}

	@Test
	void laRegionDeAtlasDeLaEntrada_esElAtlasUvRectYaResueltoDelPlacement_nuncaRecalculado() {
		List<Cuboid> cuboids = List.of(cuboidOf("cube-a", BONE_ID, "HEAD"));
		Vec4 expectedAtlasRect = new Vec4(40, 12, 48, 20);
		List<UvRegion> regions = new ArrayList<>();
		for (FaceName face : FaceName.values()) {
			Vec4 rect = face == FaceName.NORTH ? expectedAtlasRect : new Vec4(0, 0, 8, 8);
			regions.add(new UvRegion("cube-a", face, rect));
		}
		MobProjectModel model = modelWith(cuboids, regions);
		TextureGenerationSheet sheet = planner.plan(model, texturePlan(), BONE_ID, REFERENCE_IMAGE_ID).getFirst();

		TextureGenerationPlan plan = TextureGenerationPlan.forSheet(model, texturePlan(), sheet);

		assertThat(plan.find("cube-a", FaceName.NORTH).orElseThrow().atlasRegion()).isEqualTo(expectedAtlasRect);
	}

	@Test
	void find_paraUnCuboidOFaceQueNoEstaEnElPlan_devuelveVacio() {
		List<Cuboid> cuboids = List.of(cuboidOf("cube-a", BONE_ID, "HEAD"));
		MobProjectModel model = modelWith(cuboids, regionsFor("cube-a", 8));
		TextureGenerationSheet sheet = planner.plan(model, texturePlan(), BONE_ID, REFERENCE_IMAGE_ID).getFirst();

		TextureGenerationPlan plan = TextureGenerationPlan.forSheet(model, texturePlan(), sheet);

		assertThat(plan.find("cube-inexistente", FaceName.NORTH)).isEmpty();
	}

}
