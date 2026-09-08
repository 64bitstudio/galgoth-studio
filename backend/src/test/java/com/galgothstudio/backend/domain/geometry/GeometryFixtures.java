package com.galgothstudio.backend.domain.geometry;

import com.galgothstudio.backend.domain.model.BaseType;
import com.galgothstudio.backend.domain.model.Bone;
import com.galgothstudio.backend.domain.model.Cuboid;
import com.galgothstudio.backend.domain.model.CuboidFaces;
import com.galgothstudio.backend.domain.model.ExportSettings;
import com.galgothstudio.backend.domain.model.Face;
import com.galgothstudio.backend.domain.model.FormatVersion;
import com.galgothstudio.backend.domain.model.MobProjectModel;
import com.galgothstudio.backend.domain.model.TextureDocument;
import com.galgothstudio.backend.domain.model.UvLayout;
import com.galgothstudio.backend.domain.model.UvRegion;
import com.galgothstudio.backend.domain.model.Vec3;
import com.galgothstudio.backend.domain.model.Vec4;
import java.util.ArrayList;
import java.util.List;

/**
 * Construcción de {@link MobProjectModel}s mínimos y válidos para los
 * tests de {@link GeometryEngine} -- sin pasar por JSON, para mantener
 * los tests de comportamiento del motor enfocados en el motor mismo (el
 * round-trip JSON de operaciones vive en {@link GeometryOperationJsonTest}).
 */
final class GeometryFixtures {

	private GeometryFixtures() {
	}

	static CuboidFaces placeholderFaces() {
		Face placeholder = new Face(new Vec4(0, 0, 0, 0), null);
		return new CuboidFaces(placeholder, placeholder, placeholder, placeholder, placeholder, placeholder);
	}

	/** Modelo sin bones ni cuboids -- para tests de createBone/createCuboid en un modelo vacío. */
	static MobProjectModel emptyModel() {
		return new MobProjectModel(
				"mob-1",
				"project-1",
				"Test Mob",
				BaseType.HUMANOID,
				MobProjectModel.UNITS_MINECRAFT_PIXELS,
				new ArrayList<>(),
				new ArrayList<>(),
				new TextureDocument(64, 64, null),
				new UvLayout(64, 64, new ArrayList<>()),
				new ArrayList<>(),
				new ExportSettings(FormatVersion.V5),
				new ArrayList<>());
	}

	/** Modelo con un bone raíz "torso" (id={@code boneId}) y un cuboid "body" parentado a él (id={@code cuboidId}). */
	static MobProjectModel modelWithBoneAndCuboid(String boneId, String cuboidId) {
		Bone torso = new Bone(boneId, "torso", null, new Vec3(0, 24, 0), new Vec3(0, 0, 0));
		Cuboid body = new Cuboid(
				cuboidId, "body", boneId, new Vec3(-4, 12, -2), new Vec3(4, 24, 2), new Vec3(0, 24, 0),
				new Vec3(0, 0, 0), placeholderFaces());

		MobProjectModel base = emptyModel();
		return withBonesCuboidsRegions(base, List.of(torso), List.of(body), List.of());
	}

	/** Igual que {@link #modelWithBoneAndCuboid} pero además registra una uv.region que referencia el cuboid. */
	static MobProjectModel modelWithBoneCuboidAndUvRegion(String boneId, String cuboidId) {
		MobProjectModel base = modelWithBoneAndCuboid(boneId, cuboidId);
		UvRegion region = new UvRegion(cuboidId, com.galgothstudio.backend.domain.model.FaceName.NORTH,
				new Vec4(0, 0, 8, 8));
		return withBonesCuboidsRegions(base, base.bones(), base.cuboids(), List.of(region));
	}

	private static MobProjectModel withBonesCuboidsRegions(
			MobProjectModel base, List<Bone> bones, List<Cuboid> cuboids, List<UvRegion> regions) {
		return new MobProjectModel(
				base.mobId(),
				base.projectId(),
				base.name(),
				base.baseType(),
				base.units(),
				new ArrayList<>(bones),
				new ArrayList<>(cuboids),
				base.texture(),
				new UvLayout(base.uv().textureWidth(), base.uv().textureHeight(), new ArrayList<>(regions)),
				base.animations(),
				base.exportSettings(),
				base.referenceImages());
	}

}
