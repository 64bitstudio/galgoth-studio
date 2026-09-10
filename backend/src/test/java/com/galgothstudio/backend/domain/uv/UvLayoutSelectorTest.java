package com.galgothstudio.backend.domain.uv;

import static org.assertj.core.api.Assertions.assertThat;

import com.galgothstudio.backend.domain.model.Cuboid;
import com.galgothstudio.backend.domain.model.CuboidFaces;
import com.galgothstudio.backend.domain.model.Face;
import com.galgothstudio.backend.domain.model.FaceName;
import com.galgothstudio.backend.domain.model.UvLayout;
import com.galgothstudio.backend.domain.model.UvRegion;
import com.galgothstudio.backend.domain.model.UvRegionStatus;
import com.galgothstudio.backend.domain.model.Vec3;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Ticket 041, Diseño técnico §2: {@code UvLayoutSelector} decide entre
 * {@link AlphaAutoPackStrategy} y {@link StableUvStrategy} según si
 * {@code previousLayout.regions()} tiene algún {@code PAINTED}/{@code ORPHAN}.
 */
class UvLayoutSelectorTest {

	private final UvLayoutSelector selector = new UvLayoutSelector(new AlphaAutoPackStrategy(), new StableUvStrategy());

	private static Cuboid cube(String id, double size) {
		double half = size / 2;
		Face placeholder = new Face(null, null);
		CuboidFaces emptyFaces = new CuboidFaces(placeholder, placeholder, placeholder, placeholder, placeholder, placeholder);
		return new Cuboid(
				id, "cube-" + id, "bone-1", new Vec3(-half, -half, -half), new Vec3(half, half, half), new Vec3(0, 0, 0),
				new Vec3(0, 0, 0), emptyFaces);
	}

	@Test
	void previousLayoutSinPaintedNiOrphan_delegaEnAlphaAutoPackStrategyConResultadoByteIdentico() {
		Cuboid head = cube("head", 8);
		UvLayout previousLayout = new UvLayout(
				64, 64, List.of(new UvRegion("head", FaceName.NORTH, new com.galgothstudio.backend.domain.model.Vec4(8, 8, 16, 16), UvRegionStatus.UNPAINTED)));

		UvLayoutStrategy.Result viaSelector = selector.layout(List.of(head), 64, 64, previousLayout);
		UvLayoutStrategy.Result viaAlphaDirecto = new AlphaAutoPackStrategy().layout(List.of(head), 64, 64);

		assertThat(viaSelector.cuboids()).isEqualTo(viaAlphaDirecto.cuboids());
		assertThat(viaSelector.regions()).isEqualTo(viaAlphaDirecto.regions());
	}

	@Test
	void previousLayoutConAlMenosUnPainted_delegaEnStableUvStrategy() {
		Cuboid head = cube("head", 8); // footprint 32x16
		// Coloca el layout previo en un offset (10,10) deliberadamente
		// distinto de donde AlphaAutoPackStrategy reflowearía (siempre
		// arranca en (0,0)) -- si el selector eligiera Alpha por error, el
		// resultado NO preservaría este offset.
		CuboidFaces oldFaces = BoxUvMath.boxUnwrapFaces(head, 10, 10);
		List<UvRegion> oldRegions = new ArrayList<>();
		for (FaceName faceName : FaceName.values()) {
			UvRegionStatus status = faceName == FaceName.NORTH ? UvRegionStatus.PAINTED : UvRegionStatus.UNPAINTED;
			oldRegions.add(new UvRegion("head", faceName, BoxUvMath.faceOf(oldFaces, faceName).uv(), status));
		}
		UvLayout previousLayout = new UvLayout(64, 64, oldRegions);

		UvLayoutStrategy.Result result = selector.layout(List.of(head), 64, 64, previousLayout);

		// El offset (10,10) sobrevive -- prueba que se usó StableUvStrategy
		// (que preserva geometría sin cambios), no un reflow de AlphaAutoPackStrategy.
		assertThat(result.cuboids().get(0).faces().up().uv().a()).isEqualTo(18); // offsetX(10) + z(8)
		assertThat(result.cuboids().get(0).faces().west().uv().a()).isEqualTo(10); // offsetX directo
	}

	@Test
	void previousLayoutConAlMenosUnOrphan_delegaEnStableUvStrategy() {
		Cuboid head = cube("head", 8);
		CuboidFaces oldFaces = BoxUvMath.boxUnwrapFaces(head, 10, 10);
		List<UvRegion> oldRegions = new ArrayList<>();
		for (FaceName faceName : FaceName.values()) {
			oldRegions.add(new UvRegion("head", faceName, BoxUvMath.faceOf(oldFaces, faceName).uv(), UvRegionStatus.UNPAINTED));
		}
		// Una fila ORPHAN de OTRO cuboid ya eliminado -- por sí sola basta
		// para activar StableUvStrategy aunque "head" esté 100% UNPAINTED.
		oldRegions.add(
				new UvRegion(
						"deleted-cuboid", FaceName.NORTH, new com.galgothstudio.backend.domain.model.Vec4(40, 40, 48, 48),
						UvRegionStatus.ORPHAN));
		UvLayout previousLayout = new UvLayout(64, 64, oldRegions);

		UvLayoutStrategy.Result result = selector.layout(List.of(head), 64, 64, previousLayout);

		assertThat(result.cuboids().get(0).faces().up().uv().a()).isEqualTo(18);
	}

}
