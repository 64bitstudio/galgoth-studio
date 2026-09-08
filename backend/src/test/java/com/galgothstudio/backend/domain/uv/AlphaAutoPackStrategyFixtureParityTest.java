package com.galgothstudio.backend.domain.uv;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.galgothstudio.backend.domain.model.Cuboid;
import com.galgothstudio.backend.domain.model.CuboidFaces;
import com.galgothstudio.backend.domain.model.Face;
import com.galgothstudio.backend.domain.model.Vec3;
import com.galgothstudio.backend.domain.model.Vec4;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Ticket 007, AC #1: `AlphaAutoPackStrategy` (Java) y su espejo TS
 * (`frontend/src/domain/autoUv.ts`) deben producir EXACTAMENTE el mismo
 * layout dado el mismo fixture compartido
 * (`contracts/fixtures/uv-layout-fixture.json`) -- una divergencia (bug
 * en cualquiera de los dos lados) rompe este test, no solo el de TS
 * (`frontend/src/domain/__tests__/autoUv.spec.ts`).
 */
class AlphaAutoPackStrategyFixtureParityTest {

	// Gradle corre los tests con cwd = backend/ -- ../contracts es la raíz
	// del repo, igual que el resto de fixtures compartidas del proyecto.
	private static final File FIXTURE_FILE = new File("../contracts/fixtures/uv-layout-fixture.json");

	private record Atlas(int textureWidth, int textureHeight) {
	}

	private record FixtureCuboid(
			String id, String name, String boneId, double[] from, double[] to, double[] origin, double[] rotation) {
	}

	private record Fixture(
			Atlas atlas, List<FixtureCuboid> cuboids, Map<String, Map<String, double[]>> expectedFaces,
			int expectedRegionCount) {
	}

	private static Fixture loadFixture() throws IOException {
		// El fixture trae "description" (documentación) que no es parte del
		// contrato de datos que este test necesita -- ver mismo patrón en
		// CoordinateSystemTest.
		ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		return mapper.readValue(FIXTURE_FILE, Fixture.class);
	}

	private static CuboidFaces placeholderFaces() {
		Face placeholder = new Face(new Vec4(0, 0, 0, 0), null);
		return new CuboidFaces(placeholder, placeholder, placeholder, placeholder, placeholder, placeholder);
	}

	@Test
	void produceExactamenteLaMismaUvQueElFixtureCompartidoConElEspejoTs() throws IOException {
		Fixture fixture = loadFixture();
		List<Cuboid> cuboids = fixture.cuboids()
				.stream()
				.map(
						fc -> new Cuboid(
								fc.id(), fc.name(), fc.boneId(), Vec3.fromArray(fc.from()), Vec3.fromArray(fc.to()),
								Vec3.fromArray(fc.origin()), Vec3.fromArray(fc.rotation()), placeholderFaces()))
				.toList();

		UvLayoutStrategy.Result result =
				new AlphaAutoPackStrategy().layout(cuboids, fixture.atlas().textureWidth(), fixture.atlas().textureHeight());

		for (Cuboid cuboid : result.cuboids()) {
			Map<String, double[]> expectedFaces = fixture.expectedFaces().get(cuboid.id());
			assertThat(expectedFaces).as("fixture no tiene expectedFaces para '%s'", cuboid.id()).isNotNull();
			assertUv(cuboid.faces().up(), expectedFaces.get("up"));
			assertUv(cuboid.faces().down(), expectedFaces.get("down"));
			assertUv(cuboid.faces().west(), expectedFaces.get("west"));
			assertUv(cuboid.faces().north(), expectedFaces.get("north"));
			assertUv(cuboid.faces().east(), expectedFaces.get("east"));
			assertUv(cuboid.faces().south(), expectedFaces.get("south"));
		}
		assertThat(result.regions()).hasSize(fixture.expectedRegionCount());
	}

	private static void assertUv(Face face, double[] expected) {
		assertThat(face.uv().toArray()).containsExactly(expected);
	}

}
