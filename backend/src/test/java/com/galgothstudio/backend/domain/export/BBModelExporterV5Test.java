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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Ticket 010 -- `BBModelExporterV5`. Cubre las 4 AC del ticket: formato v5
 * con `groups`/`outliner` separados, fidelidad de jerarquía padre-hijo,
 * unicidad/validez de UUIDs, y correspondencia numérica exacta con el
 * `CoordinateSystemContract` de 004 (copia directa, sin transformación --
 * ver docstring de {@link BBModelExporterV5}).
 */
class BBModelExporterV5Test {

	private static final ObjectMapper JSON = new ObjectMapper();

	private static CuboidFaces placeholderFaces() {
		Face placeholder = new Face(new Vec4(0, 0, 0, 0), null);
		return new CuboidFaces(placeholder, placeholder, placeholder, placeholder, placeholder, placeholder);
	}

	private static MobProjectModel modelWith(List<Bone> bones, List<Cuboid> cuboids) {
		return new MobProjectModel(
				"mob-1", "project-1", "Test Mob", BaseType.HUMANOID, MobProjectModel.UNITS_MINECRAFT_PIXELS, bones,
				cuboids, new TextureDocument(64, 64, null), new UvLayout(64, 64, new ArrayList<>()), new ArrayList<>(),
				new ExportSettings(FormatVersion.V5), new ArrayList<>());
	}

	// -- AC #1: formato v5, groups/outliner separados -----------------------

	@Test
	void unCuboidYUnBone_exportaFormatVersion5ConGroupsYOutlinerSeparados() throws Exception {
		String boneId = UUID.randomUUID().toString();
		String cuboidId = UUID.randomUUID().toString();
		Bone bone = new Bone(boneId, "torso", null, new Vec3(0, 24, 0), new Vec3(0, 0, 0));
		Cuboid cuboid = new Cuboid(
				cuboidId, "body", boneId, new Vec3(-4, 12, -2), new Vec3(4, 24, 2), new Vec3(0, 24, 0),
				new Vec3(0, 0, 0), placeholderFaces());

		String json = BBModelExporterV5.export(modelWith(List.of(bone), List.of(cuboid)));
		JsonNode root = JSON.readTree(json);

		assertThat(root.path("meta").path("format_version").asText()).isEqualTo("5.0");
		// AC #1: groups y outliner son estructuras SEPARADAS -- groups es un
		// array PLANO (sin 'children'), la jerarquía vive solo en outliner.
		assertThat(root.path("groups")).hasSize(1);
		assertThat(root.path("groups").get(0).has("children")).isFalse();
		assertThat(root.path("groups").get(0).path("uuid").asText()).isEqualTo(boneId);
		assertThat(root.path("outliner")).hasSize(1);
		assertThat(root.path("outliner").get(0).path("uuid").asText()).isEqualTo(boneId);
		assertThat(root.path("outliner").get(0).path("children").get(0).asText()).isEqualTo(cuboidId);
		assertThat(root.path("elements")).hasSize(1);
		assertThat(root.path("elements").get(0).path("uuid").asText()).isEqualTo(cuboidId);
	}

	// -- AC #2: jerarquía padre-hijo fiel en outliner -------------------------

	@Test
	void jerarquiaPadreHijoDeBonesSeReflejaFielmenteEnOutliner() throws Exception {
		String rootId = UUID.randomUUID().toString();
		String childId = UUID.randomUUID().toString();
		String grandchildId = UUID.randomUUID().toString();
		Bone root = new Bone(rootId, "root", null, new Vec3(0, 0, 0), new Vec3(0, 0, 0));
		Bone child = new Bone(childId, "child", rootId, new Vec3(0, 0, 0), new Vec3(0, 0, 0));
		Bone grandchild = new Bone(grandchildId, "grandchild", childId, new Vec3(0, 0, 0), new Vec3(0, 0, 0));

		String json = BBModelExporterV5.export(modelWith(List.of(root, child, grandchild), List.of()));
		JsonNode outliner = JSON.readTree(json).path("outliner");

		assertThat(outliner).hasSize(1); // un solo bone raíz
		JsonNode rootNode = outliner.get(0);
		assertThat(rootNode.path("uuid").asText()).isEqualTo(rootId);
		JsonNode childNode = rootNode.path("children").get(0);
		assertThat(childNode.path("uuid").asText()).isEqualTo(childId);
		JsonNode grandchildNode = childNode.path("children").get(0);
		assertThat(grandchildNode.path("uuid").asText()).isEqualTo(grandchildId);
	}

	// -- AC #3: UUIDs únicos y referencias válidas ---------------------------

	@Test
	void todosLosUuidsSonUnicosYLasReferenciasDeGroupsOutlinerSonValidas() throws Exception {
		String rootId = UUID.randomUUID().toString();
		String childId = UUID.randomUUID().toString();
		String cuboidOnRootId = UUID.randomUUID().toString();
		String cuboidOnChildId = UUID.randomUUID().toString();
		Bone root = new Bone(rootId, "root", null, new Vec3(0, 0, 0), new Vec3(0, 0, 0));
		Bone child = new Bone(childId, "child", rootId, new Vec3(0, 0, 0), new Vec3(0, 0, 0));
		Cuboid cuboidOnRoot = new Cuboid(
				cuboidOnRootId, "a", rootId, new Vec3(0, 0, 0), new Vec3(1, 1, 1), new Vec3(0, 0, 0),
				new Vec3(0, 0, 0), placeholderFaces());
		Cuboid cuboidOnChild = new Cuboid(
				cuboidOnChildId, "b", childId, new Vec3(0, 0, 0), new Vec3(1, 1, 1), new Vec3(0, 0, 0),
				new Vec3(0, 0, 0), placeholderFaces());

		String json =
				BBModelExporterV5.export(modelWith(List.of(root, child), List.of(cuboidOnRoot, cuboidOnChild)));
		JsonNode doc = JSON.readTree(json);

		Set<String> elementUuids = uuidsOf(doc.path("elements"));
		Set<String> groupUuids = uuidsOf(doc.path("groups"));
		assertThat(elementUuids).hasSize(2); // sin duplicados (Set) y con el tamaño esperado
		assertThat(groupUuids).hasSize(2);
		assertThat(elementUuids).doesNotContainAnyElementsOf(groupUuids); // universos disjuntos

		Set<String> referencedInOutliner = new HashSet<>();
		collectOutlinerUuids(doc.path("outliner"), referencedInOutliner);
		Set<String> knownUuids = new HashSet<>();
		knownUuids.addAll(elementUuids);
		knownUuids.addAll(groupUuids);
		assertThat(knownUuids).containsAll(referencedInOutliner); // toda referencia del outliner existe de verdad
	}

	private static Set<String> uuidsOf(JsonNode array) {
		Set<String> uuids = new HashSet<>();
		array.forEach(node -> uuids.add(node.path("uuid").asText()));
		return uuids;
	}

	private static void collectOutlinerUuids(JsonNode node, Set<String> out) {
		if (node.isTextual()) {
			out.add(node.asText());
		} else if (node.isObject()) {
			out.add(node.path("uuid").asText());
			node.path("children").forEach(child -> collectOutlinerUuids(child, out));
		} else if (node.isArray()) {
			node.forEach(child -> collectOutlinerUuids(child, out));
		}
	}

	// -- AC #4: correspondencia numérica exacta con 004 ----------------------

	@Test
	void cuboidConFromToRotationNoTrivialesCorrespondeExactamenteALaConversionDe004() throws Exception {
		String boneId = UUID.randomUUID().toString();
		String cuboidId = UUID.randomUUID().toString();
		Bone bone = new Bone(boneId, "arm", null, new Vec3(0, 0, 0), new Vec3(0, 0, 0));
		Vec3 from = new Vec3(-8.2, 10.2, -2.3);
		Vec3 to = new Vec3(-4.2, 22.2, 1.7);
		Vec3 origin = new Vec3(-4, 22, 0);
		Vec3 rotation = new Vec3(0, 0, -18);
		Cuboid cuboid = new Cuboid(cuboidId, "handRight", boneId, from, to, origin, rotation, placeholderFaces());

		String json = BBModelExporterV5.export(modelWith(List.of(bone), List.of(cuboid)));
		JsonNode element = JSON.readTree(json).path("elements").get(0);

		assertVec3(element.path("from"), from);
		assertVec3(element.path("to"), to);
		assertVec3(element.path("origin"), origin);
		assertVec3(element.path("rotation"), rotation); // [x,y,z] directo -- ver ADR, copia sin transformar
	}

	@Test
	void unCuboidConRotacionCeroOmiteElCampoRotation() throws Exception {
		String boneId = UUID.randomUUID().toString();
		String cuboidId = UUID.randomUUID().toString();
		Bone bone = new Bone(boneId, "torso", null, new Vec3(0, 0, 0), new Vec3(0, 0, 0));
		Cuboid cuboid = new Cuboid(
				cuboidId, "body", boneId, new Vec3(-4, 0, -2), new Vec3(4, 8, 2), new Vec3(0, 0, 0),
				new Vec3(0, 0, 0), placeholderFaces());

		String json = BBModelExporterV5.export(modelWith(List.of(bone), List.of(cuboid)));
		JsonNode element = JSON.readTree(json).path("elements").get(0);

		// Verificado contra Cube.getSaveCopy() real de Blockbench: rotation
		// [0,0,0] se OMITE del JSON, no se escribe como array de ceros.
		assertThat(element.has("rotation")).isFalse();
	}

	private static void assertVec3(JsonNode array, Vec3 expected) {
		assertThat(array.get(0).asDouble()).isEqualTo(expected.x());
		assertThat(array.get(1).asDouble()).isEqualTo(expected.y());
		assertThat(array.get(2).asDouble()).isEqualTo(expected.z());
	}

}
