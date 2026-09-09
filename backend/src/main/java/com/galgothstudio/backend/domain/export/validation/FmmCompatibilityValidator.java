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
		Map<String, JsonNode> elementsByUuid = indexByUuid(root.path("elements"));
		Set<String> groupUuids = new HashSet<>();
		root.path("groups").forEach(group -> groupUuids.add(group.path("uuid").asText()));

		checkUuidUniqueness(root, issues);
		checkOutlinerReferences(root.path("outliner"), elementsByUuid.keySet(), groupUuids, issues);
		int textureCount = root.path("textures").size();
		int textureWidth = root.path("resolution").path("width").asInt();
		int textureHeight = root.path("resolution").path("height").asInt();
		for (JsonNode element : root.path("elements")) {
			checkCuboidDimensions(element, issues);
			checkFaceUvAndTextureIndex(element, textureWidth, textureHeight, textureCount, issues);
		}
		checkSpecialBoneNames(root.path("outliner"), root.path("groups"), issues);

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
		for (JsonNode array : List.of(root.path("elements"), root.path("groups"))) {
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
				if (!groupUuids.contains(uuid)) {
					issues.add(
							new ValidationIssue(
									Severity.ERROR, "OUTLINER_REFERENCE", uuid,
									"outliner referencia el group '" + uuid + "' pero no existe en 'groups'."));
				}
				checkOutlinerReferences(node.path("children"), elementUuids, groupUuids, issues);
			}
		}
	}

	// -- dimensiones de cuboid --------------------------------------------------

	private static void checkCuboidDimensions(JsonNode element, List<ValidationIssue> issues) {
		String uuid = element.path("uuid").asText();
		JsonNode from = element.path("from");
		JsonNode to = element.path("to");
		String[] axisNames = { "x", "y", "z" };
		for (int i = 0; i < 3; i++) {
			double size = to.get(i).asDouble() - from.get(i).asDouble();
			if (size <= 0) {
				issues.add(
						new ValidationIssue(
								Severity.ERROR, "CUBOID_DIMENSIONS", uuid,
								"Dimensión en eje " + axisNames[i] + " del cuboid '" + element.path("name").asText()
										+ "' (" + uuid + ") es " + size + " -- debe ser > 0."));
			}
		}
	}

	// -- UV e índice de textura ---------------------------------------------------

	private static void checkFaceUvAndTextureIndex(
			JsonNode element, int textureWidth, int textureHeight, int textureCount, List<ValidationIssue> issues) {
		String uuid = element.path("uuid").asText();
		element.path("faces").properties().forEach(entry -> {
			String faceName = entry.getKey();
			JsonNode face = entry.getValue();
			JsonNode uv = face.path("uv");
			if (!uv.isArray() || uv.size() != 4) {
				issues.add(
						new ValidationIssue(
								Severity.ERROR, "FACE_UV_SHAPE", uuid,
								"Cara '" + faceName + "' del cuboid '" + uuid + "' no tiene un uv de 4 componentes."));
				return;
			}
			for (int i = 0; i < 4; i++) {
				double value = uv.get(i).asDouble();
				double limit = i % 2 == 0 ? textureWidth : textureHeight;
				if (value < 0 || value > limit) {
					issues.add(
							new ValidationIssue(
									Severity.ERROR, "FACE_UV_BOUNDS", uuid,
									"Cara '" + faceName + "' del cuboid '" + uuid + "': coordenada uv[" + i + "]=" + value
											+ " fuera de [0," + limit + "] (atlas " + textureWidth + "x" + textureHeight
											+ ")."));
				}
			}
			JsonNode textureIndex = face.path("texture");
			if (!textureIndex.isNull() && !textureIndex.isMissingNode()) {
				int index = textureIndex.asInt();
				if (index < 0 || index >= textureCount) {
					issues.add(
							new ValidationIssue(
									Severity.ERROR, "TEXTURE_INDEX", uuid,
									"Cara '" + faceName + "' del cuboid '" + uuid + "' referencia el índice de textura "
											+ index + ", pero 'textures' solo tiene " + textureCount + " entrada(s)."));
				}
			}
		});
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
		JsonNode group = groupsByUuid.get(uuid);
		String name = group != null ? group.path("name").asText() : "";
		boolean isSpecialNoGeometryBone = name.equalsIgnoreCase("hitbox") || name.equalsIgnoreCase("tag_name");
		boolean hasCuboidChildren = false;
		for (JsonNode child : node.path("children")) {
			if (child.isTextual()) {
				hasCuboidChildren = true;
			}
		}
		if (isSpecialNoGeometryBone && hasCuboidChildren) {
			issues.add(
					new ValidationIssue(
							Severity.WARNING, "SPECIAL_BONE_GEOMETRY_DROPPED", uuid,
							"El bone '" + name + "' (" + uuid + ") tiene cuboids hijos, pero FreeMinecraftModels nunca"
									+ " genera geometría visible para bones llamados exactamente 'hitbox' o 'tag_name'"
									+ " -- esos cuboids no se mostrarán en el modelo importado."));
		}
		for (JsonNode child : node.path("children")) {
			checkSpecialBoneNamesRecursive(child, groupsByUuid, issues);
		}
	}

}
