package com.galgothstudio.backend.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.galgothstudio.backend.domain.jackson.Vec3JacksonModule;
import com.galgothstudio.backend.domain.jackson.Vec4JacksonModule;
import com.galgothstudio.backend.domain.model.Vec3;
import com.galgothstudio.backend.domain.model.Vec4;
import java.io.File;
import java.io.IOException;
import org.junit.jupiter.api.Test;

/**
 * Espejo de frontend/src/domain/__tests__/coordinateSystem.spec.ts --
 * mismo fixture compartido (contracts/fixtures/coordinate-system-fixture.json),
 * verifica que la implementación Java del CoordinateSystemContract
 * (docs/adr/0001-coordinate-system-contract.md) coincide con la TS.
 */
class CoordinateSystemTest {

	// Gradle corre los tests con cwd = backend/ -- ../contracts es la raíz
	// del repo, igual que process.cwd() + '..' del lado TS.
	private static final File FIXTURE_FILE =
			new File("../contracts/fixtures/coordinate-system-fixture.json");

	private record SingleCase(double[] point, double[] origin, double[] rotationDeg, double[] expected) {
	}

	private record ParentChildCase(
			double[] point,
			double[] cuboidOrigin,
			double[] cuboidRotationDeg,
			double[] boneOrigin,
			double[] boneRotationDeg) {
	}

	private record Fixture(
			SingleCase singleAxisRotation, SingleCase offsetPivotRotation, ParentChildCase parentChildComposition) {
	}

	private static Fixture loadFixture() throws IOException {
		// El fixture trae un campo "description" por caso (documentación
		// para quien lo lea, ver contracts/fixtures/coordinate-system-fixture.json)
		// que no es parte del contrato de datos que este test necesita.
		ObjectMapper mapper = new ObjectMapper()
				.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		return mapper.readValue(FIXTURE_FILE, Fixture.class);
	}

	private static void assertVec3Close(Vec3 actual, double[] expected) {
		assertThat(actual.x()).isCloseTo(expected[0], within(1e-9));
		assertThat(actual.y()).isCloseTo(expected[1], within(1e-9));
		assertThat(actual.z()).isCloseTo(expected[2], within(1e-9));
	}

	@Test
	void singleAxisRotation_rotar90GradosEnZAlrededorDelOrigen() throws IOException {
		SingleCase c = loadFixture().singleAxisRotation();
		Vec3 result = CoordinateSystem.applyPivotRotation(
				Vec3.fromArray(c.point()), Vec3.fromArray(c.origin()), Vec3.fromArray(c.rotationDeg()));
		assertVec3Close(result, c.expected());
	}

	@Test
	void offsetPivotRotation_mismoCasoConPivoteDesplazado() throws IOException {
		SingleCase c = loadFixture().offsetPivotRotation();
		Vec3 result = CoordinateSystem.applyPivotRotation(
				Vec3.fromArray(c.point()), Vec3.fromArray(c.origin()), Vec3.fromArray(c.rotationDeg()));
		assertVec3Close(result, c.expected());
	}

	@Test
	void parentChildComposition_composicionDosPasosCoincideConRotacionCombinada() throws IOException {
		ParentChildCase c = loadFixture().parentChildComposition();
		Vec3 point = Vec3.fromArray(c.point());
		Vec3 cuboidOrigin = Vec3.fromArray(c.cuboidOrigin());
		Vec3 cuboidRotationDeg = Vec3.fromArray(c.cuboidRotationDeg());
		Vec3 boneOrigin = Vec3.fromArray(c.boneOrigin());
		Vec3 boneRotationDeg = Vec3.fromArray(c.boneRotationDeg());

		// Camino 1: composición real bone->cuboid del contrato.
		Vec3 viaComposition = CoordinateSystem.composeBoneChildTransform(
				point, cuboidOrigin, cuboidRotationDeg, boneOrigin, boneRotationDeg);

		// Camino 2 (independiente): como boneOrigin == cuboidOrigin y ambas
		// rotaciones son puramente en Z, debe coincidir con UNA sola
		// rotación de ángulo combinado alrededor de ese mismo pivote.
		double combinedZ = boneRotationDeg.z() + cuboidRotationDeg.z();
		Vec3 viaCombinedAngle =
				CoordinateSystem.applyPivotRotation(point, cuboidOrigin, new Vec3(0, 0, combinedZ));

		assertVec3Close(viaComposition, new double[] { viaCombinedAngle.x(), viaCombinedAngle.y(), viaCombinedAngle.z() });
	}

	@Test
	void vec3JacksonModule_serializaComoArrayDe3Numeros() throws IOException {
		ObjectMapper mapper = new ObjectMapper().registerModule(new Vec3JacksonModule());
		String json = mapper.writeValueAsString(new Vec3(1.5, -2, 3));
		assertThat(json).isEqualTo("[1.5,-2.0,3.0]");

		Vec3 parsed = mapper.readValue("[1.5,-2.0,3.0]", Vec3.class);
		assertThat(parsed).isEqualTo(new Vec3(1.5, -2.0, 3.0));
	}

	@Test
	void vec4JacksonModule_serializaComoArrayDe4Numeros() throws IOException {
		ObjectMapper mapper = new ObjectMapper().registerModule(new Vec4JacksonModule());
		String json = mapper.writeValueAsString(new Vec4(0, 0, 8, 8));
		assertThat(json).isEqualTo("[0.0,0.0,8.0,8.0]");

		Vec4 parsed = mapper.readValue("[0.0,0.0,8.0,8.0]", Vec4.class);
		assertThat(parsed).isEqualTo(new Vec4(0, 0, 8, 8));
	}

	@Test
	void vec4_equalsCompareContenidoNoReferencia() {
		// Hallazgo real de SonarQube (java:S2384): un record con un campo
		// double[] hereda equals/hashCode por referencia para ese campo.
		// Vec4 (a,b,c,d primitivos) no tiene ese problema -- este test lo
		// deja explícito.
		assertThat(new Vec4(0, 0, 8, 8)).isEqualTo(new Vec4(0, 0, 8, 8));
	}

}
