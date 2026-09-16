package com.galgothstudio.backend.domain.template;

import com.galgothstudio.backend.domain.model.BaseType;
import com.galgothstudio.backend.domain.model.Proportions;
import com.galgothstudio.backend.domain.model.Vec3;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Template canónico humanoide completo -- ticket 097, HU-1/HU-3. Jerarquía y
 * rest-pose documentadas en {@code docs/definiciones/anatomia-por-capas-generacion-mobs.md}
 * (diagrama de jerarquía). Coordenadas en minecraft_pixels, rest-pose
 * absoluta (ADR 0001) -- un humanoide "neutral" (todas las proporciones en
 * 1.0) mide 32px de alto (2 bloques), silueta similar a Steve como línea
 * base, NO como resultado final obligatorio.
 *
 * <p>{@link Proportions} solo expone 4 campos hoy (headScale/armLength/
 * handScale/shoulderWidth) -- torso y piernas no tienen campo de proporción
 * propio todavía y pasan sin cambios; ampliar {@code ModelIntent.Proportions}
 * es un cambio de contrato fuera de alcance de este ticket.
 *
 * <p>Simplificación documentada: escalar {@code shoulderWidth} escala
 * uniformemente TODAS las coordenadas X del brazo (pivotes y cuboides), lo
 * que también engrosa proporcionalmente el ancho del brazo al alejarlo del
 * torso -- comportamiento intencional y simple, no un bug.
 */
final class HumanoidCanonicalTemplate {

	private HumanoidCanonicalTemplate() {
	}

	// Rest-pose (multiplicador 1.0) -- ver diagrama del documento de definición.
	private static final double SHOULDER_Y = 24;
	private static final double UPPER_ARM_LEN = 8; // shoulder (24) -> elbow (16)
	private static final double FOREARM_LEN = 6; // elbow (16) -> wrist (10)
	private static final double BASE_SHOULDER_X = 6; // positivo=izquierda, negativo=derecha
	private static final double HIP_X = 2; // positivo=izquierda, negativo=derecha
	private static final double LIMB_HALF_WIDTH = 2;
	private static final double ARM_HALF_DEPTH = 2;
	private static final double HAND_HALF_SIZE = 2; // half-size en X/Z; largo Y = 2*HAND_HALF_SIZE en rest-pose

	// Ids/nombres reutilizados entre bones y cuboides -- constantes en vez de
	// literales repetidos (hallazgo real de SonarQube, ticket 097: un typo en
	// alguna de las repeticiones habría producido un boneId que no matchea
	// ningún bone, no solo una advertencia de estilo).
	private static final String LEFT = "left";
	private static final String RIGHT = "right";
	private static final String BODY_ID = "body";
	private static final String TORSO_ID = "torso";
	private static final String HEAD_ID = "head";
	private static final String ARM_SUFFIX = "_arm";
	private static final String FOREARM_SUFFIX = "_forearm";
	private static final String HAND_SUFFIX = "_hand";
	private static final String LEG_SUFFIX = "_leg";
	private static final String SHIN_SUFFIX = "_shin";
	private static final String FOOT_SUFFIX = "_foot";

	static CanonicalTemplate build() {
		List<TemplateBoneSpec> bones = baseBones();
		List<TemplateCuboidSpec> cuboids = baseCuboids();
		Map<String, ProportionRange> ranges = Map.of(
				"headScale", new ProportionRange(0.6, 1.8),
				"armLength", new ProportionRange(0.7, 1.6),
				"handScale", new ProportionRange(0.7, 2.2),
				"shoulderWidth", new ProportionRange(0.8, 1.6));
		return new CanonicalTemplate(BaseType.HUMANOID, bones, cuboids, ranges, HumanoidCanonicalTemplate::applyProportions);
	}

	private static String sideName(int sign) {
		return sign > 0 ? LEFT : RIGHT;
	}

	private static List<TemplateBoneSpec> baseBones() {
		List<TemplateBoneSpec> bones = new ArrayList<>();
		bones.add(new TemplateBoneSpec(BODY_ID, BODY_ID, null, new Vec3(0, 12, 0), Vec3.of(0, 0, 0)));
		bones.add(new TemplateBoneSpec(TORSO_ID, TORSO_ID, BODY_ID, new Vec3(0, 12, 0), Vec3.of(0, 0, 0)));
		bones.add(new TemplateBoneSpec(HEAD_ID, HEAD_ID, TORSO_ID, new Vec3(0, 24, 0), Vec3.of(0, 0, 0)));
		bones.addAll(armBones(1));
		bones.addAll(armBones(-1));
		bones.addAll(legBones(1));
		bones.addAll(legBones(-1));
		return bones;
	}

	private static List<TemplateBoneSpec> armBones(int sign) {
		String side = sideName(sign);
		double shoulderX = sign * BASE_SHOULDER_X;
		double elbowY = SHOULDER_Y - UPPER_ARM_LEN;
		double wristY = elbowY - FOREARM_LEN;
		return List.of(
				new TemplateBoneSpec(side + ARM_SUFFIX, side + "Arm", TORSO_ID, new Vec3(shoulderX, SHOULDER_Y, 0), Vec3.of(0, 0, 0)),
				new TemplateBoneSpec(side + FOREARM_SUFFIX, side + "Forearm", side + ARM_SUFFIX, new Vec3(shoulderX, elbowY, 0), Vec3.of(0, 0, 0)),
				new TemplateBoneSpec(side + HAND_SUFFIX, side + "Hand", side + FOREARM_SUFFIX, new Vec3(shoulderX, wristY, 0), Vec3.of(0, 0, 0)));
	}

	private static List<TemplateBoneSpec> legBones(int sign) {
		String side = sideName(sign);
		double hipX = sign * HIP_X;
		return List.of(
				new TemplateBoneSpec(side + LEG_SUFFIX, side + "Leg", BODY_ID, new Vec3(hipX, 12, 0), Vec3.of(0, 0, 0)),
				new TemplateBoneSpec(side + SHIN_SUFFIX, side + "Shin", side + LEG_SUFFIX, new Vec3(hipX, 6, 0), Vec3.of(0, 0, 0)),
				new TemplateBoneSpec(side + FOOT_SUFFIX, side + "Foot", side + SHIN_SUFFIX, new Vec3(hipX, 2, 0), Vec3.of(0, 0, 0)));
	}

	private static List<TemplateCuboidSpec> baseCuboids() {
		List<TemplateCuboidSpec> cuboids = new ArrayList<>();
		// Ids de cuboid distintos de los ids de bone (aunque compartan boneId):
		// GeometryEngine rechaza un batch con tempId duplicado, y CreateBone
		// ya usa TORSO_ID/HEAD_ID como su propio tempId -- hallazgo real,
		// no obvio hasta correr el generador (ticket 098).
		cuboids.add(new TemplateCuboidSpec("torso_cuboid", TORSO_ID, TORSO_ID, new Vec3(-4, 12, -2), new Vec3(4, 24, 2), new Vec3(0, 12, 0), Vec3.of(0, 0, 0), "TORSO"));
		cuboids.add(new TemplateCuboidSpec("head_cuboid", HEAD_ID, HEAD_ID, new Vec3(-4, 24, -4), new Vec3(4, 32, 4), new Vec3(0, 24, 0), Vec3.of(0, 0, 0), "HEAD"));
		cuboids.addAll(armCuboids(1));
		cuboids.addAll(armCuboids(-1));
		cuboids.addAll(legCuboids(1));
		cuboids.addAll(legCuboids(-1));
		return cuboids;
	}

	private static List<TemplateCuboidSpec> armCuboids(int sign) {
		String side = sideName(sign);
		double shoulderX = sign * BASE_SHOULDER_X;
		double elbowY = SHOULDER_Y - UPPER_ARM_LEN;
		double wristY = elbowY - FOREARM_LEN;
		double xMin = shoulderX - LIMB_HALF_WIDTH;
		double xMax = shoulderX + LIMB_HALF_WIDTH;
		// Ids de cuboid distintos de los ids de bone (mismo hallazgo que
		// torso/head en baseCuboids): "_upper_arm" ya era distinto de
		// ARM_SUFFIX ("_arm"), pero forearm/hand cuboid coincidían
		// exactamente con el id de su propio bone -- GeometryEngine rechaza
		// tempId duplicado en el batch.
		return List.of(
				new TemplateCuboidSpec(side + "_upper_arm", side + " upper arm", side + ARM_SUFFIX,
						new Vec3(xMin, elbowY, -ARM_HALF_DEPTH), new Vec3(xMax, SHOULDER_Y, ARM_HALF_DEPTH),
						new Vec3(shoulderX, SHOULDER_Y, 0), Vec3.of(0, 0, 0), "ARM"),
				new TemplateCuboidSpec(side + FOREARM_SUFFIX + "_cuboid", side + " forearm", side + FOREARM_SUFFIX,
						new Vec3(xMin, wristY, -ARM_HALF_DEPTH), new Vec3(xMax, elbowY, ARM_HALF_DEPTH),
						new Vec3(shoulderX, elbowY, 0), Vec3.of(0, 0, 0), "FOREARM"),
				new TemplateCuboidSpec(side + HAND_SUFFIX + "_cuboid", side + " hand", side + HAND_SUFFIX,
						new Vec3(shoulderX - HAND_HALF_SIZE, wristY - 2 * HAND_HALF_SIZE, -HAND_HALF_SIZE),
						new Vec3(shoulderX + HAND_HALF_SIZE, wristY, HAND_HALF_SIZE),
						new Vec3(shoulderX, wristY, 0), Vec3.of(0, 0, 0), "HAND"));
	}

	private static List<TemplateCuboidSpec> legCuboids(int sign) {
		String side = sideName(sign);
		double hipX = sign * HIP_X;
		double xMin = hipX - LIMB_HALF_WIDTH;
		double xMax = hipX + LIMB_HALF_WIDTH;
		return List.of(
				new TemplateCuboidSpec(side + "_thigh", side + " thigh", side + LEG_SUFFIX,
						new Vec3(xMin, 6, -2), new Vec3(xMax, 12, 2), new Vec3(hipX, 12, 0), Vec3.of(0, 0, 0), "LEG"),
				new TemplateCuboidSpec(side + "_shin_cuboid", side + " shin", side + SHIN_SUFFIX,
						new Vec3(xMin, 2, -2), new Vec3(xMax, 6, 2), new Vec3(hipX, 6, 0), Vec3.of(0, 0, 0), "SHIN"),
				new TemplateCuboidSpec(side + "_foot_cuboid", side + " foot", side + FOOT_SUFFIX,
						new Vec3(xMin, 0, -2), new Vec3(xMax, 2, 2), new Vec3(hipX, 2, 0), Vec3.of(0, 0, 0), "FOOT"));
	}

	// -- ProportionApplier ------------------------------------------------

	private static ProportionAdjustmentResult applyProportions(
			List<TemplateBoneSpec> baseBones,
			List<TemplateCuboidSpec> baseCuboids,
			Proportions proportions,
			Map<String, ProportionRange> ranges) {
		double headScale = proportions.headScale();
		double armLength = proportions.armLength();
		double handScale = proportions.handScale();
		double shoulderWidth = proportions.shoulderWidth();

		List<TemplateBoneSpec> bones = new ArrayList<>();
		List<TemplateCuboidSpec> cuboids = new ArrayList<>();

		for (TemplateBoneSpec bone : baseBones) {
			bones.add(switch (bone.id()) {
				case "left_arm", "left_forearm", "left_hand" -> scaledArmBone(bone, 1, armLength, shoulderWidth);
				case "right_arm", "right_forearm", "right_hand" -> scaledArmBone(bone, -1, armLength, shoulderWidth);
				default -> bone; // head/torso/body/piernas: sin campo de proporción propio hoy, sin cambios.
			});
		}

		for (TemplateCuboidSpec cuboid : baseCuboids) {
			cuboids.add(switch (cuboid.id()) {
				case "head_cuboid" -> scaledAroundOrigin(cuboid, headScale);
				case "left_upper_arm", "left_forearm_cuboid" -> scaledArmSegment(cuboid, 1, armLength, shoulderWidth);
				case "right_upper_arm", "right_forearm_cuboid" -> scaledArmSegment(cuboid, -1, armLength, shoulderWidth);
				case "left_hand_cuboid" -> scaledHand(cuboid, 1, armLength, shoulderWidth, handScale);
				case "right_hand_cuboid" -> scaledHand(cuboid, -1, armLength, shoulderWidth, handScale);
				default -> cuboid; // torso/piernas: sin cambios.
			});
		}

		return new ProportionAdjustmentResult(bones, cuboids, List.of());
	}

	private static TemplateBoneSpec scaledArmBone(TemplateBoneSpec bone, int sign, double armLength, double shoulderWidth) {
		double shoulderX = sign * BASE_SHOULDER_X * shoulderWidth;
		String segment = bone.id().substring(bone.id().indexOf('_') + 1);
		double y = switch (segment) {
			case "arm" -> SHOULDER_Y;
			case "forearm" -> SHOULDER_Y - UPPER_ARM_LEN * armLength;
			case "hand" -> SHOULDER_Y - (UPPER_ARM_LEN + FOREARM_LEN) * armLength;
			default -> throw new IllegalStateException("bone de brazo inesperado: " + bone.id());
		};
		return new TemplateBoneSpec(bone.id(), bone.name(), bone.parentId(), new Vec3(shoulderX, y, 0), bone.rotation());
	}

	private static TemplateCuboidSpec scaledArmSegment(TemplateCuboidSpec cuboid, int sign, double armLength, double shoulderWidth) {
		double shoulderX = sign * BASE_SHOULDER_X * shoulderWidth;
		double xMin = shoulderX - LIMB_HALF_WIDTH;
		double xMax = shoulderX + LIMB_HALF_WIDTH;
		double elbowY = SHOULDER_Y - UPPER_ARM_LEN * armLength;
		double wristY = elbowY - FOREARM_LEN * armLength;
		boolean isUpperArm = cuboid.id().endsWith("upper_arm");
		double yFrom = isUpperArm ? elbowY : wristY;
		double yTo = isUpperArm ? SHOULDER_Y : elbowY;
		Vec3 origin = isUpperArm ? new Vec3(shoulderX, SHOULDER_Y, 0) : new Vec3(shoulderX, elbowY, 0);
		return new TemplateCuboidSpec(cuboid.id(), cuboid.name(), cuboid.boneId(),
				new Vec3(xMin, yFrom, -ARM_HALF_DEPTH), new Vec3(xMax, yTo, ARM_HALF_DEPTH),
				origin, cuboid.rotation(), cuboid.semanticPart());
	}

	private static TemplateCuboidSpec scaledHand(TemplateCuboidSpec cuboid, int sign, double armLength, double shoulderWidth, double handScale) {
		double shoulderX = sign * BASE_SHOULDER_X * shoulderWidth;
		double wristY = SHOULDER_Y - (UPPER_ARM_LEN + FOREARM_LEN) * armLength;
		Vec3 wrist = new Vec3(shoulderX, wristY, 0);
		Vec3 from = new Vec3(shoulderX - HAND_HALF_SIZE * handScale, wristY - 2 * HAND_HALF_SIZE * handScale, -HAND_HALF_SIZE * handScale);
		Vec3 to = new Vec3(shoulderX + HAND_HALF_SIZE * handScale, wristY, HAND_HALF_SIZE * handScale);
		return new TemplateCuboidSpec(cuboid.id(), cuboid.name(), cuboid.boneId(), from, to, wrist, cuboid.rotation(), cuboid.semanticPart());
	}

	private static TemplateCuboidSpec scaledAroundOrigin(TemplateCuboidSpec cuboid, double scale) {
		Vec3 origin = cuboid.origin();
		Vec3 from = scalePoint(cuboid.from(), origin, scale);
		Vec3 to = scalePoint(cuboid.to(), origin, scale);
		return new TemplateCuboidSpec(cuboid.id(), cuboid.name(), cuboid.boneId(), from, to, origin, cuboid.rotation(), cuboid.semanticPart());
	}

	private static Vec3 scalePoint(Vec3 point, Vec3 origin, double scale) {
		return new Vec3(
				origin.x() + (point.x() - origin.x()) * scale,
				origin.y() + (point.y() - origin.y()) * scale,
				origin.z() + (point.z() - origin.z()) * scale);
	}
}
