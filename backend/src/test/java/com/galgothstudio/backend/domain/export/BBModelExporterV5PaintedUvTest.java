package com.galgothstudio.backend.domain.export;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.galgothstudio.backend.domain.export.validation.FmmCompatibilityValidator;
import com.galgothstudio.backend.domain.export.validation.ValidationResult;
import com.galgothstudio.backend.domain.model.BaseType;
import com.galgothstudio.backend.domain.model.Bone;
import com.galgothstudio.backend.domain.model.Cuboid;
import com.galgothstudio.backend.domain.model.CuboidFaces;
import com.galgothstudio.backend.domain.model.ExportSettings;
import com.galgothstudio.backend.domain.model.Face;
import com.galgothstudio.backend.domain.model.FaceName;
import com.galgothstudio.backend.domain.model.FormatVersion;
import com.galgothstudio.backend.domain.model.MobProjectModel;
import com.galgothstudio.backend.domain.model.TextureDocument;
import com.galgothstudio.backend.domain.model.UvLayout;
import com.galgothstudio.backend.domain.model.UvRegion;
import com.galgothstudio.backend.domain.model.UvRegionStatus;
import com.galgothstudio.backend.domain.model.Vec3;
import com.galgothstudio.backend.domain.model.Vec4;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Ticket 044, último AC: un modelo con al menos una región {@code PAINTED}
 * se exporta con la UV EXACTAMENTE como está almacenada (nunca
 * recalculada -- perdería el arte real) y el {@code .bbmodel} resultante
 * sigue pasando {@link FmmCompatibilityValidator} (013).
 */
class BBModelExporterV5PaintedUvTest {

	@Test
	void unaRegionPaintedSeExportaConLaUvAlmacenadaYPasaFmmCompatibilityValidator() throws Exception {
		String boneId = UUID.randomUUID().toString();
		String cuboidId = UUID.randomUUID().toString();
		Bone bone = new Bone(boneId, "torso", null, new Vec3(0, 0, 0), new Vec3(0, 0, 0));

		// La cara "north" tiene arte real pintado -- su rect está en una
		// posición que un reflow completo de AlphaAutoPackStrategy NUNCA
		// elegiría (lejos del origen, deliberadamente).
		Vec4 paintedRect = new Vec4(48, 48, 56, 56);
		Face paintedFace = new Face(paintedRect, 0);
		Face otherFace = new Face(new Vec4(0, 0, 8, 8), 0);
		CuboidFaces faces = new CuboidFaces(paintedFace, otherFace, otherFace, otherFace, otherFace, otherFace);
		Cuboid cuboid = new Cuboid(
				cuboidId, "body", boneId, new Vec3(-4, 0, -2), new Vec3(4, 8, 2), new Vec3(0, 0, 0), new Vec3(0, 0, 0),
				faces);

		List<UvRegion> regions = new ArrayList<>();
		regions.add(new UvRegion(cuboidId, FaceName.NORTH, paintedRect, UvRegionStatus.PAINTED));
		regions.add(new UvRegion(cuboidId, FaceName.SOUTH, new Vec4(0, 0, 8, 8)));
		regions.add(new UvRegion(cuboidId, FaceName.EAST, new Vec4(0, 0, 8, 8)));
		regions.add(new UvRegion(cuboidId, FaceName.WEST, new Vec4(0, 0, 8, 8)));
		regions.add(new UvRegion(cuboidId, FaceName.UP, new Vec4(0, 0, 8, 8)));
		regions.add(new UvRegion(cuboidId, FaceName.DOWN, new Vec4(0, 0, 8, 8)));

		MobProjectModel model = new MobProjectModel(
				"mob-1", "project-1", "Test Mob", BaseType.HUMANOID, MobProjectModel.UNITS_MINECRAFT_PIXELS,
				List.of(bone), List.of(cuboid), new TextureDocument(64, 64, "textures/deadbeef.png"),
				new UvLayout(64, 64, regions), List.of(), new ExportSettings(FormatVersion.V5), List.of());

		String json = BBModelExporterV5.export(model);

		JsonNode faceUv = new ObjectMapper().readTree(json).path("elements").get(0).path("faces").path("north").path("uv");
		assertThat(faceUv.get(0).asDouble()).isEqualTo(paintedRect.a());
		assertThat(faceUv.get(1).asDouble()).isEqualTo(paintedRect.b());
		assertThat(faceUv.get(2).asDouble()).isEqualTo(paintedRect.c());
		assertThat(faceUv.get(3).asDouble()).isEqualTo(paintedRect.d());

		ValidationResult validation = FmmCompatibilityValidator.validate(json);
		assertThat(validation.pass()).as("hallazgos FMM: %s", validation.issues()).isTrue();
	}

}
