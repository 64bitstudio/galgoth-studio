package com.galgothstudio.backend.domain.uv;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.galgothstudio.backend.domain.model.BaseType;
import com.galgothstudio.backend.domain.model.Cuboid;
import com.galgothstudio.backend.domain.model.CuboidFaces;
import com.galgothstudio.backend.domain.model.ExportSettings;
import com.galgothstudio.backend.domain.model.Face;
import com.galgothstudio.backend.domain.model.FaceName;
import com.galgothstudio.backend.domain.model.FormatVersion;
import com.galgothstudio.backend.domain.model.MobProjectModel;
import com.galgothstudio.backend.domain.model.TextureDocument;
import com.galgothstudio.backend.domain.model.UvLayout;
import com.galgothstudio.backend.domain.model.UvRegion;
import com.galgothstudio.backend.domain.model.UvRegionStatus;
import com.galgothstudio.backend.domain.model.Vec3;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Ticket 042, Diseño técnico §7 de
 * `docs/definiciones/galgoth-studio-fase3-textura.md` -- Estado A (upgrade
 * explícito x1→x2 antes de PAINTED) y Estado B (densidad/atlas
 * congelados en cuanto existe una región PAINTED).
 */
class TexelDensityUpgradeTest {

	private static Cuboid cube(String id, double size) {
		double half = size / 2;
		Face placeholder = new Face(null, null);
		CuboidFaces emptyFaces = new CuboidFaces(placeholder, placeholder, placeholder, placeholder, placeholder, placeholder);
		return new Cuboid(
				id, "cube-" + id, "bone-1", new Vec3(-half, -half, -half), new Vec3(half, half, half), new Vec3(0, 0, 0),
				new Vec3(0, 0, 0), emptyFaces);
	}

	private static List<UvRegion> regionsFor(String cuboidId, CuboidFaces faces, Map<FaceName, UvRegionStatus> statusByFace) {
		List<UvRegion> regions = new ArrayList<>();
		for (FaceName faceName : FaceName.values()) {
			UvRegionStatus status = statusByFace.getOrDefault(faceName, UvRegionStatus.UNPAINTED);
			regions.add(new UvRegion(cuboidId, faceName, BoxUvMath.faceOf(faces, faceName).uv(), status));
		}
		return regions;
	}

	private static MobProjectModel modelWith(List<Cuboid> cuboids, TextureDocument texture, UvLayout uv) {
		return new MobProjectModel(
				"mob-1", "project-1", "Test Mob", BaseType.HUMANOID, MobProjectModel.UNITS_MINECRAFT_PIXELS, List.of(), cuboids,
				texture, uv, List.of(), new ExportSettings(FormatVersion.V5), List.of());
	}

	// -- Estado A: upgrade explícito x1->x2 antes de PAINTED -----------------

	@Test
	void upgradeAntesDePintar_recalculaLosFootprintsALaNuevaDensidadYReempaqueta() {
		Cuboid head = cube("head", 8); // footprint X1 = 32x16
		CuboidFaces facesX1 = BoxUvMath.boxUnwrapFaces(head, 0, 0);
		List<UvRegion> regionsX1 = regionsFor("head", facesX1, Map.of());
		MobProjectModel modelX1 = modelWith(List.of(withFaces(head, facesX1)), new TextureDocument(32, 16, null), new UvLayout(32, 16, regionsX1));

		TexelDensityUpgrade.Result upgrade = TexelDensityUpgrade.upgradeTo(modelX1, TexelDensity.X2);

		assertThat(upgrade.applied()).isTrue();
		// footprint X2 del mismo cubo = 64x32 -- el atlas resultante cambia
		// de tamaño en consecuencia (AC del ticket: comparar los dos
		// tamaños de atlas para la misma geometría).
		assertThat(upgrade.model().texture().width()).isGreaterThan(modelX1.texture().width());
		assertThat(upgrade.model().texture().height()).isGreaterThan(modelX1.texture().height());
		assertThat(upgrade.model().texture().width()).isEqualTo(64);
		assertThat(upgrade.model().texture().height()).isEqualTo(32);
		assertThat(upgrade.model().uv().textureWidth()).isEqualTo(64);
		assertThat(upgrade.model().uv().textureHeight()).isEqualTo(32);

		// Las regiones reempaquetadas también son físicamente más grandes
		// (densidad real, no solo un lienzo más grande con caras chicas) --
		// la cara norte pasa de 8x8 a 16x16 texels.
		UvRegion northAfter = upgrade.model()
				.uv()
				.regions()
				.stream()
				.filter(r -> r.cuboidId().equals("head") && r.face() == FaceName.NORTH)
				.findFirst()
				.orElseThrow();
		assertThat(northAfter.rect().c() - northAfter.rect().a()).isEqualTo(16);
		assertThat(northAfter.rect().d() - northAfter.rect().b()).isEqualTo(16);
	}

	@Test
	void unMobSinNingunaRegionPainted_puedeUpgradearseAunConGeometriaVariada() {
		List<Cuboid> cuboids = List.of(cube("head", 8), cube("body", 12));
		MobProjectModel model = modelWith(cuboids, new TextureDocument(64, 64, null), new UvLayout(64, 64, List.of()));

		TexelDensityUpgrade.Result upgrade = TexelDensityUpgrade.upgradeTo(model, TexelDensity.X2);

		assertThat(upgrade.applied()).isTrue();
		assertThat(upgrade.model().uv().regions()).hasSize(cuboids.size() * 6);
	}

	// -- Estado B: densidad/atlas congelados en cuanto existe PAINTED -------

	@Test
	void conAlMenosUnaRegionPainted_elUpgradeNoTieneEfecto() {
		Cuboid head = cube("head", 8);
		CuboidFaces facesX1 = BoxUvMath.boxUnwrapFaces(head, 0, 0);
		List<UvRegion> paintedRegions = regionsFor("head", facesX1, Map.of(FaceName.NORTH, UvRegionStatus.PAINTED));
		TextureDocument frozenTexture = new TextureDocument(32, 16, "sha256:already-painted");
		MobProjectModel frozenModel =
				modelWith(List.of(withFaces(head, facesX1)), frozenTexture, new UvLayout(32, 16, paintedRegions));

		TexelDensityUpgrade.Result upgrade = TexelDensityUpgrade.upgradeTo(frozenModel, TexelDensity.X2);

		// "la operación no tiene efecto" -- ni la densidad ni width/height
		// cambian, el modelo de salida es EXACTAMENTE el de entrada.
		assertThat(upgrade.applied()).isFalse();
		assertThat(upgrade.model()).isEqualTo(frozenModel);
	}

	@Test
	void conAtlasCongeladoPorPaint_unaRegionNuevaQueNoCabe_lanzaOverflowSinCrecerElAtlas() {
		// Integración con 041: el atlas está congelado (hay PAINTED), y una
		// operación de geometría que agrega un cuboid nuevo sin espacio
		// libre debe seguir lanzando UvAtlasOverflowException -- el atlas
		// NUNCA crece ni recalcula densidad para hacerle espacio, ni
		// siquiera cuando quien la dispara es una operación distinta al
		// upgrade explícito.
		Cuboid head = cube("head", 8); // footprint 32x16 -- exactamente el atlas completo
		CuboidFaces headFaces = BoxUvMath.boxUnwrapFaces(head, 0, 0);
		List<UvRegion> paintedRegions = regionsFor("head", headFaces, Map.of(FaceName.NORTH, UvRegionStatus.PAINTED));
		UvLayout frozenLayout = new UvLayout(32, 16, paintedRegions);
		Cuboid newCuboid = cube("new-arm", 8); // no hay espacio libre en absoluto
		List<Cuboid> cuboidsWithAddition = List.of(withFaces(head, headFaces), newCuboid);
		UvLayoutSelector selector = new UvLayoutSelector(new AlphaAutoPackStrategy(), new StableUvStrategy());

		assertThatThrownBy(() -> selector.layout(cuboidsWithAddition, 32, 16, frozenLayout))
				.isInstanceOf(UvAtlasOverflowException.class)
				.satisfies(ex -> {
					UvAtlasOverflowException overflow = (UvAtlasOverflowException) ex;
					assertThat(overflow.currentWidth()).isEqualTo(32);
					assertThat(overflow.currentHeight()).isEqualTo(16);
				});
	}

	private static Cuboid withFaces(Cuboid cuboid, CuboidFaces faces) {
		return new Cuboid(
				cuboid.id(), cuboid.name(), cuboid.boneId(), cuboid.from(), cuboid.to(), cuboid.origin(), cuboid.rotation(), faces);
	}

}
