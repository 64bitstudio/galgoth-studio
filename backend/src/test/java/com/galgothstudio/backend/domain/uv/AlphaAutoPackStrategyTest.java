package com.galgothstudio.backend.domain.uv;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.galgothstudio.backend.domain.model.Cuboid;
import com.galgothstudio.backend.domain.model.CuboidFaces;
import com.galgothstudio.backend.domain.model.Face;
import com.galgothstudio.backend.domain.model.UvRegion;
import com.galgothstudio.backend.domain.model.Vec3;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Ticket 006 -- AutoUv backend (`UvLayoutStrategy` + `AlphaAutoPackStrategy`).
 * Cubre las 4 AC del ticket a nivel de la estrategia en sí (la integración
 * con {@code GeometryEngine} vive en {@code GeometryEngineUvIntegrationTest}).
 */
class AlphaAutoPackStrategyTest {

	private static final CuboidFaces EMPTY_FACES;

	static {
		Face placeholder = new Face(null, null);
		EMPTY_FACES = new CuboidFaces(placeholder, placeholder, placeholder, placeholder, placeholder, placeholder);
	}

	private static Cuboid cube(String id, String boneId, double size) {
		// Cubo de `size`x`size`x`size` centrado en el origen local.
		double half = size / 2;
		return new Cuboid(
				id, "cube-" + id, boneId, new Vec3(-half, -half, -half), new Vec3(half, half, half),
				new Vec3(0, 0, 0), new Vec3(0, 0, 0), EMPTY_FACES);
	}

	// -- AC #1: UV válida en las 6 caras, dentro de [0,W)x[0,H) ------------

	@Test
	void unSoloCuboid_recibeElDesenvolvimientoDeCajaEstandarDeMinecraft() {
		// Cubo 8x8x8 -- footprint = 2*(8+8)=32 de ancho, (8+8)=16 de alto.
		Cuboid head = cube("head", "bone-1", 8);
		AlphaAutoPackStrategy strategy = new AlphaAutoPackStrategy();

		UvLayoutStrategy.Result result = strategy.layout(List.of(head), 64, 64);

		CuboidFaces faces = result.cuboids().get(0).faces();
		assertUv(faces.up(), 8, 0, 16, 8);
		assertUv(faces.down(), 16, 0, 24, 8);
		assertUv(faces.west(), 0, 8, 8, 16);
		assertUv(faces.north(), 8, 8, 16, 16);
		assertUv(faces.east(), 16, 8, 24, 16);
		assertUv(faces.south(), 24, 8, 32, 16);
		// texture=0 -- único atlas que existe este ciclo (ver Face#texture()).
		assertThat(faces.north().texture()).isEqualTo(0);
	}

	@Test
	void todasLasRegionesGeneradasQuedanDentroDeLosLimitesDelAtlas() {
		Cuboid head = cube("head", "bone-1", 8);
		Cuboid body = cube("body", "bone-1", 12);
		AlphaAutoPackStrategy strategy = new AlphaAutoPackStrategy();

		UvLayoutStrategy.Result result = strategy.layout(List.of(head, body), 64, 64);

		for (UvRegion region : result.regions()) {
			assertThat(region.rect().a()).isBetween(0.0, 64.0);
			assertThat(region.rect().c()).isBetween(0.0, 64.0);
			assertThat(region.rect().b()).isBetween(0.0, 64.0);
			assertThat(region.rect().d()).isBetween(0.0, 64.0);
		}
		// 2 cuboids x 6 caras = 12 regiones.
		assertThat(result.regions()).hasSize(12);
	}

	@Test
	void multiplesCuboids_seEmpaquetanFilaPorFilaSinSuperponerse() {
		// footprint de cada uno = 32x16 -- en un atlas de 64 de ancho caben
		// dos por fila antes de envolver a la siguiente.
		Cuboid a = cube("a", "bone-1", 8);
		Cuboid b = cube("b", "bone-1", 8);
		Cuboid c = cube("c", "bone-1", 8);
		AlphaAutoPackStrategy strategy = new AlphaAutoPackStrategy();

		UvLayoutStrategy.Result result = strategy.layout(List.of(a, b, c), 64, 64);

		Cuboid resultA = result.cuboids().get(0);
		Cuboid resultB = result.cuboids().get(1);
		Cuboid resultC = result.cuboids().get(2);
		assertThat(resultA.faces().up().uv().a()).isEqualTo(8); // fila 1, columna 0 (offsetX=0)
		assertThat(resultB.faces().up().uv().a()).isEqualTo(40); // fila 1, columna 1 (offsetX=32)
		assertThat(resultC.faces().up().uv().b()).isEqualTo(16); // fila 2 (offsetY=16), envolvió por ancho
	}

	// -- AC #2: overflow del atlas -----------------------------------------

	@Test
	void modeloQueNoCabeEnElAtlas_lanzaUvAtlasOverflowConLasDimensionesMinimasQueSiCabrian() {
		Cuboid head = cube("head", "bone-1", 8); // footprint 32x16
		AlphaAutoPackStrategy strategy = new AlphaAutoPackStrategy();
		List<Cuboid> cuboids = List.of(head);

		assertThatThrownBy(() -> strategy.layout(cuboids, 8, 8))
				.isInstanceOf(UvAtlasOverflowException.class)
				.satisfies(ex -> {
					UvAtlasOverflowException overflow = (UvAtlasOverflowException) ex;
					assertThat(overflow.currentWidth()).isEqualTo(8);
					assertThat(overflow.currentHeight()).isEqualTo(8);
					assertThat(overflow.requiredWidth()).isEqualTo(32);
					assertThat(overflow.requiredHeight()).isEqualTo(16);
				});
	}

	// -- AC #4: determinismo ------------------------------------------------

	@Test
	void elMismoModeloDeEntrada_produceExactamenteElMismoLayoutDosVeces() {
		List<Cuboid> input = List.of(cube("head", "bone-1", 8), cube("body", "bone-1", 12), cube("arm", "bone-2", 6));
		AlphaAutoPackStrategy strategy = new AlphaAutoPackStrategy();

		UvLayoutStrategy.Result first = strategy.layout(input, 64, 64);
		UvLayoutStrategy.Result second = strategy.layout(input, 64, 64);

		assertThat(first.cuboids()).isEqualTo(second.cuboids());
		assertThat(first.regions()).isEqualTo(second.regions());
	}

	private static void assertUv(Face face, double u0, double v0, double u1, double v1) {
		assertThat(face.uv().a()).isEqualTo(u0);
		assertThat(face.uv().b()).isEqualTo(v0);
		assertThat(face.uv().c()).isEqualTo(u1);
		assertThat(face.uv().d()).isEqualTo(v1);
	}

}
