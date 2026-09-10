package com.galgothstudio.backend.aiorchestrator.texture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import com.galgothstudio.backend.domain.model.ReferenceImage;
import com.galgothstudio.backend.domain.model.TextureDocument;
import com.galgothstudio.backend.domain.model.TexturePalette;
import com.galgothstudio.backend.domain.model.TexturePlan;
import com.galgothstudio.backend.domain.model.UvLayout;
import com.galgothstudio.backend.domain.model.UvRegion;
import com.galgothstudio.backend.domain.model.Vec3;
import com.galgothstudio.backend.domain.model.Vec4;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * `TextureGenerationSheetPlanner` -- ticket 053, Diseño técnico §11 punto
 * 2 y §21 (aislamiento espacial): no-solape de `sheetRect`, lectura
 * (nunca reasignación) del `atlasUvRect` ya resuelto, y fallback/batching
 * explícito cuando el bone excede el límite técnico de una sola sheet.
 */
class TextureGenerationSheetPlannerTest {

	private static final String BONE_ID = "head-1";
	private static final String REFERENCE_IMAGE_ID = "ref-1";

	private final TextureGenerationSheetPlanner planner = new TextureGenerationSheetPlanner();

	private static Bone bone(String id, String name) {
		return new Bone(id, name, null, new Vec3(0, 0, 0), new Vec3(0, 0, 0));
	}

	private static Cuboid cuboidOf(String id, String boneId) {
		Face placeholder = new Face(null, null);
		CuboidFaces emptyFaces = new CuboidFaces(placeholder, placeholder, placeholder, placeholder, placeholder, placeholder);
		return new Cuboid(id, "cuboid-" + id, boneId, new Vec3(0, 0, 0), new Vec3(4, 4, 4), new Vec3(0, 0, 0), new Vec3(0, 0, 0), emptyFaces);
	}

	/** Una `UvRegion` por cada una de las 6 caras de `cuboidId`, todas cuadradas de `size`x`size`, en un rect fijo arbitrario (el planner no valida solapes de ATLAS, solo lee el rect). */
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
		ReferenceImage referenceImage = new ReferenceImage(REFERENCE_IMAGE_ID, "storage-key", 100, 100, "image/png");
		return new MobProjectModel(
				"mob-1", "project-1", "Test Mob", BaseType.HUMANOID, MobProjectModel.UNITS_MINECRAFT_PIXELS, List.of(head),
				cuboids, texture, new UvLayout(64, 64, regions), List.of(), new ExportSettings(FormatVersion.V5),
				List.of(referenceImage));
	}

	private static TexturePlan texturePlan() {
		return new TexturePlan(
				List.of(new BoneSemanticLabel(BONE_ID, "head", "cabeza")), new TexturePalette("#111111", "#222222"),
				List.of(new FaceMaterialNote(BONE_ID, "head", FaceName.NORTH, "cuero desgastado")));
	}

	@Test
	void armaUnaSolaSheetConTodasLasCarasDeAmbosCuboids_conMetadataDelTexturePlan() {
		List<Cuboid> cuboids = List.of(cuboidOf("cube-a", BONE_ID), cuboidOf("cube-b", BONE_ID));
		List<UvRegion> regions = new ArrayList<>(regionsFor("cube-a", 8));
		regions.addAll(regionsFor("cube-b", 8));
		MobProjectModel model = modelWith(cuboids, regions);

		List<TextureGenerationSheet> sheets = planner.plan(model, texturePlan(), BONE_ID);

		assertThat(sheets).hasSize(1);
		TextureGenerationSheet sheet = sheets.getFirst();
		assertThat(sheet.boneId()).isEqualTo(BONE_ID);
		assertThat(sheet.boneName()).isEqualTo("head");
		assertThat(sheet.semanticLabel()).isEqualTo("cabeza");
		assertThat(sheet.dominantPalette()).contains("#111111").contains("#222222");
		assertThat(sheet.materialNotes()).contains("north: cuero desgastado");
		assertThat(sheet.referenceImageId()).isEqualTo(REFERENCE_IMAGE_ID);
		assertThat(sheet.placements()).hasSize(12); // 2 cuboids x 6 caras
	}

	@Test
	void losSheetRectDeTodosLosPlacements_nuncaSeSolapanEntreSi_AC_aislamientoEspacial() {
		List<Cuboid> cuboids = List.of(cuboidOf("cube-a", BONE_ID), cuboidOf("cube-b", BONE_ID));
		List<UvRegion> regions = new ArrayList<>(regionsFor("cube-a", 8));
		regions.addAll(regionsFor("cube-b", 8));
		MobProjectModel model = modelWith(cuboids, regions);

		List<CuboidFacePlacement> placements = planner.plan(model, texturePlan(), BONE_ID).getFirst().placements();

		for (int i = 0; i < placements.size(); i++) {
			for (int j = i + 1; j < placements.size(); j++) {
				assertThat(overlap(placements.get(i).sheetRect(), placements.get(j).sheetRect()))
						.as("placement %d y %d no deben solaparse", i, j)
						.isFalse();
			}
		}
	}

	@Test
	void elAtlasUvRectDeCadaPlacement_esElYaResueltoEnModelUv_nuncaRecalculado() {
		List<Cuboid> cuboids = List.of(cuboidOf("cube-a", BONE_ID));
		Vec4 expectedAtlasRect = new Vec4(40, 12, 48, 20);
		List<UvRegion> regions = new ArrayList<>();
		for (FaceName face : FaceName.values()) {
			Vec4 rect = face == FaceName.NORTH ? expectedAtlasRect : new Vec4(0, 0, 8, 8);
			regions.add(new UvRegion("cube-a", face, rect));
		}
		MobProjectModel model = modelWith(cuboids, regions);

		List<CuboidFacePlacement> placements = planner.plan(model, texturePlan(), BONE_ID).getFirst().placements();

		CuboidFacePlacement north = placements.stream().filter(p -> p.face() == FaceName.NORTH).findFirst().orElseThrow();
		assertThat(north.atlasUvRect()).isEqualTo(expectedAtlasRect);
	}

	@Test
	void unBoneQueNoExiste_lanzaExcepcionExplicita() {
		MobProjectModel model = modelWith(List.of(), List.of());

		assertThatThrownBy(() -> planner.plan(model, texturePlan(), "no-existe")).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void unBoneSinCuboids_lanzaExcepcionExplicita() {
		MobProjectModel model = modelWith(List.of(), List.of());

		assertThatThrownBy(() -> planner.plan(model, texturePlan(), BONE_ID)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void unaCaraSinUvRegionAsignadaTodavia_lanzaExcepcionExplicita_elPlannerNoReasignaUv() {
		List<Cuboid> cuboids = List.of(cuboidOf("cube-a", BONE_ID));
		List<UvRegion> regionsSinDown = regionsFor("cube-a", 8).stream().filter(r -> r.face() != FaceName.DOWN).toList();
		MobProjectModel model = modelWith(cuboids, regionsSinDown);

		assertThatThrownBy(() -> planner.plan(model, texturePlan(), BONE_ID)).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void unModeloSinImagenDeReferencia_lanzaExcepcionExplicita() {
		List<Cuboid> cuboids = List.of(cuboidOf("cube-a", BONE_ID));
		Bone head = bone(BONE_ID, "head");
		TextureDocument texture = new TextureDocument(64, 64, null);
		MobProjectModel model = new MobProjectModel(
				"mob-1", "project-1", "Test Mob", BaseType.HUMANOID, MobProjectModel.UNITS_MINECRAFT_PIXELS, List.of(head),
				cuboids, texture, new UvLayout(64, 64, regionsFor("cube-a", 8)), List.of(), new ExportSettings(FormatVersion.V5),
				List.of());

		assertThatThrownBy(() -> planner.plan(model, texturePlan(), BONE_ID)).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void unBoneQueExcedeElLimiteTecnicoDeUnaSolaSheet_seDivideEnVariasSheets_fallbackExplicito() {
		// Cada cara de 900x900: dos ya no entran en la misma fila/bin de
		// MAX_SHEET_DIMENSION_PX=1536 (900+2+900 > 1536) -- fuerza batching.
		List<Cuboid> cuboids = List.of(cuboidOf("cube-a", BONE_ID), cuboidOf("cube-b", BONE_ID));
		List<UvRegion> regions = new ArrayList<>(regionsFor("cube-a", 900));
		regions.addAll(regionsFor("cube-b", 900));
		MobProjectModel model = modelWith(cuboids, regions);

		List<TextureGenerationSheet> sheets = planner.plan(model, texturePlan(), BONE_ID);

		assertThat(sheets.size()).isGreaterThan(1);
		// Identificación explícita de cada parte: índice (0-based) + tamaño de la lista.
		int totalPlacements = sheets.stream().mapToInt(s -> s.placements().size()).sum();
		assertThat(totalPlacements).isEqualTo(12); // 2 cuboids x 6 caras, sin pérdida ni duplicados

		Set<String> seen = new HashSet<>();
		for (TextureGenerationSheet sheet : sheets) {
			for (CuboidFacePlacement placement : sheet.placements()) {
				String key = placement.cuboidId() + "|" + placement.face();
				assertThat(seen.add(key)).as("cada (cuboid, cara) aparece en exactamente una sub-sheet").isTrue();
			}
			// Cada sub-sheet respeta el límite técnico por sí misma.
			assertThat(sheet.sheetWidth()).isLessThanOrEqualTo(TextureGenerationSheetPlanner.MAX_SHEET_DIMENSION_PX);
			assertThat(sheet.sheetHeight()).isLessThanOrEqualTo(TextureGenerationSheetPlanner.MAX_SHEET_DIMENSION_PX);
			// Todas las sub-sheets comparten la misma metadata del bone/TexturePlan.
			assertThat(sheet.boneId()).isEqualTo(BONE_ID);
			assertThat(sheet.semanticLabel()).isEqualTo("cabeza");
		}
	}

	private static boolean overlap(Vec4 a, Vec4 b) {
		return a.a() < b.c() && b.a() < a.c() && a.b() < b.d() && b.b() < a.d();
	}

}
