package com.galgothstudio.backend.domain.geometry;

import static org.assertj.core.api.Assertions.assertThat;

import com.galgothstudio.backend.domain.geometry.SecondaryGeometryConstraints.ValidationResult;
import com.galgothstudio.backend.domain.model.MobProjectModel;
import com.galgothstudio.backend.domain.model.Vec3;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Ticket 099 -- HU-2b. */
class SecondaryGeometryConstraintsTest {

	// Modelo primario de referencia: un bone "hand" con pivote (6,10,0) y un
	// cuboid propio de 4x2x4 alrededor de él -- mismo orden de magnitud que
	// el template humanoide real (097).
	private static MobProjectModel primaryModel() {
		return GeometryFixtures.modelWithBoneAndCuboid("hand", "hand-cuboid");
	}

	private static CreateCuboid validClaw() {
		// boneId debe matchear el bone real del fixture -- GeometryFixtures.modelWithBoneAndCuboid usa "torso" como nombre pero el id se lo pasamos nosotros.
		return new CreateCuboid("claw-1", "claw", "hand", new Vec3(3, 8, -3), new Vec3(5, 10, -1), new Vec3(4, 9, -2), Vec3.of(0, 0, 0), "CLAW");
	}

	@Test
	void cuboidValido_esAceptado() {
		ValidationResult result = SecondaryGeometryConstraints.validate(List.of(validClaw()), primaryModel());

		assertThat(result.accepted()).hasSize(1);
		assertThat(result.rejected()).isEmpty();
	}

	@Test
	void operacionQueNoEsCreateCuboid_esRechazada() {
		GeometryOperation resize = new ResizeCuboid("hand-cuboid", Vec3.of(1.1, 1.1, 1.1));

		ValidationResult result = SecondaryGeometryConstraints.validate(List.of(resize), primaryModel());

		assertThat(result.accepted()).isEmpty();
		assertThat(result.rejected()).hasSize(1);
		assertThat(result.rejected().get(0).reason()).contains("solo admite createCuboid");
	}

	@Test
	void semanticPartVacio_esRechazado() {
		CreateCuboid op = new CreateCuboid("c1", "algo", "hand", new Vec3(3, 8, -3), new Vec3(5, 10, -1), new Vec3(4, 9, -2), Vec3.of(0, 0, 0), "");

		ValidationResult result = SecondaryGeometryConstraints.validate(List.of(op), primaryModel());

		assertThat(result.rejected()).hasSize(1);
		assertThat(result.rejected().get(0).reason()).contains("semanticPart");
	}

	@Test
	void semanticPartAusente_esRechazado() {
		// Constructor de compatibilidad (semanticPart=null) -- mismo caso que vacío.
		CreateCuboid op = new CreateCuboid("c1", "algo", "hand", new Vec3(3, 8, -3), new Vec3(5, 10, -1), new Vec3(4, 9, -2), Vec3.of(0, 0, 0));

		ValidationResult result = SecondaryGeometryConstraints.validate(List.of(op), primaryModel());

		assertThat(result.rejected()).hasSize(1);
	}

	@Test
	void boneIdInexistente_esRechazado_huerfano() {
		CreateCuboid op = new CreateCuboid("c1", "algo", "no-existe", new Vec3(3, 8, -3), new Vec3(5, 10, -1), new Vec3(4, 9, -2), Vec3.of(0, 0, 0), "CLAW");

		ValidationResult result = SecondaryGeometryConstraints.validate(List.of(op), primaryModel());

		assertThat(result.rejected()).hasSize(1);
		assertThat(result.rejected().get(0).reason()).contains("no existe").contains("huérfano");
	}

	@Test
	void coordenadasNoFinitas_sonRechazadas() {
		CreateCuboid nanOp = new CreateCuboid("c1", "algo", "hand", new Vec3(Double.NaN, 8, -3), new Vec3(5, 10, -1), new Vec3(4, 9, -2), Vec3.of(0, 0, 0), "CLAW");
		CreateCuboid infOp = new CreateCuboid("c2", "algo", "hand", new Vec3(3, 8, -3), new Vec3(Double.POSITIVE_INFINITY, 10, -1), new Vec3(4, 9, -2), Vec3.of(0, 0, 0), "CLAW");

		ValidationResult result = SecondaryGeometryConstraints.validate(List.of(nanOp, infOp), primaryModel());

		assertThat(result.rejected()).hasSize(2);
		assertThat(result.rejected()).allSatisfy(r -> assertThat(r.reason()).contains("finitas"));
	}

	@Test
	void dimensionesNoPositivas_sonRechazadas() {
		CreateCuboid zeroWidth = new CreateCuboid("c1", "algo", "hand", new Vec3(4, 8, -3), new Vec3(4, 10, -1), new Vec3(4, 9, -2), Vec3.of(0, 0, 0), "CLAW");
		CreateCuboid inverted = new CreateCuboid("c2", "algo", "hand", new Vec3(5, 8, -3), new Vec3(3, 10, -1), new Vec3(4, 9, -2), Vec3.of(0, 0, 0), "CLAW");

		ValidationResult result = SecondaryGeometryConstraints.validate(List.of(zeroWidth, inverted), primaryModel());

		assertThat(result.rejected()).hasSize(2);
	}

	@Test
	void tamanoExcesivo_esRechazado() {
		CreateCuboid huge = new CreateCuboid("c1", "algo", "hand", new Vec3(0, 0, 0), new Vec3(50, 50, 50), new Vec3(4, 9, -2), Vec3.of(0, 0, 0), "CLAW");

		ValidationResult result = SecondaryGeometryConstraints.validate(List.of(huge), primaryModel());

		assertThat(result.rejected()).hasSize(1);
		assertThat(result.rejected().get(0).reason()).contains("excede el máximo");
	}

	@Test
	void fueraDelBoundingBoxDelPersonaje_esRechazado() {
		// El fixture tiene un único cuboid entre (-4,12,-2) y (4,24,2) --
		// algo a 500px de distancia cae completamente fuera, con cualquier padding razonable.
		CreateCuboid farAway = new CreateCuboid("c1", "algo", "hand", new Vec3(500, 500, 500), new Vec3(502, 502, 502), new Vec3(501, 501, 501), Vec3.of(0, 0, 0), "CLAW");

		ValidationResult result = SecondaryGeometryConstraints.validate(List.of(farAway), primaryModel());

		assertThat(result.rejected()).hasSize(1);
		assertThat(result.rejected().get(0).reason()).contains("bounding box");
	}

	@Test
	void distanciaIrrazonableAlPivoteDelBonePadre_esRechazada() {
		// origin lejos del pivote real del bone "hand" (24,10,0 en el fixture real -- ver GeometryFixtures),
		// aunque el cuboid en sí esté technically dentro del bounding box.
		CreateCuboid op = new CreateCuboid("c1", "algo", "hand", new Vec3(-3, 12, -1), new Vec3(-1, 14, 1), new Vec3(-2, 13, 0), Vec3.of(0, 0, 0), "CLAW");

		ValidationResult result = SecondaryGeometryConstraints.validate(List.of(op), primaryModel(), 16.0, 5.0, 8.0);

		assertThat(result.rejected()).hasSize(1);
		assertThat(result.rejected().get(0).reason()).contains("pivote");
	}

	@Test
	void batchMixto_aceptaLosValidosYRechazaLosInvalidos_sinAbortarElBatchCompleto() {
		CreateCuboid valid = validClaw();
		CreateCuboid invalid = new CreateCuboid("c-bad", "algo", "no-existe", new Vec3(0, 0, 0), new Vec3(1, 1, 1), new Vec3(0, 0, 0), Vec3.of(0, 0, 0), "CLAW");

		ValidationResult result = SecondaryGeometryConstraints.validate(List.of(valid, invalid), primaryModel());

		assertThat(result.accepted()).hasSize(1);
		assertThat(result.rejected()).hasSize(1);
	}
}
