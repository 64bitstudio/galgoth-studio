package com.galgothstudio.backend.domain.export;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/** Ticket 056 (HU-43) -- mismo AC que {@link BBModelExporterV5RealTextureTest}, para {@link BBModelExporterV4}. */
class BBModelExporterV4RealTextureTest {

	private static final ObjectMapper JSON = new ObjectMapper();

	private static CuboidFaces placeholderFaces() {
		Face placeholder = new Face(new Vec4(0, 0, 0, 0), null);
		return new CuboidFaces(placeholder, placeholder, placeholder, placeholder, placeholder, placeholder);
	}

	private static MobProjectModel modelWith(int textureWidth, int textureHeight, String storageKey) {
		String boneId = UUID.randomUUID().toString();
		String cuboidId = UUID.randomUUID().toString();
		Bone bone = new Bone(boneId, "torso", null, new Vec3(0, 0, 0), new Vec3(0, 0, 0));
		Cuboid cuboid = new Cuboid(
				cuboidId, "body", boneId, new Vec3(-4, 0, -2), new Vec3(4, 8, 2), new Vec3(0, 0, 0), new Vec3(0, 0, 0),
				placeholderFaces());
		return new MobProjectModel(
				"mob-1", "project-1", "Test Mob", BaseType.HUMANOID, MobProjectModel.UNITS_MINECRAFT_PIXELS, List.of(bone),
				List.of(cuboid), new TextureDocument(textureWidth, textureHeight, storageKey),
				new UvLayout(textureWidth, textureHeight, new ArrayList<>()), new ArrayList<>(),
				new ExportSettings(FormatVersion.V4), new ArrayList<>());
	}

	private static byte[] solidColorPng(int width, int height, int rgb) throws Exception {
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				image.setRGB(x, y, rgb);
			}
		}
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(image, "png", out);
		return out.toByteArray();
	}

	@Test
	void conBytesDeTexturaRealesElBbmodelV4LosEmbebeTalCualEnVezDelPlaceholder() throws Exception {
		byte[] realPng = solidColorPng(16, 16, 0x112233);
		MobProjectModel model = modelWith(16, 16, "textures/real-hash.png");

		String json = BBModelExporterV4.export(model, realPng);
		JsonNode texture = JSON.readTree(json).path("textures").get(0);

		assertThat(texture.path("name").asText()).isEqualTo("texture");
		byte[] embeddedPng = Base64.getDecoder().decode(texture.path("source").asText().split(",", 2)[1]);
		assertThat(embeddedPng).isEqualTo(realPng);
	}

	@Test
	void sinBytesDeTexturaRealSigueUsandoElPlaceholderCheckerboardSinCambios() throws Exception {
		MobProjectModel model = modelWith(16, 16, null);

		String jsonNull = BBModelExporterV4.export(model, null);
		String jsonUnico = BBModelExporterV4.export(model);

		assertThat(jsonNull).isEqualTo(jsonUnico);
		JsonNode texture = JSON.readTree(jsonUnico).path("textures").get(0);
		assertThat(texture.path("name").asText()).isEqualTo("placeholder");
	}

}
