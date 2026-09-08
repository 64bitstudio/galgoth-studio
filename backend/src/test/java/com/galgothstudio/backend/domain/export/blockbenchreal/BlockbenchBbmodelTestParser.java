package com.galgothstudio.backend.domain.export.blockbenchreal;

import com.fasterxml.jackson.databind.JsonNode;
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
import com.galgothstudio.backend.domain.model.Vec3;
import com.galgothstudio.backend.domain.model.Vec4;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Ticket 012 -- parser TEST-ONLY de `.bbmodel` reales para fixtures de
 * conformidad. Deliberadamente NO es un `BBModelImporter` de producción
 * (excluido explícitamente del alcance del proyecto, ver ticket 008 y la
 * nota del propio ticket 012): vive exclusivamente bajo `src/test/`,
 * ningún paquete de producción lo importa. Mismo algoritmo ya validado en
 * Python (`contracts/fixtures/scripts/convert-carcomido-sample.py`,
 * ticket 008), reimplementado en Java para probar que el dominio interno
 * puede representar un archivo real SIN pérdida, de forma independiente
 * del script Python.
 *
 * <p>Soporta ambas formas de rotación observadas en `.bbmodel` reales
 * (ver ADR 0001): array {@code [x,y,z]} (groups siempre, cubes en v5) y
 * escalar+eje {@code rotation:-7, axis:"z"} (cubes en archivos viejos
 * como el sample de este proyecto, `format_version: "4.10"`).
 */
final class BlockbenchBbmodelTestParser {

	record ParseResult(MobProjectModel model, Set<String> orphanedElementUuids) {
	}

	private BlockbenchBbmodelTestParser() {
	}

	static ParseResult parse(JsonNode root, String mobId, String projectId) {
		Map<String, JsonNode> elementsByUuid = new HashMap<>();
		for (JsonNode element : root.path("elements")) {
			elementsByUuid.put(element.path("uuid").asText(), element);
		}

		List<Bone> bones = new ArrayList<>();
		List<Cuboid> cuboids = new ArrayList<>();
		Set<String> referencedUuids = new HashSet<>();
		walkOutliner(root.path("outliner"), null, elementsByUuid, bones, cuboids, referencedUuids);

		Set<String> orphans = new HashSet<>(elementsByUuid.keySet());
		orphans.removeAll(referencedUuids);

		int width = root.path("resolution").path("width").asInt();
		int height = root.path("resolution").path("height").asInt();

		MobProjectModel model = new MobProjectModel(
				mobId, projectId, root.path("name").asText(), BaseType.HUMANOID,
				MobProjectModel.UNITS_MINECRAFT_PIXELS, bones, cuboids, new TextureDocument(width, height, null),
				new UvLayout(width, height, List.of()), List.of(), new ExportSettings(FormatVersion.V4), List.of());

		return new ParseResult(model, orphans);
	}

	private static void walkOutliner(
			JsonNode nodes, String parentBoneId, Map<String, JsonNode> elementsByUuid, List<Bone> bones,
			List<Cuboid> cuboids, Set<String> referencedUuids) {
		for (JsonNode node : nodes) {
			if (node.isTextual()) {
				referencedUuids.add(node.asText());
				cuboids.add(toCuboid(elementsByUuid.get(node.asText()), parentBoneId));
			} else {
				String boneId = node.path("uuid").asText();
				bones.add(
						new Bone(
								boneId, node.path("name").asText(), parentBoneId, readVec3(node.path("origin")),
								readVec3OrZero(node.path("rotation"))));
				walkOutliner(node.path("children"), boneId, elementsByUuid, bones, cuboids, referencedUuids);
			}
		}
	}

	private static Cuboid toCuboid(JsonNode element, String boneId) {
		return new Cuboid(
				element.path("uuid").asText(), element.path("name").asText(), boneId, readVec3(element.path("from")),
				readVec3(element.path("to")), readVec3(element.path("origin")), readCubeRotation(element),
				readFaces(element.path("faces")));
	}

	/** Cube elements: array [x,y,z] (v5) o escalar+eje (`rotation`+`axis`, forma vieja -- ver ADR 0001). */
	private static Vec3 readCubeRotation(JsonNode element) {
		JsonNode rotationNode = element.path("rotation");
		if (rotationNode.isArray()) {
			return readVec3(rotationNode);
		}
		if (rotationNode.isMissingNode() || rotationNode.isNull()) {
			return new Vec3(0, 0, 0);
		}
		double scalar = rotationNode.asDouble();
		String axis = element.path("axis").asText("z");
		return switch (axis) {
			case "x" -> new Vec3(scalar, 0, 0);
			case "y" -> new Vec3(0, scalar, 0);
			default -> new Vec3(0, 0, scalar);
		};
	}

	private static Vec3 readVec3(JsonNode array) {
		return new Vec3(array.get(0).asDouble(), array.get(1).asDouble(), array.get(2).asDouble());
	}

	private static Vec3 readVec3OrZero(JsonNode maybeArray) {
		return maybeArray.isArray() ? readVec3(maybeArray) : new Vec3(0, 0, 0);
	}

	private static CuboidFaces readFaces(JsonNode facesNode) {
		Map<FaceName, Face> faces = new HashMap<>();
		for (FaceName faceName : FaceName.values()) {
			JsonNode faceNode = facesNode.path(faceName.name().toLowerCase());
			JsonNode uvNode = faceNode.path("uv");
			Vec4 uv = new Vec4(uvNode.get(0).asDouble(), uvNode.get(1).asDouble(), uvNode.get(2).asDouble(), uvNode.get(3).asDouble());
			JsonNode textureNode = faceNode.path("texture");
			Integer texture = textureNode.isNull() || textureNode.isMissingNode() ? null : textureNode.asInt();
			faces.put(faceName, new Face(uv, texture));
		}
		return new CuboidFaces(
				faces.get(FaceName.NORTH), faces.get(FaceName.SOUTH), faces.get(FaceName.EAST), faces.get(FaceName.WEST),
				faces.get(FaceName.UP), faces.get(FaceName.DOWN));
	}

}
