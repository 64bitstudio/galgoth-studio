package com.galgothstudio.backend.domain.export.blockbenchreal;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.galgothstudio.backend.domain.export.BBModelExporterV4;
import com.galgothstudio.backend.domain.export.BBModelExporterV5;
import com.galgothstudio.backend.domain.jackson.Vec3JacksonModule;
import com.galgothstudio.backend.domain.jackson.Vec4JacksonModule;
import com.galgothstudio.backend.domain.model.Bone;
import com.galgothstudio.backend.domain.model.MobProjectModel;
import com.galgothstudio.backend.domain.model.Vec3;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import java.io.File;
import java.io.IOException;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Tickets 012 (AC #2) / 014 (AC #3) -- cierra el bloqueo real documentado
 * en el `## Hecho` de ambos: 5 casos mínimos (cuboid simple, jerarquía
 * parent/child, pivot+rotación no triviales, múltiples cuboides, UV con
 * textura real), cada uno en v5 Y v4, provistos por el Product Owner
 * desde una instalación REAL de Blockbench (abiertos/guardados para
 * producir la variante v5; "Export Legacy Project" para la v4) -- NO
 * generados programáticamente (un primer paquete entregado sí lo era,
 * su propio README lo admitía explícitamente; se pidió repetir el paso
 * real antes de aceptarlo como fixture de conformidad).
 *
 * <p>Confirmado antes de escribir este test que las 10 fixtures tienen
 * campos idiosincrásicos reales de Blockbench que nuestro propio
 * exportador nunca produce (`unhandled_root_fields`, `multi_file_ruleset`,
 * `allow_mirror_modeling`, `mirror_uv`, `selected`, metadata de textura
 * completa) -- evidencia de que vienen de la app real, no de una
 * reconstrucción a partir de la misma spec que ya usamos para programar
 * el exportador (lo cual solo probaría que somos consistentes con
 * nosotros mismos, no que entendimos bien el formato real).
 */
class BlockbenchRealFixturePackConformanceTest {

	private static final File FIXTURES_DIR = new File("src/test/resources/fixtures/blockbench-real");
	private static final File SCHEMA_FILE = new File("../contracts/schemas/mob-project-model.schema.json");
	private static final String[] CASES = {
		"01_cuboid_simple", "02_parent_child_hierarchy", "03_pivot_rotation", "04_multiple_cuboids", "05_uv_real_texture"
	};

	private static Stream<Arguments> todosLosArchivosV5YV4() {
		return Stream.of(CASES).flatMap(
				caseName -> Stream.of(
						Arguments.of(caseName + " (v5)", fixtureFile("v5", caseName, "v5")),
						Arguments.of(caseName + " (v4)", fixtureFile("v4", caseName, "v4"))));
	}

	private static File fixtureFile(String folder, String caseName, String suffix) {
		return new File(FIXTURES_DIR, folder + "/" + caseName + "_" + suffix + ".bbmodel");
	}

	private static JsonNode parseJson(File file) throws IOException {
		return new ObjectMapper().readTree(file);
	}

	private static MobProjectModel parseModel(File file) throws IOException {
		return BlockbenchBbmodelTestParser.parse(parseJson(file), "conformance-fixture", "conformance-project").model();
	}

	@ParameterizedTest(name = "{0} se parsea sin pérdida, valida contra el schema formal, y se reexporta sin errores en su propio formato")
	@MethodSource("todosLosArchivosV5YV4")
	void unFixtureRealSeParseaValidaYReexportaSinErrores(String label, File file) throws IOException {
		JsonNode root = parseJson(file);
		BlockbenchBbmodelTestParser.ParseResult result =
				BlockbenchBbmodelTestParser.parse(root, "conformance-fixture", "conformance-project");
		MobProjectModel model = result.model();

		assertThat(result.orphanedElementUuids()).as("ningún cuboid del fixture real queda sin bone -- %s", label).isEmpty();

		ObjectMapper domainMapper =
				new ObjectMapper().registerModule(new Vec3JacksonModule()).registerModule(new Vec4JacksonModule());
		JsonSchema schema =
				JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(parseJson(SCHEMA_FILE));
		Set<ValidationMessage> errors = schema.validate(domainMapper.valueToTree(model));
		assertThat(errors).as("modelo parseado de %s debe ser válido contra el schema formal", label).isEmpty();

		boolean isV5 = "5.0".equals(root.path("meta").path("format_version").asText());
		String reexported = isV5 ? BBModelExporterV5.export(model) : BBModelExporterV4.export(model);
		JsonNode reexportedJson = parseJson(reexported);
		assertThat(reexportedJson.path("meta").path("format_version").asText()).isEqualTo(isV5 ? "5.0" : "4.10");
		assertThat(reexportedJson.has("groups")).as("v5 separa groups[], v4 los embebe en outliner -- %s", label).isEqualTo(isV5);
		assertThat(reexportedJson.path("elements")).hasSameSizeAs(model.cuboids());
	}

	private static JsonNode parseJson(String rawJson) throws IOException {
		return new ObjectMapper().readTree(rawJson);
	}

	@Test
	void caso01CuboidSimpleTieneExactamenteUnCuboidSinJerarquiaRealEnAmbosFormatos() throws IOException {
		// El propio fixture real cuelga el cuboid directo de la raíz del
		// outliner, sin ningún group/bone -- Blockbench lo permite, nuestro
		// dominio no (boneId no-nulo obligatorio). El único bone presente
		// acá es el sintético que el parser agrega para ese caso real (ver
		// docstring de BlockbenchBbmodelTestParser); no hay jerarquía real.
		for (String folder : new String[] {"v5", "v4"}) {
			MobProjectModel model = parseModel(fixtureFile(folder, "01_cuboid_simple", folder));
			assertThat(model.cuboids()).hasSize(1);
			assertThat(model.bones()).hasSize(1);
		}
	}

	@Test
	void caso02JerarquiaTieneDosBonesAnidadosConUnCuboidEnCadaNivelEnAmbosFormatos() throws IOException {
		for (String folder : new String[] {"v5", "v4"}) {
			MobProjectModel model = parseModel(fixtureFile(folder, "02_parent_child_hierarchy", folder));
			assertThat(model.bones()).hasSize(2);
			Bone parent = model.bones().stream().filter(b -> b.parentId() == null).findFirst().orElseThrow();
			Bone child = model.bones().stream().filter(b -> !b.id().equals(parent.id())).findFirst().orElseThrow();
			assertThat(child.parentId()).isEqualTo(parent.id());
			assertThat(model.cuboids()).hasSize(2);
		}
	}

	@Test
	void caso03PivotYRotacionNoTrivialesSePreservanEnAmbosFormatos() throws IOException {
		for (String folder : new String[] {"v5", "v4"}) {
			MobProjectModel model = parseModel(fixtureFile(folder, "03_pivot_rotation", folder));
			assertThat(model.cuboids()).hasSize(1);
			var cuboid = model.cuboids().get(0);
			assertThat(cuboid.origin()).isNotEqualTo(new Vec3(0, 0, 0));
			assertThat(cuboid.rotation()).isNotEqualTo(new Vec3(0, 0, 0));
		}
	}

	@Test
	void caso04MultiplesCuboidesTieneAlMenosDosCuboidesEnAmbosFormatos() throws IOException {
		for (String folder : new String[] {"v5", "v4"}) {
			MobProjectModel model = parseModel(fixtureFile(folder, "04_multiple_cuboids", folder));
			assertThat(model.cuboids()).hasSizeGreaterThanOrEqualTo(2);
		}
	}

	@Test
	void caso05UvTexturaRealTieneUnaTexturaRealAplicadaViaUvEnAmbosFormatos() throws IOException {
		for (String folder : new String[] {"v5", "v4"}) {
			JsonNode root = parseJson(fixtureFile(folder, "05_uv_real_texture", folder));
			assertThat(root.path("textures")).hasSizeGreaterThanOrEqualTo(1);
			String source = root.path("textures").get(0).path("source").asText();
			assertThat(source).startsWith("data:image/png;base64,");
			MobProjectModel model = parseModel(fixtureFile(folder, "05_uv_real_texture", folder));
			boolean anyFaceReferencesTexture =
					model.cuboids().stream().anyMatch(c -> c.faces().north().texture() != null);
			assertThat(anyFaceReferencesTexture).as("al menos una cara debe referenciar el índice de la textura real").isTrue();
		}
	}

}
