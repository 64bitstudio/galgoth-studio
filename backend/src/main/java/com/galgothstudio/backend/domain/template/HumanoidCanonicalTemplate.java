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
	private static final double LIMB_HALF_WIDTH = 2;
	private static final double ARM_HALF_DEPTH = 2;
	private static final double HAND_HALF_SIZE = 2; // half-size en X/Z; largo Y = 2*HAND_HALF_SIZE en rest-pose

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

	private static List<TemplateBoneSpec> baseBones() {
		List<TemplateBoneSpec> bones = new ArrayList<>();
		bones.add(new TemplateBoneSpec("body", "body", null, new Vec3(0, 12, 0), Vec3.of(0, 0, 0)));
		bones.add(new TemplateBoneSpec("torso", "torso", "body", new Vec3(0, 12, 0), Vec3.of(0, 0, 0)));
		bones.add(new TemplateBoneSpec("head", "head", "torso", new Vec3(0, 24, 0), Vec3.of(0, 0, 0)));
		bones.addAll(armBones(1));
		bones.addAll(armBones(-1));
		bones.addAll(legBones(1));
		bones.addAll(legBones(-1));
		return bones;
	}

	private static List<TemplateBoneSpec> armBones(int sign) {
		String side = sign > 0 ? "left" : "right";
		double shoulderX = sign * BASE_SHOULDER_X;
		double elbowY = SHOULDER_Y - UPPER_ARM_LEN;
		double wristY = elbowY - FOREARM_LEN;
		return List.of(
				new TemplateBoneSpec(side + "_arm", side + "Arm", "torso", new Vec3(shoulderX, SHOULDER_Y, 0), Vec3.of(0, 0, 0)),
				new TemplateBoneSpec(side + "_forearm", side + "Forearm", side + "_arm", new Vec3(shoulderX, elbowY, 0), Vec3.of(0, 0, 0)),
				new TemplateBoneSpec(side + "_hand", side + "Hand", side + "_forearm", new Vec3(shoulderX, wristY, 0), Vec3.of(0, 0, 0)));
	}

	private static List<TemplateBoneSpec> legBones(int sign) {
		String side = sign > 0 ? "left" : "right";
		double hipX = sign * 2;
		return List.of(
				new TemplateBoneSpec(side + "_leg", side + "Leg", "body", new Vec3(hipX, 12, 0), Vec3.of(0, 0, 0)),
				new TemplateBoneSpec(side + "_shin", side + "Shin", side + "_leg", new Vec3(hipX, 6, 0), Vec3.of(0, 0, 0)),
				new TemplateBoneSpec(side + "_foot", side + "Foot", side + "_shin", new Vec3(hipX, 2, 0), Vec3.of(0, 0, 0)));
	}

	private static List<TemplateCuboidSpec> baseCuboids() {
		List<TemplateCuboidSpec> cuboids = new ArrayList<>();
		cuboids.add(new TemplateCuboidSpec("torso", "torso", "torso", new Vec3(-4, 12, -2), new Vec3(4, 24, 2), new Vec3(0, 12, 0), Vec3.of(0, 0, 0), "TORSO"));
		cuboids.add(new TemplateCuboidSpec("head", "head", "head", new Vec3(-4, 24, -4), new Vec3(4, 32, 4), new Vec3(0, 24, 0), Vec3.of(0, 0, 0), "HEAD"));
		cuboids.addAll(armCuboids(1));
		cuboids.addAll(armCuboids(-1));
		cuboids.addAll(legCuboids(1));
		cuboids.addAll(legCuboids(-1));
		return cuboids;
	}

	private static List<TemplateCuboidSpec> armCuboids(int sign) {
		String side = sign > 0 ? "left" : "right";
		double shoulderX = sign * BASE_SHOULDER_X;
		double elbowY = SHOULDER_Y - UPPER_ARM_LEN;
		double wristY = elbowY - FOREARM_LEN;
		double xMin = shoulderX - LIMB_HALF_WIDTH;
		double xMax = shoulderX + LIMB_HALF_WIDTH;
		return List.of(
				new TemplateCuboidSpec(side + "_upper_arm", side + " upper arm", side + "_arm",
						new Vec3(xMin, elbowY, -ARM_HALF_DEPTH), new Vec3(xMax, SHOULDER_Y, ARM_HALF_DEPTH),
						new Vec3(shoulderX, SHOULDER_Y, 0), Vec3.of(0, 0, 0), "ARM"),
				new TemplateCuboidSpec(side + "_forearm", side + " forearm", side + "_forearm",
						new Vec3(xMin, wristY, -ARM_HALF_DEPTH), new Vec3(xMax, elbowY, ARM_HALF_DEPTH),
						new Vec3(shoulderX, elbowY, 0), Vec3.of(0, 0, 0), "FOREARM"),
				new TemplateCuboidSpec(side + "_hand", side + " hand", side + "_hand",
						new Vec3(shoulderX - HAND_HALF_SIZE, wristY - 2 * HAND_HALF_SIZE, -HAND_HALF_SIZE),
						new Vec3(shoulderX + HAND_HALF_SIZE, wristY, HAND_HALF_SIZE),
						new Vec3(shoulderX, wristY, 0), Vec3.of(0, 0, 0), "HAND"));
	}

	private static List<TemplateCuboidSpec> legCuboids(int sign) {
		String side = sign > 0 ? "left" : "right";
		double hipX = sign * 2;
		double xMin = hipX - LIMB_HALF_WIDTH;
		double xMax = hipX + LIMB_HALF_WIDTH;
		return List.of(
				new TemplateCuboidSpec(side + "_thigh", side + " thigh", side + "_leg",
						new Vec3(xMin, 6, -2), new Vec3(xMax, 12, 2), new Vec3(hipX, 12, 0), Vec3.of(0, 0, 0), "LEG"),
				new TemplateCuboidSpec(side + "_shin_cuboid", side + " shin", side + "_shin",
						new Vec3(xMin, 2, -2), new Vec3(xMax, 6, 2), new Vec3(hipX, 6, 0), Vec3.of(0, 0, 0), "SHIN"),
				new TemplateCuboidSpec(side + "_foot_cuboid", side + " foot", side + "_foot",
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
				case "head" -> scaledAroundOrigin(cuboid, headScale);
				case "left_upper_arm", "left_forearm" -> scaledArmSegment(cuboid, 1, armLength, shoulderWidth);
				case "right_upper_arm", "right_forearm" -> scaledArmSegment(cuboid, -1, armLength, shoulderWidth);
				case "left_hand" -> scaledHand(cuboid, 1, armLength, shoulderWidth, handScale);
				case "right_hand" -> scaledHand(cuboid, -1, armLength, shoulderWidth, handScale);
				default -> cuboid; // torso/piernas: sin cambios.
			});
		}

		return new ProportionAdjustmentResult(bones, cuboids, List.of());
	}

	private static TemplateBoneSpec scaledArmBone(TemplateBoneSpec bone, int sign, double armLength, double shoulderWidth) {
		double shoulderX = sign * BASE_SHOULDER_X * shoulderWidth;
		double y = switch (bone.id().substring(bone.id().indexOf('_') + 1)) {
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
