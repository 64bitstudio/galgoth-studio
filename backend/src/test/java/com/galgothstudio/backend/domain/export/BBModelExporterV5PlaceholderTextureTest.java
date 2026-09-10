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
import com.galgothstudio.backend.domain.uv.AlphaAutoPackStrategy;
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
 * Ticket 011 -- textura placeholder auto-generada (AC #1 de ese ticket,
 * sin cambios de fondo: mecanismo ortogonal a la UV en sí). Reescrito en
 * el ticket 044 (Diseño técnico §3 de
 * `docs/definiciones/galgoth-studio-fase3-textura.md`): el overload
 * {@code export(MobProjectModel, UvLayoutStrategy)} que este archivo
 * cubría (recompute + overflow AL EXPORTAR) fue eliminado -- esa
 * responsabilidad se movió por completo a los llamadores de motor
 * (`GeometryEngine`/`GeometryPlannerService`/`AiGeometryEditPlannerService`
 * vía `UvLayoutSelector`), nunca al exportador. Los modelos de este
 * archivo llegan con la UV YA resuelta (como llegaría desde una Revision
 * real), tal como exige el nuevo contrato de {@link BBModelExporterV5#export(MobProjectModel)}.
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

	/** Simula lo que hoy hace `GeometryEngine`/`UvLayoutSelector` ANTES de llegar al exportador -- ticket 044. */
	private static MobProjectModel withUvResolvedBy(UvLayoutStrategy strategy, MobProjectModel model) {
		int width = model.texture().width();
		int height = model.texture().height();
		UvLayoutStrategy.Result result = strategy.layout(model.cuboids(), width, height);
		return new MobProjectModel(
				model.mobId(), model.projectId(), model.name(), model.baseType(), model.units(), model.bones(),
				result.cuboids(), model.texture(), new UvLayout(width, height, result.regions()), model.animations(),
				model.exportSettings(), model.referenceImages());
	}

	// -- AC #1 (ticket 011): textura placeholder con dimensiones EXACTAS al atlas --------

	@Test
	void generaUnaTexturaPlaceholderConDimensionesExactasAlAtlasDelModelo() throws Exception {
		String boneId = UUID.randomUUID().toString();
		String cuboidId = UUID.randomUUID().toString();
		Bone bone = new Bone(boneId, "torso", null, new Vec3(0, 0, 0), new Vec3(0, 0, 0));
		Cuboid cuboid = new Cuboid(
				cuboidId, "body", boneId, new Vec3(-4, 0, -2), new Vec3(4, 8, 2), new Vec3(0, 0, 0), new Vec3(0, 0, 0),
				emptyFaces());
		MobProjectModel model = withUvResolvedBy(
				new AlphaAutoPackStrategy(), modelWith(64, 32, List.of(bone), List.of(cuboid)));

		String json = BBModelExporterV5.export(model);
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

	// -- Ticket 044, AC #2: determinismo -- exportar dos veces sin cambios produce bytes idénticos --

	@Test
	void exportarElMismoModeloDosVecesProduceBytesIdenticos() {
		String boneId = UUID.randomUUID().toString();
		Bone bone = new Bone(boneId, "torso", null, new Vec3(0, 0, 0), new Vec3(0, 0, 0));
		Cuboid head = new Cuboid(
				UUID.randomUUID().toString(), "head", boneId, new Vec3(-4, 0, -4), new Vec3(4, 8, 4), new Vec3(0, 0, 0),
				new Vec3(0, 0, 0), emptyFaces());
		MobProjectModel model =
				withUvResolvedBy(new AlphaAutoPackStrategy(), modelWith(64, 64, List.of(bone), List.of(head)));

		String first = BBModelExporterV5.export(model);
		String second = BBModelExporterV5.export(model);

		assertThat(first).isEqualTo(second);
	}

	// -- Ticket 044, AC #1: el exportador NUNCA invoca ninguna estrategia de UV --------

	/**
	 * Antes del ticket 044, un modelo cuya UV excedía lo que cabría en el
	 * atlas según {@link AlphaAutoPackStrategy} hacía fallar el EXPORT
	 * (overload {@code export(model, strategy)}, {@code UvAtlasOverflowException}).
	 * Con el nuevo contrato, el exportador ya ni siquiera tiene forma de
	 * detectarlo -- no depende de {@code domain.uv} en absoluto -- así que
	 * un modelo en ese estado (guardado tal cual por una Revision ya
	 * existente, legítimo o no) se exporta SIN fallar y con la UV
	 * EXACTAMENTE como llegó, prueba indirecta de que ninguna estrategia
	 * se invocó (si se hubiera invocado, este test fallaría con
	 * {@code UvAtlasOverflowException}).
	 */
	@Test
	void unaUvQueExcederiaElAtlasSegunAlphaAutoPackSeExportaSinFallarYSinTocarla() throws Exception {
		String boneId = UUID.randomUUID().toString();
		Bone bone = new Bone(boneId, "torso", null, new Vec3(0, 0, 0), new Vec3(0, 0, 0));
		// Cuboid 8x8x8 -> footprint 32x16, no cabe en un atlas de 8x8, pero la
		// UV ya almacenada (rect fuera de esos bounds) se conserva tal cual.
		Vec4 storedRect = new Vec4(0, 0, 32, 16);
		Face storedFace = new Face(storedRect, 0);
		CuboidFaces storedFaces =
				new CuboidFaces(storedFace, storedFace, storedFace, storedFace, storedFace, storedFace);
		Cuboid huge = new Cuboid(
				UUID.randomUUID().toString(), "huge", boneId, new Vec3(-4, -4, -4), new Vec3(4, 4, 4), new Vec3(0, 0, 0),
				new Vec3(0, 0, 0), storedFaces);
		MobProjectModel model = modelWith(8, 8, List.of(bone), List.of(huge));

		// Si el exportador invocara AlphaAutoPackStrategy internamente, esta
		// línea lanzaría UvAtlasOverflowException -- no lo hace.
		String json = BBModelExporterV5.export(model);

		JsonNode faceUv = JSON.readTree(json).path("elements").get(0).path("faces").path("north").path("uv");
		assertThat(faceUv.get(0).asDouble()).isEqualTo(storedRect.a());
		assertThat(faceUv.get(1).asDouble()).isEqualTo(storedRect.b());
		assertThat(faceUv.get(2).asDouble()).isEqualTo(storedRect.c());
		assertThat(faceUv.get(3).asDouble()).isEqualTo(storedRect.d());
	}

}
