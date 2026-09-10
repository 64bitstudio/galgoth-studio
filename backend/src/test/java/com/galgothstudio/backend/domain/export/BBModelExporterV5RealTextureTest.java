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
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/**
 * Ticket 056 (HU-43) -- segundo gap real detectado y cerrado con VoBo del
 * PO: {@link BBModelExporterV5#export(MobProjectModel)} SIEMPRE embebía
 * el checkerboard placeholder (ticket 011), incluso cuando el mob ya
 * tenía textura real persistida (045/046-054). Cubre el nuevo overload
 * {@link BBModelExporterV5#export(MobProjectModel, byte[])}: con bytes
 * reales -> se embeben tal cual (nunca el placeholder); sin ellos
 * (`null`) -> comportamiento IDÉNTICO al de siempre (ver también
 * `BBModelExporterV5PlaceholderTextureTest`, sin cambios).
 */
class BBModelExporterV5RealTextureTest {

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
				new ExportSettings(FormatVersion.V5), new ArrayList<>());
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

	// -- AC #2 de 056: con textura real, el .bbmodel la incluye, no el placeholder --

	@Test
	void conBytesDeTexturaRealesElBbmodelLosEmbebeTalCualEnVezDelPlaceholder() throws Exception {
		byte[] realPng = solidColorPng(32, 32, 0x336699);
		MobProjectModel model = modelWith(32, 32, "textures/real-hash.png");

		String json = BBModelExporterV5.export(model, realPng);
		JsonNode texture = JSON.readTree(json).path("textures").get(0);

		assertThat(texture.path("name").asText()).isEqualTo("texture");
		assertThat(texture.path("width").asInt()).isEqualTo(32);
		assertThat(texture.path("height").asInt()).isEqualTo(32);
		String dataUri = texture.path("source").asText();
		byte[] embeddedPng = Base64.getDecoder().decode(dataUri.substring(dataUri.indexOf(',') + 1));
		assertThat(embeddedPng).isEqualTo(realPng); // bytes REALES, no recodificados ni alterados

		BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(embeddedPng));
		assertThat(decoded.getWidth()).isEqualTo(32);
		assertThat(decoded.getHeight()).isEqualTo(32);
		assertThat(decoded.getRGB(0, 0) & 0xFFFFFF).isEqualTo(0x336699);
	}

	// -- AC #2 de 056: sin ninguna región pintada, el placeholder queda igual (sin cambios de fondo) --

	@Test
	void sinBytesDeTexturaRealSigueUsandoElPlaceholderCheckerboardSinCambios() throws Exception {
		MobProjectModel modelSinStorageKey = modelWith(16, 16, null);

		String jsonOverloadNull = BBModelExporterV5.export(modelSinStorageKey, null);
		String jsonOverloadUnico = BBModelExporterV5.export(modelSinStorageKey);

		// El overload de un solo argumento es 100% equivalente a pasar `null`
		// explícito -- mismo bbmodel byte a byte (determinismo, ticket 044).
		assertThat(jsonOverloadNull).isEqualTo(jsonOverloadUnico);

		JsonNode texture = JSON.readTree(jsonOverloadUnico).path("textures").get(0);
		assertThat(texture.path("name").asText()).isEqualTo("placeholder");
		assertThat(texture.path("width").asInt()).isEqualTo(16);
		assertThat(texture.path("height").asInt()).isEqualTo(16);
	}

	// -- Determinismo (ticket 044): exportar dos veces con los MISMOS bytes reales produce bytes idénticos --

	@Test
	void exportarDosVecesConLosMismosBytesRealesProduceElMismoBbmodel() throws Exception {
		byte[] realPng = solidColorPng(8, 8, 0xAABBCC);
		MobProjectModel model = modelWith(8, 8, "textures/hash.png");

		String first = BBModelExporterV5.export(model, realPng);
		String second = BBModelExporterV5.export(model, realPng);

		assertThat(first).isEqualTo(second);
	}

}
