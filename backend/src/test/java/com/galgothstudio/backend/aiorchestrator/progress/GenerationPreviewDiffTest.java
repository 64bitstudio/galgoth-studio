package com.galgothstudio.backend.aiorchestrator.progress;

import static org.assertj.core.api.Assertions.assertThat;

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
import com.galgothstudio.backend.domain.model.Vec3;
import com.galgothstudio.backend.domain.model.Vec4;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Unidad directa de {@link GenerationPreviewDiff} (ticket 029, AC #1) -- sin BD, sin proveedor, función pura. */
class GenerationPreviewDiffTest {

	private static final Bone ROOT = new Bone("b1", "root", null, new Vec3(0, 0, 0), new Vec3(0, 0, 0));

	private static MobProjectModel modelWith(List<Bone> bones, List<Cuboid> cuboids) {
		TextureDocument texture = new TextureDocument(64, 64, null);
		return new MobProjectModel(
				"mob-1", "project-1", "Test", BaseType.HUMANOID,
				MobProjectModel.UNITS_MINECRAFT_PIXELS, bones, cuboids, texture, new UvLayout(64, 64, List.of()), List.of(),
				new ExportSettings(FormatVersion.V5), List.of());
	}

	private static Cuboid cuboid(String id, Vec3 to) {
		Face placeholder = new Face(new Vec4(0, 0, 0, 0), null);
		CuboidFaces faces = new CuboidFaces(placeholder, placeholder, placeholder, placeholder, placeholder, placeholder);
		return new Cuboid(id, "c", "b1", new Vec3(0, 0, 0), to, new Vec3(0, 0, 0), new Vec3(0, 0, 0), faces);
	}

	@Test
	void un_bone_nuevo_aparece_como_agregado() {
		MobProjectModel before = modelWith(List.of(), List.of());
		MobProjectModel after = modelWith(List.of(ROOT), List.of());

		PreviewDelta delta = GenerationPreviewDiff.diff(before, after);

		assertThat(delta.addedOrUpdatedBones()).containsExactly(ROOT);
		assertThat(delta.addedOrUpdatedCuboids()).isEmpty();
		assertThat(delta.removedCuboidIds()).isEmpty();
		assertThat(delta.isEmpty()).isFalse();
	}

	@Test
	void un_cuboid_modificado_aparece_como_actualizado_no_duplicado() {
		Cuboid original = cuboid("c1", new Vec3(4, 4, 4));
		Cuboid resized = cuboid("c1", new Vec3(8, 8, 8));
		MobProjectModel before = modelWith(List.of(ROOT), List.of(original));
		MobProjectModel after = modelWith(List.of(ROOT), List.of(resized));

		PreviewDelta delta = GenerationPreviewDiff.diff(before, after);

		assertThat(delta.addedOrUpdatedBones()).isEmpty(); // el bone no cambió, no debe aparecer
		assertThat(delta.addedOrUpdatedCuboids()).containsExactly(resized);
		assertThat(delta.removedCuboidIds()).isEmpty();
	}

	@Test
	void un_cuboid_removido_aparece_solo_en_removedCuboidIds() {
		Cuboid c1 = cuboid("c1", new Vec3(4, 4, 4));
		MobProjectModel before = modelWith(List.of(ROOT), List.of(c1));
		MobProjectModel after = modelWith(List.of(ROOT), List.of());

		PreviewDelta delta = GenerationPreviewDiff.diff(before, after);

		assertThat(delta.addedOrUpdatedCuboids()).isEmpty();
		assertThat(delta.removedCuboidIds()).containsExactly("c1");
	}

	@Test
	void dos_estados_identicos_producen_un_delta_vacio() {
		Cuboid c1 = cuboid("c1", new Vec3(4, 4, 4));
		MobProjectModel model = modelWith(List.of(ROOT), List.of(c1));

		PreviewDelta delta = GenerationPreviewDiff.diff(model, model);

		assertThat(delta.isEmpty()).isTrue();
	}

}
