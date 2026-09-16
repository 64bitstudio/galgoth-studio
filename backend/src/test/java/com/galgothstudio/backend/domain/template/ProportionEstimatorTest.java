package com.galgothstudio.backend.domain.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.galgothstudio.backend.domain.model.BaseType;
import com.galgothstudio.backend.domain.model.ModelIntent;
import com.galgothstudio.backend.domain.model.Proportions;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Ticket 097 -- HU-3. Cubre: clamping a rangos válidos con advertencia (nunca
 * se descarta el intent en silencio), y que el ajuste de proporciones del
 * template humanoide produce una anatomía sin huecos ni cuboides inválidos
 * en ningún punto del rango válido.
 */
class ProportionEstimatorTest {

	private static final double EPS = 1e-9;

	private static ModelIntent intentWith(Proportions proportions) {
		return new ModelIntent("humanoid", proportions, 0.0, List.of(), List.of());
	}

	private static Optional<TemplateCuboidSpec> cuboid(ProportionAdjustmentResult result, String id) {
		return result.cuboids().stream().filter(c -> c.id().equals(id)).findFirst();
	}

	private static Optional<TemplateBoneSpec> bone(ProportionAdjustmentResult result, String id) {
		return result.bones().stream().filter(b -> b.id().equals(id)).findFirst();
	}

	// -- Clamping -----------------------------------------------------------

	@Test
	void proporcionDentroDeRango_seAplicaTalCualSinAdvertencia() {
		CanonicalTemplate template = CanonicalTemplateCatalog.forBaseType(BaseType.HUMANOID);
		ModelIntent intent = intentWith(new Proportions(1.2, 1.0, 1.0, 1.0));

		ProportionAdjustmentResult result = ProportionEstimator.adjust(template, intent);

		assertThat(result.warnings()).isEmpty();
	}

	@Test
	void proporcionFueraDeRango_seClampeaYQuedaRegistradaComoAdvertencia_noSeDescartaElIntent() {
		CanonicalTemplate template = CanonicalTemplateCatalog.forBaseType(BaseType.HUMANOID);
		// headScale rango [0.6,1.8] -- 5.0 está muy fuera.
		ModelIntent intent = intentWith(new Proportions(5.0, 1.0, 1.0, 1.0));

		ProportionAdjustmentResult result = ProportionEstimator.adjust(template, intent);

		assertThat(result.warnings()).hasSize(1);
		assertThat(result.warnings().get(0)).contains("headScale").contains("1.8");
		// El resto del modelo se sigue produciendo -- el intent no se descarta completo.
		assertThat(result.bones()).isNotEmpty();
		assertThat(result.cuboids()).isNotEmpty();
		TemplateCuboidSpec head = cuboid(result, "head").orElseThrow();
		// Clampado a 1.8, no al valor pedido 5.0: alto = 8 * 1.8 = 14.4 desde el origen (y=24).
		assertThat(head.to().y() - head.origin().y()).isCloseTo(8 * 1.8, within(EPS));
	}

	@Test
	void variasProporcionesFueraDeRango_generanUnaAdvertenciaPorCadaUna() {
		CanonicalTemplate template = CanonicalTemplateCatalog.forBaseType(BaseType.HUMANOID);
		ModelIntent intent = intentWith(new Proportions(5.0, 5.0, 5.0, 5.0));

		ProportionAdjustmentResult result = ProportionEstimator.adjust(template, intent);

		assertThat(result.warnings()).hasSize(4);
	}

	// -- Head scaling ---------------------------------------------------------

	@Test
	void headScale_escalaLaCabezaAlrededorDelPivoteDelCuello_sinMoverElPivote() {
		CanonicalTemplate template = CanonicalTemplateCatalog.forBaseType(BaseType.HUMANOID);
		ModelIntent intent = intentWith(new Proportions(1.5, 1.0, 1.0, 1.0));

		ProportionAdjustmentResult result = ProportionEstimator.adjust(template, intent);
		TemplateCuboidSpec head = cuboid(result, "head").orElseThrow();

		assertThat(head.origin()).isEqualTo(new com.galgothstudio.backend.domain.model.Vec3(0, 24, 0));
		assertThat(head.from().x()).isCloseTo(-4 * 1.5, within(EPS));
		assertThat(head.to().x()).isCloseTo(4 * 1.5, within(EPS));
		assertThat(head.to().y() - head.origin().y()).isCloseTo(8 * 1.5, within(EPS));
		assertThat(head.from().y()).isCloseTo(24, within(EPS)); // el cuello no baja
	}

	// -- Arm chain: contigüidad y sin huecos ----------------------------------

	@Test
	void armLength_cadenaDeBrazoQuedaContiguaSinHuecosEnTodoElRangoValido() {
		CanonicalTemplate template = CanonicalTemplateCatalog.forBaseType(BaseType.HUMANOID);

		for (double armLength : new double[] { 0.7, 0.85, 1.0, 1.3, 1.6 }) {
			ModelIntent intent = intentWith(new Proportions(1.0, armLength, 1.0, 1.0));
			ProportionAdjustmentResult result = ProportionEstimator.adjust(template, intent);

			TemplateCuboidSpec upperArm = cuboid(result, "left_upper_arm").orElseThrow();
			TemplateCuboidSpec forearm = cuboid(result, "left_forearm").orElseThrow();
			TemplateCuboidSpec hand = cuboid(result, "left_hand").orElseThrow();

			assertThat(upperArm.from().y()).as("armLength=" + armLength)
					.isCloseTo(forearm.to().y(), within(EPS));
			assertThat(forearm.from().y()).as("armLength=" + armLength)
					.isCloseTo(hand.to().y(), within(EPS));

			// Cada cuboid del brazo sigue siendo una caja válida (from < to en cada eje).
			for (TemplateCuboidSpec c : List.of(upperArm, forearm, hand)) {
				assertThat(c.from().x()).isLessThan(c.to().x());
				assertThat(c.from().y()).isLessThan(c.to().y());
				assertThat(c.from().z()).isLessThan(c.to().z());
			}

			// Los pivotes de los bones coinciden con las articulaciones de los cuboides.
			TemplateBoneSpec forearmBone = bone(result, "left_forearm").orElseThrow();
			assertThat(forearmBone.pivot().y()).isCloseTo(upperArm.from().y(), within(EPS));
		}
	}

	// -- Hand scaling -----------------------------------------------------------

	@Test
	void handScale_escalaLaManoAlrededorDeLaMuneca_sinMoverLaMuneca() {
		CanonicalTemplate template = CanonicalTemplateCatalog.forBaseType(BaseType.HUMANOID);
		ModelIntent intent = intentWith(new Proportions(1.0, 1.0, 2.0, 1.0));

		ProportionAdjustmentResult result = ProportionEstimator.adjust(template, intent);
		TemplateCuboidSpec hand = cuboid(result, "left_hand").orElseThrow();
		TemplateCuboidSpec forearm = cuboid(result, "left_forearm").orElseThrow();

		// La muñeca (tope de la mano) sigue pegada al final del antebrazo, cualquiera sea handScale.
		assertThat(hand.to().y()).isCloseTo(forearm.from().y(), within(EPS));
		assertThat(hand.to().y() - hand.from().y()).isCloseTo(2 * 2 * 2.0, within(EPS)); // 2*HAND_HALF_SIZE*handScale
	}

	// -- Shoulder width -----------------------------------------------------------

	@Test
	void shoulderWidth_alejaLosBrazosDelTorsoSimetricamenteEnAmbosLados() {
		CanonicalTemplate template = CanonicalTemplateCatalog.forBaseType(BaseType.HUMANOID);
		ModelIntent intent = intentWith(new Proportions(1.0, 1.0, 1.0, 1.5));

		ProportionAdjustmentResult result = ProportionEstimator.adjust(template, intent);
		TemplateCuboidSpec leftArm = cuboid(result, "left_upper_arm").orElseThrow();
		TemplateCuboidSpec rightArm = cuboid(result, "right_upper_arm").orElseThrow();

		assertThat(leftArm.origin().x()).isCloseTo(6 * 1.5, within(EPS));
		assertThat(rightArm.origin().x()).isCloseTo(-6 * 1.5, within(EPS));
	}

	// -- Piernas y torso no tienen campo de proporción propio hoy ----------------

	@Test
	void piernasYTorso_quedanSinCambiosParaCualquierCombinacionDeProporciones() {
		CanonicalTemplate template = CanonicalTemplateCatalog.forBaseType(BaseType.HUMANOID);
		ModelIntent neutral = intentWith(new Proportions(1.0, 1.0, 1.0, 1.0));
		ModelIntent extreme = intentWith(new Proportions(1.8, 1.6, 2.2, 1.6));

		ProportionAdjustmentResult neutralResult = ProportionEstimator.adjust(template, neutral);
		ProportionAdjustmentResult extremeResult = ProportionEstimator.adjust(template, extreme);

		for (String id : List.of("torso", "left_thigh", "right_thigh", "left_shin_cuboid", "right_foot_cuboid")) {
			assertThat(cuboid(extremeResult, id)).isEqualTo(cuboid(neutralResult, id));
		}
	}
}
