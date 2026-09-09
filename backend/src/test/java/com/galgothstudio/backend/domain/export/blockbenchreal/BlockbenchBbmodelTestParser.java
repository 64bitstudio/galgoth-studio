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
import java.util.EnumMap;
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
 *
 * <p>Soporta ambas formas de `outliner` para los bones (ticket 012, fixtures
 * reales v5/v4 provistas por el PO desde Blockbench real): v5 separa
 * `groups[]` (con `name`/`origin`/`rotation`) de un `outliner[]` mínimo
 * (`uuid`/`isOpen`/`children` únicamente); v4 embebe esos mismos campos
 * directo en el nodo del `outliner`. {@link #walkOutliner} detecta cuál
 * de las dos formas tiene cada nodo (¿tiene `name` inline?) y si no,
 * busca esos datos en `groups[]` por `uuid`.
 *
 * <p><b>Hallazgo real de conformidad (ticket 012, fixture `01_cuboid_simple`)</b>:
 * Blockbench permite un cuboid colgado DIRECTO de la raíz del `outliner`,
 * sin ningún group/bone que lo contenga -- pero {@code Cuboid.boneId} en
 * nuestro dominio es un `String` no-nulo obligatorio (todo cuboid
 * pertenece siempre a un bone, invariante de diseño desde el ticket 004,
 * necesaria para animación). Para representar este caso real sin perder
 * geometría (posición/tamaño/rotación/caras se preservan exactos), este
 * parser sintetiza un bone raíz implícito ({@link #SYNTHETIC_ROOT_BONE_ID})
 * con pivot/rotación cero y reasigna esos cuboids a él -- documentado
 * explícitamente como la única transformación no literal que este parser
 * aplica.
 */
final class BlockbenchBbmodelTestParser {

	private static final String SYNTHETIC_ROOT_BONE_ID = "synthetic-root";
	private static final String SYNTHETIC_ROOT_BONE_NAME = "synthetic_root";

	record ParseResult(MobProjectModel model, Set<String> orphanedElementUuids) {
	}

	private BlockbenchBbmodelTestParser() {
	}

	static ParseResult parse(JsonNode root, String mobId, String projectId) {
		Map<String, JsonNode> elementsByUuid = new HashMap<>();
		for (JsonNode element : root.path("elements")) {
			elementsByUuid.put(element.path("uuid").asText(), element);
		}
		Map<String, JsonNode> groupsByUuid = new HashMap<>();
		for (JsonNode group : root.path("groups")) {
			groupsByUuid.put(group.path("uuid").asText(), group);
		}

		List<Bone> bones = new ArrayList<>();
		List<Cuboid> cuboids = new ArrayList<>();
		Set<String> referencedUuids = new HashSet<>();
		walkOutliner(root.path("outliner"), null, elementsByUuid, groupsByUuid, bones, cuboids, referencedUuids);
		attachRootlessCuboidsToSyntheticBone(bones, cuboids);

		Set<String> orphans = new HashSet<>(elementsByUuid.keySet());
		orphans.removeAll(referencedUuids);

		int width = root.path("resolution").path("width").asInt();
		int height = root.path("resolution").path("height").asInt();
		FormatVersion formatVersion = "5.0".equals(root.path("meta").path("format_version").asText()) ? FormatVersion.V5 : FormatVersion.V4;

		MobProjectModel model = new MobProjectModel(
				mobId, projectId, root.path("name").asText(), BaseType.HUMANOID,
				MobProjectModel.UNITS_MINECRAFT_PIXELS, bones, cuboids, new TextureDocument(width, height, null),
				new UvLayout(width, height, List.of()), List.of(), new ExportSettings(formatVersion), List.of());

		return new ParseResult(model, orphans);
	}

	/**
	 * Cada nodo del outliner es un bone (objeto) o un cuboid (uuid crudo,
	 * referenciando `elements`). Para un bone, sus datos (`name`/`origin`/
	 * `rotation`) están o bien INLINE en el propio nodo (v4, embebido) o
	 * bien separados en `groups[]` por `uuid` (v5, el nodo del outliner
	 * solo trae `uuid`/`isOpen`/`children`) -- se detecta por la presencia
	 * de `name` inline.
	 */
	private static void walkOutliner(
			JsonNode nodes, String parentBoneId, Map<String, JsonNode> elementsByUuid, Map<String, JsonNode> groupsByUuid,
			List<Bone> bones, List<Cuboid> cuboids, Set<String> referencedUuids) {
		for (JsonNode node : nodes) {
			if (node.isTextual()) {
				referencedUuids.add(node.asText());
				cuboids.add(toCuboid(elementsByUuid.get(node.asText()), parentBoneId));
			} else {
				String boneId = node.path("uuid").asText();
				JsonNode groupData = node.has("name") ? node : groupsByUuid.getOrDefault(boneId, node);
				bones.add(
						new Bone(
								boneId, groupData.path("name").asText(), parentBoneId, readVec3(groupData.path("origin")),
								readVec3OrZero(groupData.path("rotation"))));
				walkOutliner(node.path("children"), boneId, elementsByUuid, groupsByUuid, bones, cuboids, referencedUuids);
			}
		}
	}

	/** Ver el hallazgo documentado en el docstring de la clase -- solo actúa si de verdad hay algún cuboid sin bone. */
	private static void attachRootlessCuboidsToSyntheticBone(List<Bone> bones, List<Cuboid> cuboids) {
		boolean anyRootless = cuboids.stream().anyMatch(c -> c.boneId() == null);
		if (!anyRootless) {
			return;
		}
		bones.add(0, new Bone(SYNTHETIC_ROOT_BONE_ID, SYNTHETIC_ROOT_BONE_NAME, null, new Vec3(0, 0, 0), new Vec3(0, 0, 0)));
		for (int i = 0; i < cuboids.size(); i++) {
			Cuboid cuboid = cuboids.get(i);
			if (cuboid.boneId() == null) {
				cuboids.set(
						i,
						new Cuboid(
								cuboid.id(), cuboid.name(), SYNTHETIC_ROOT_BONE_ID, cuboid.from(), cuboid.to(), cuboid.origin(),
								cuboid.rotation(), cuboid.faces()));
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
		Map<FaceName, Face> faces = new EnumMap<>(FaceName.class);
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
