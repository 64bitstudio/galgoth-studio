package com.galgothstudio.backend.domain.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import com.galgothstudio.backend.domain.uv.AlphaAutoPackStrategy;
import com.galgothstudio.backend.domain.uv.UvAtlasOverflowException;
import com.galgothstudio.backend.domain.uv.UvLayoutStrategy;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/**
 * Ticket 011 -- textura placeholder auto-generada + formalización del
 * atlas. Cubre las 3 AC del ticket sobre el overload
 * {@link BBModelExporterV5#export(MobProjectModel, UvLayoutStrategy)}.
 */
class BBModelExporterV5PlaceholderTextureTest {

	private static final ObjectMapper JSON = new ObjectMapper();

	private static CuboidFaces emptyFaces() {
		Face placeholder = new Face(new Vec4(0, 0, 0, 0), null);
		return new CuboidFaces(placeholder, placeholder, placeholder, placeholder, placeholder, placeholder);
	}

	private static MobProjectModel modelWith(int textureWidth, int textureHeight, List<Bone> bones, List<Cuboid> cuboids) {
		return new MobProjectModel(
				"mob-1", "project-1", "Test Mob", BaseType.HUMANOID, MobProjectModel.UNITS_MINECRAFT_PIXELS, bones,
				cuboids, new TextureDocument(textureWidth, textureHeight, null),
				new UvLayout(textureWidth, textureHeight, new ArrayList<>()), new ArrayList<>(),
				new ExportSettings(FormatVersion.V5), new ArrayList<>());
	}

	// -- AC #1: textura placeholder con dimensiones EXACTAS al atlas --------

	@Test
	void generaUnaTexturaPlaceholderConDimensionesExactasAlAtlasDelModelo() throws Exception {
		String boneId = UUID.randomUUID().toString();
		String cuboidId = UUID.randomUUID().toString();
		Bone bone = new Bone(boneId, "torso", null, new Vec3(0, 0, 0), new Vec3(0, 0, 0));
		Cuboid cuboid = new Cuboid(
				cuboidId, "body", boneId, new Vec3(-4, 0, -2), new Vec3(4, 8, 2), new Vec3(0, 0, 0), new Vec3(0, 0, 0),
				emptyFaces());
		MobProjectModel model = modelWith(64, 32, List.of(bone), List.of(cuboid));

		String json = BBModelExporterV5.export(model, new AlphaAutoPackStrategy());
		JsonNode texture = JSON.readTree(json).path("textures").get(0);

		assertThat(texture.path("width").asInt()).isEqualTo(64);
		assertThat(texture.path("height").asInt()).isEqualTo(32);

		// El PNG embebido en la data URI también debe medir exactamente eso
		// -- no basta con que el campo "width"/"height" lo *diga*.
		String dataUri = texture.path("source").asText();
		String base64Payload = dataUri.substring(dataUri.indexOf(',') + 1);
		BufferedImage decodedPng = ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(base64Payload)));
		assertThat(decodedPng.getWidth()).isEqualTo(64);
		assertThat(decodedPng.getHeight()).isEqualTo(32);
	}

	// -- AC #2: la UV generada por 006 cae dentro de esos bounds -------------

	@Test
	void laUvGeneradaPorAutoUvCaeDentroDeLosBoundsDeLaTexturaPlaceholder() throws Exception {
		String boneId = UUID.randomUUID().toString();
		Bone bone = new Bone(boneId, "torso", null, new Vec3(0, 0, 0), new Vec3(0, 0, 0));
		Cuboid head = new Cuboid(
				UUID.randomUUID().toString(), "head", boneId, new Vec3(-4, 0, -4), new Vec3(4, 8, 4),
				new Vec3(0, 0, 0), new Vec3(0, 0, 0), emptyFaces());
		Cuboid body = new Cuboid(
				UUID.randomUUID().toString(), "body", boneId, new Vec3(-4, 0, -2), new Vec3(4, 12, 2),
				new Vec3(0, 0, 0), new Vec3(0, 0, 0), emptyFaces());
		MobProjectModel model = modelWith(64, 64, List.of(bone), List.of(head, body));

		String json = BBModelExporterV5.export(model, new AlphaAutoPackStrategy());
		JsonNode doc = JSON.readTree(json);
		int textureWidth = doc.path("textures").get(0).path("width").asInt();
		int textureHeight = doc.path("textures").get(0).path("height").asInt();

		for (JsonNode element : doc.path("elements")) {
			for (JsonNode face : element.path("faces")) {
				JsonNode uv = face.path("uv");
				assertThat(uv.get(0).asDouble()).isBetween(0.0, (double) textureWidth);
				assertThat(uv.get(2).asDouble()).isBetween(0.0, (double) textureWidth);
				assertThat(uv.get(1).asDouble()).isBetween(0.0, (double) textureHeight);
				assertThat(uv.get(3).asDouble()).isBetween(0.0, (double) textureHeight);
			}
		}
	}

	// -- AC #3: overflow -> falla explícita, nunca agranda en silencio -------

	@Test
	void unModeloQueDisparaUvAtlasOverflowFallaElExportEnVezDeAgrandarLaTexturaEnSilencio() {
		String boneId = UUID.randomUUID().toString();
		Bone bone = new Bone(boneId, "torso", null, new Vec3(0, 0, 0), new Vec3(0, 0, 0));
		// Cuboid 8x8x8 -> footprint 32x16, no cabe en un atlas de 8x8.
		Cuboid huge = new Cuboid(
				UUID.randomUUID().toString(), "huge", boneId, new Vec3(-4, -4, -4), new Vec3(4, 4, 4),
				new Vec3(0, 0, 0), new Vec3(0, 0, 0), emptyFaces());
		MobProjectModel model = modelWith(8, 8, List.of(bone), List.of(huge));
		AlphaAutoPackStrategy strategy = new AlphaAutoPackStrategy();

		assertThatThrownBy(() -> BBModelExporterV5.export(model, strategy)).isInstanceOf(UvAtlasOverflowException.class);
	}

}
