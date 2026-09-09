package com.galgothstudio.backend.domain.export.validation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.galgothstudio.backend.domain.export.validation.ValidationIssue.Severity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Validador de compatibilidad FMM (FreeMinecraftModels) -- ticket 013,
 * master prompt §15/HU-20. Cubre los checks aplicables a geometría/bones/
 * UV/textura placeholder de este ciclo (sin animación, fuera de alcance).
 * Opera sobre el JSON `.bbmodel` ya exportado (010+011) -- nunca confía
 * en que quien lo produjo (nuestro exportador u otro) ya garantizó estas
 * invariantes, las revalida de cero como último checkpoint antes del export.
 */
public final class FmmCompatibilityValidator {

	private static final String FIELD_ELEMENTS = "elements";
	private static final String FIELD_GROUPS = "groups";
	private static final String FIELD_CHILDREN = "children";
	private static final String[] AXIS_NAMES = { "x", "y", "z" };

	private FmmCompatibilityValidator() {
	}

	public static ValidationResult validate(String bbmodelJson) {
		JsonNode root;
		try {
			root = new ObjectMapper().readTree(bbmodelJson);
		} catch (JsonProcessingException e) {
			return new ValidationResult(
					List.of(
							new ValidationIssue(
									Severity.ERROR, "JSON_VALID", "(documento completo)",
									"El .bbmodel no es JSON válido: " + e.getMessage())));
		}

		List<ValidationIssue> issues = new ArrayList<>();
		Map<String, JsonNode> elementsByUuid = indexByUuid(root.path(FIELD_ELEMENTS));
		Set<String> groupUuids = new HashSet<>();
		root.path(FIELD_GROUPS).forEach(group -> groupUuids.add(group.path("uuid").asText()));

		checkUuidUniqueness(root, issues);
		checkOutlinerReferences(root.path("outliner"), elementsByUuid.keySet(), groupUuids, issues);
		int textureCount = root.path("textures").size();
		int textureWidth = root.path("resolution").path("width").asInt();
		int textureHeight = root.path("resolution").path("height").asInt();
		for (JsonNode element : root.path(FIELD_ELEMENTS)) {
			checkCuboidDimensions(element, issues);
			checkFaces(element, textureWidth, textureHeight, textureCount, issues);
		}
		checkSpecialBoneNames(root.path("outliner"), root.path(FIELD_GROUPS), issues);

		return new ValidationResult(issues);
	}

	private static Map<String, JsonNode> indexByUuid(JsonNode array) {
		Map<String, JsonNode> byUuid = new HashMap<>();
		array.forEach(node -> byUuid.put(node.path("uuid").asText(), node));
		return byUuid;
	}

	// -- unicidad de UUID -----------------------------------------------------

	private static void checkUuidUniqueness(JsonNode root, List<ValidationIssue> issues) {
		Set<String> seen = new HashSet<>();
		Set<String> duplicates = new HashSet<>();
		for (JsonNode array : List.of(root.path(FIELD_ELEMENTS), root.path(FIELD_GROUPS))) {
			for (JsonNode node : array) {
				String uuid = node.path("uuid").asText();
				if (!seen.add(uuid)) {
					duplicates.add(uuid);
				}
			}
		}
		for (String duplicate : duplicates) {
			issues.add(
					new ValidationIssue(
							Severity.ERROR, "UUID_UNIQUE", duplicate,
							"El uuid '" + duplicate + "' aparece más de una vez entre elements/groups."));
		}
	}

	// -- referencias de outliner ------------------------------------------------

	/**
	 * <p><b>Hallazgo real (ticket 033, suite E2E de aceptación)</b>: este
	 * check asumía SIEMPRE la forma v5 de `outliner` (grupo mínimo
	 * {@code {uuid,isOpen,children}}, datos reales en `groups[]` aparte)
	 * -- nunca se había corrido este validador contra la salida de
	 * `BBModelExporterV4` (014, groups EMBEBIDOS directo en el nodo del
	 * outliner, sin `groups[]` top-level), así que cada bone de un v4
	 * real disparaba un falso `OUTLINER_REFERENCE` ("no existe en
	 * 'groups'"). Un group v4 se detecta por tener `name` inline (mismo
	 * criterio ya usado en `BlockbenchBbmodelTestParser`, test-only,
	 * ticket 012) -- en ese caso el nodo ES la definición real, no hay
	 * nada que resolver contra `groups[]`.
	 */
	private static void checkOutlinerReferences(
			JsonNode outliner, Set<String> elementUuids, Set<String> groupUuids, List<ValidationIssue> issues) {
		for (JsonNode node : outliner) {
			if (node.isTextual()) {
				String uuid = node.asText();
				if (!elementUuids.contains(uuid)) {
					issues.add(
							new ValidationIssue(
									Severity.ERROR, "OUTLINER_REFERENCE", uuid,
									"outliner referencia el elemento '" + uuid + "' pero no existe en 'elements'."));
				}
			} else {
				String uuid = node.path("uuid").asText();
				boolean isV4EmbeddedGroup = node.has("name");
				if (!isV4EmbeddedGroup && !groupUuids.contains(uuid)) {
					issues.add(
							new ValidationIssue(
									Severity.ERROR, "OUTLINER_REFERENCE", uuid,
									"outliner referencia el group '" + uuid + "' pero no existe en 'groups'."));
				}
				checkOutlinerReferences(node.path(FIELD_CHILDREN), elementUuids, groupUuids, issues);
			}
		}
	}

	// -- dimensiones de cuboid --------------------------------------------------

	private static void checkCuboidDimensions(JsonNode element, List<ValidationIssue> issues) {
		String uuid = element.path("uuid").asText();
		JsonNode from = element.path("from");
		JsonNode to = element.path("to");
		for (int i = 0; i < 3; i++) {
			double size = to.get(i).asDouble() - from.get(i).asDouble();
			if (size <= 0) {
				issues.add(
						new ValidationIssue(
								Severity.ERROR, "CUBOID_DIMENSIONS", uuid,
								"Dimensión en eje " + AXIS_NAMES[i] + " del cuboid '" + element.path("name").asText()
										+ "' (" + uuid + ") es " + size + " -- debe ser > 0."));
			}
		}
	}

	// -- UV e índice de textura ---------------------------------------------------

	private static void checkFaces(
			JsonNode element, int textureWidth, int textureHeight, int textureCount, List<ValidationIssue> issues) {
		String uuid = element.path("uuid").asText();
		element.path("faces").properties().forEach(entry -> {
			String faceName = entry.getKey();
			JsonNode face = entry.getValue();
			String label = faceLabel(faceName, uuid);
			if (checkFaceUvShape(face, uuid, label, issues)) {
				checkFaceUvBounds(face.path("uv"), uuid, label, textureWidth, textureHeight, issues);
			}
			checkFaceTextureIndex(face.path("texture"), uuid, label, textureCount, issues);
		});
	}

	private static String faceLabel(String faceName, String cuboidUuid) {
		return "Cara '" + faceName + "' del cuboid '" + cuboidUuid + "'";
	}

	/** @return {@code true} si el uv tiene la forma esperada (4 componentes) -- para saltar el check de bounds si no. */
	private static boolean checkFaceUvShape(JsonNode face, String uuid, String label, List<ValidationIssue> issues) {
		JsonNode uv = face.path("uv");
		if (uv.isArray() && uv.size() == 4) {
			return true;
		}
		issues.add(new ValidationIssue(Severity.ERROR, "FACE_UV_SHAPE", uuid, label + " no tiene un uv de 4 componentes."));
		return false;
	}

	private static void checkFaceUvBounds(
			JsonNode uv, String uuid, String label, int textureWidth, int textureHeight, List<ValidationIssue> issues) {
		for (int i = 0; i < 4; i++) {
			double value = uv.get(i).asDouble();
			double limit = i % 2 == 0 ? textureWidth : textureHeight;
			if (value < 0 || value > limit) {
				issues.add(
						new ValidationIssue(
								Severity.ERROR, "FACE_UV_BOUNDS", uuid,
								label + ": coordenada uv[" + i + "]=" + value + " fuera de [0," + limit + "] (atlas "
										+ textureWidth + "x" + textureHeight + ")."));
			}
		}
	}

	private static void checkFaceTextureIndex(
			JsonNode textureIndexNode, String uuid, String label, int textureCount, List<ValidationIssue> issues) {
		if (textureIndexNode.isNull() || textureIndexNode.isMissingNode()) {
			return;
		}
		int index = textureIndexNode.asInt();
		if (index < 0 || index >= textureCount) {
			issues.add(
					new ValidationIssue(
							Severity.ERROR, "TEXTURE_INDEX", uuid,
							label + " referencia el índice de textura " + index + ", pero 'textures' solo tiene "
									+ textureCount + " entrada(s)."));
		}
	}

	// -- convención de nombres especiales de bone --------------------------------

	/**
	 * Verificado contra el código fuente real de FreeMinecraftModels
	 * (`BoneBlueprint.generateAndWriteCubes`): un bone cuyo nombre es
	 * EXACTAMENTE (sin distinguir mayúsculas) {@code hitbox} o
	 * {@code tag_name} nunca escribe la geometría de sus cuboids hijos al
	 * modelo importado -- se pierden en silencio. Se reporta como WARNING
	 * (el archivo sigue siendo válido, pero el usuario probablemente no
	 * quería ese resultado).
	 */
	private static void checkSpecialBoneNames(JsonNode outliner, JsonNode groups, List<ValidationIssue> issues) {
		Map<String, JsonNode> groupsByUuid = indexByUuid(groups);
		for (JsonNode node : outliner) {
			checkSpecialBoneNamesRecursive(node, groupsByUuid, issues);
		}
	}

	private static void checkSpecialBoneNamesRecursive(
			JsonNode node, Map<String, JsonNode> groupsByUuid, List<ValidationIssue> issues) {
		if (node.isTextual()) {
			return;
		}
		String uuid = node.path("uuid").asText();
		// v4 embebe name/etc. directo en el nodo; v5 los deja aparte en groups[] (ver checkOutlinerReferences).
		JsonNode group = node.has("name") ? node : groupsByUuid.get(uuid);
		String name = group != null ? group.path("name").asText() : "";
		if (isSpecialNoGeometryBoneName(name) && hasCuboidChild(node)) {
			issues.add(
					new ValidationIssue(
							Severity.WARNING, "SPECIAL_BONE_GEOMETRY_DROPPED", uuid,
							"El bone '" + name + "' (" + uuid + ") tiene cuboids hijos, pero FreeMinecraftModels nunca"
									+ " genera geometría visible para bones llamados exactamente 'hitbox' o 'tag_name'"
									+ " -- esos cuboids no se mostrarán en el modelo importado."));
		}
		for (JsonNode child : node.path(FIELD_CHILDREN)) {
			checkSpecialBoneNamesRecursive(child, groupsByUuid, issues);
		}
	}

	private static boolean isSpecialNoGeometryBoneName(String name) {
		return name.equalsIgnoreCase("hitbox") || name.equalsIgnoreCase("tag_name");
	}

	private static boolean hasCuboidChild(JsonNode node) {
		for (JsonNode child : node.path(FIELD_CHILDREN)) {
			if (child.isTextual()) {
				return true;
			}
		}
		return false;
	}

}
