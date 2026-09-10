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
import com.galgothstudio.backend.domain.model.Vec4;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Ticket 041, AC #1: la sobrecarga {@code default} de 4 argumentos de
 * {@link UvLayoutStrategy} -- {@link AlphaAutoPackStrategy} NO la
 * sobreescribe, así que debe delegar en la sobrecarga de 3 argumentos
 * existente sin ningún cambio de comportamiento, sin tocar
 * {@code AlphaAutoPackStrategyTest}/{@code AlphaAutoPackStrategyFixtureParityTest}.
 */
class UvLayoutStrategyDefaultMethodTest {

	private static Cuboid cube(String id) {
		Face placeholder = new Face(null, null);
		CuboidFaces emptyFaces = new CuboidFaces(placeholder, placeholder, placeholder, placeholder, placeholder, placeholder);
		return new Cuboid(id, "cube-" + id, "bone-1", new Vec3(-4, -4, -4), new Vec3(4, 4, 4), new Vec3(0, 0, 0), new Vec3(0, 0, 0), emptyFaces);
	}

	@Test
	void alphaAutoPackStrategyNoSobreescribeLaSobrecargaDe4Args_delegaEnLaDe3SinCambiosDeComportamiento() {
		AlphaAutoPackStrategy strategy = new AlphaAutoPackStrategy();
		List<Cuboid> cuboids = List.of(cube("head"), cube("body"));
		// previousLayout deliberadamente "ruidoso" (con PAINTED/ORPHAN) --
		// si el default de verdad ignora este argumento (como debe), el
		// resultado tiene que ser IDÉNTICO al de la sobrecarga de 3 args.
		UvLayout noisyPreviousLayout = new UvLayout(
				64, 64, List.of(new UvRegion("other-cuboid", FaceName.NORTH, new Vec4(0, 0, 8, 8), UvRegionStatus.PAINTED)));

		UvLayoutStrategy.Result via4Args = strategy.layout(cuboids, 64, 64, noisyPreviousLayout);
		UvLayoutStrategy.Result via3Args = strategy.layout(cuboids, 64, 64);

		assertThat(via4Args.cuboids()).isEqualTo(via3Args.cuboids());
		assertThat(via4Args.regions()).isEqualTo(via3Args.regions());
		assertThat(via4Args.reservations()).isEmpty();
	}

}
