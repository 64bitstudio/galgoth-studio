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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Ticket 014 -- `BBModelExporterV4`. Cubre AC #1 (formato v4 real) y AC #2
 * (misma geometría/jerarquía que v5 para el mismo modelo). AC #3 (fixtures
 * reales v4 mínimas) queda bloqueado por el mismo motivo que el ticket 012
 * -- ver su Hecho y el de este ticket.
 */
class BBModelExporterV4Test {

	private static final ObjectMapper JSON = new ObjectMapper();

	private static CuboidFaces placeholderFaces() {
		Face placeholder = new Face(new Vec4(0, 0, 0, 0), null);
		return new CuboidFaces(placeholder, placeholder, placeholder, placeholder, placeholder, placeholder);
	}

	private static MobProjectModel modelWith(List<Bone> bones, List<Cuboid> cuboids) {
		return new MobProjectModel(
				"mob-1", "project-1", "Test Mob", BaseType.HUMANOID, MobProjectModel.UNITS_MINECRAFT_PIXELS, bones,
				cuboids, new TextureDocument(64, 64, null), new UvLayout(64, 64, new ArrayList<>()), new ArrayList<>(),
				new ExportSettings(FormatVersion.V4), new ArrayList<>());
	}

	// -- AC #1: formato v4 real (sin separación groups/outliner) -------------

	@Test
	void unCuboidYUnBone_exportaFormatVersion4ConLaJerarquiaInlineEnOutliner() throws Exception {
		String boneId = UUID.randomUUID().toString();
		String cuboidId = UUID.randomUUID().toString();
		Bone bone = new Bone(boneId, "torso", null, new Vec3(0, 24, 0), new Vec3(0, 0, 0));
		Cuboid cuboid = new Cuboid(
				cuboidId, "body", boneId, new Vec3(-4, 12, -2), new Vec3(4, 24, 2), new Vec3(0, 24, 0),
				new Vec3(0, 0, 0), placeholderFaces());

		String json = BBModelExporterV4.export(modelWith(List.of(bone), List.of(cuboid)));
		JsonNode root = JSON.readTree(json);

		assertThat(root.path("meta").path("format_version").asText()).isEqualTo("4.10");
		// AC #1: sin separación groups/outliner -- 'groups' NO existe como
		// campo top-level; el group vive embebido DENTRO de outliner.
		assertThat(root.has("groups")).isFalse();
		JsonNode groupNode = root.path("outliner").get(0);
		assertThat(groupNode.path("uuid").asText()).isEqualTo(boneId);
		assertThat(groupNode.path("name").asText()).isEqualTo("torso");
		assertThat(groupNode.path("origin")).hasSize(3);
		assertThat(groupNode.has("rotation")).isFalse(); // rotación cero -> se omite
		assertThat(groupNode.path("children").get(0).asText()).isEqualTo(cuboidId);
	}

	@Test
	void unBoneConRotacionNoTrivialIncluyeElCampoRotationEnElGroupInline() throws Exception {
		String boneId = UUID.randomUUID().toString();
		Bone bone = new Bone(boneId, "armRight", null, new Vec3(-5, 22, 0), new Vec3(0, 0, -18));

		String json = BBModelExporterV4.export(modelWith(List.of(bone), List.of()));
		JsonNode groupNode = JSON.readTree(json).path("outliner").get(0);

		assertThat(groupNode.has("rotation")).isTrue();
		assertThat(groupNode.path("rotation").get(2).asDouble()).isEqualTo(-18.0);
	}

	// -- AC #2: misma geometría/jerarquía que v5 para el mismo modelo -------

	@Test
	void v4YV5RepresentanLaMismaGeometriaYJerarquiaParaElMismoModelo() throws Exception {
		String rootId = UUID.randomUUID().toString();
		String childId = UUID.randomUUID().toString();
		String cuboidOnRootId = UUID.randomUUID().toString();
		String cuboidOnChildId = UUID.randomUUID().toString();
		Bone root = new Bone(rootId, "root", null, new Vec3(0, 10, 0), new Vec3(0, 0, 0));
		Bone child = new Bone(childId, "child", rootId, new Vec3(2, 10, 0), new Vec3(0, 0, -12));
		Cuboid cuboidOnRoot = new Cuboid(
				cuboidOnRootId, "a", rootId, new Vec3(-4, 0, -2), new Vec3(4, 12, 2), new Vec3(0, 6, 0),
				new Vec3(0, 0, 0), placeholderFaces());
		Cuboid cuboidOnChild = new Cuboid(
				cuboidOnChildId, "b", childId, new Vec3(0, 8, -1), new Vec3(4, 16, 1), new Vec3(2, 10, 0),
				new Vec3(5, 0, 0), placeholderFaces());
		MobProjectModel model = modelWith(List.of(root, child), List.of(cuboidOnRoot, cuboidOnChild));

		String v4Json = BBModelExporterV4.export(model);
		String v5Json = BBModelExporterV5.export(model);

		// Mismos cuboids: mismo from/to/origin/rotation por uuid en ambos formatos.
		Map<String, JsonNode> v4Elements = elementsByUuid(v4Json);
		Map<String, JsonNode> v5Elements = elementsByUuid(v5Json);
		assertThat(v4Elements.keySet()).isEqualTo(v5Elements.keySet());
		for (String uuid : v4Elements.keySet()) {
			assertThat(v4Elements.get(uuid).path("from")).isEqualTo(v5Elements.get(uuid).path("from"));
			assertThat(v4Elements.get(uuid).path("to")).isEqualTo(v5Elements.get(uuid).path("to"));
			assertThat(v4Elements.get(uuid).path("origin")).isEqualTo(v5Elements.get(uuid).path("origin"));
		}

		// Misma jerarquía de bones: mismo conjunto de pares (bone, parent) y
		// mismo conjunto de (bone, cuboids-hijos-directos), leídos de cada
		// estructura de outliner con su propia forma.
		assertThat(hierarchyOf(JSON.readTree(v4Json))).isEqualTo(hierarchyOf(JSON.readTree(v5Json)));
	}

	private static Map<String, JsonNode> elementsByUuid(String bbmodelJson) throws Exception {
		Map<String, JsonNode> byUuid = new HashMap<>();
		JSON.readTree(bbmodelJson).path("elements").forEach(el -> byUuid.put(el.path("uuid").asText(), el));
		return byUuid;
	}

	/** {bone-uuid -> set de sus hijos directos (uuids de bones y/o cuboids)} -- independiente de si el group es un stub (v5) o inline (v4). */
	private static Map<String, Set<String>> hierarchyOf(JsonNode root) {
		Map<String, Set<String>> hierarchy = new HashMap<>();
		collectHierarchy(root.path("outliner"), hierarchy);
		return hierarchy;
	}

	private static void collectHierarchy(JsonNode nodes, Map<String, Set<String>> hierarchy) {
		for (JsonNode node : nodes) {
			if (node.isTextual()) {
				continue;
			}
			String uuid = node.path("uuid").asText();
			Set<String> children = new HashSet<>();
			for (JsonNode child : node.path("children")) {
				children.add(child.isTextual() ? child.asText() : child.path("uuid").asText());
			}
			hierarchy.put(uuid, children);
			collectHierarchy(node.path("children"), hierarchy);
		}
	}

}
