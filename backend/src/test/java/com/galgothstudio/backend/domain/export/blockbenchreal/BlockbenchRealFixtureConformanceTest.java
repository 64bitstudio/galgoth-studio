package com.galgothstudio.backend.domain.export.blockbenchreal;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.galgothstudio.backend.domain.export.BBModelExporterV5;
import com.galgothstudio.backend.domain.jackson.Vec3JacksonModule;
import com.galgothstudio.backend.domain.jackson.Vec4JacksonModule;
import com.galgothstudio.backend.domain.model.MobProjectModel;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Ticket 012, AC #3: fixture de conformidad adicional usando el `.bbmodel`
 * REAL del proyecto (`carcomido_minecraft_cuboids.bbmodel`, provisto en
 * `galgoth_studio_build_pack/samples/`, `format_version: "4.10"` -- no
 * generado por nuestro exportador). Prueba, de forma independiente del
 * script Python del ticket 008, que el dominio interno Java puede
 * representar un `.bbmodel` real sin pérdida relevante: parsea con
 * {@link BlockbenchBbmodelTestParser} (TEST-ONLY, ver su docstring),
 * valida el resultado contra el JSON Schema formal, y confirma que
 * nuestro propio exportador puede re-exportar esa geometría sin errores.
 */
class BlockbenchRealFixtureConformanceTest {

	private static final File REAL_BBMODEL_FILE =
			new File("../galgoth_studio_build_pack/samples/carcomido_minecraft_cuboids.bbmodel");
	private static final File SCHEMA_FILE = new File("../contracts/schemas/mob-project-model.schema.json");

	@Test
	void elBbmodelRealDeCarcomidoSeParseaSinPerdidaYValidaContraElSchemaFormal() throws IOException {
		ObjectMapper mapper = new ObjectMapper();
		JsonNode root = mapper.readTree(REAL_BBMODEL_FILE);

		BlockbenchBbmodelTestParser.ParseResult result =
				BlockbenchBbmodelTestParser.parse(root, "conformance-carcomido", "conformance-project");

		// El sample real trae 25 elementos; 1 es huérfano (no referenciado
		// por ningún group del outliner, ver Hecho del ticket 008 -- mismo
		// hallazgo, confirmado independientemente por este parser Java).
		assertThat(result.orphanedElementUuids()).hasSize(1);
		MobProjectModel model = result.model();
		assertThat(model.bones()).hasSize(6);
		assertThat(model.cuboids()).hasSize(24);

		ObjectMapper domainMapper =
				new ObjectMapper().registerModule(new Vec3JacksonModule()).registerModule(new Vec4JacksonModule());
		JsonSchema schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)
				.getSchema(mapper.readTree(SCHEMA_FILE));
		Set<ValidationMessage> errors = schema.validate(domainMapper.valueToTree(model));
		assertThat(errors).isEmpty();
	}

	@Test
	void laGeometriaParseadaDelBbmodelRealSeReexportaSinErrores() throws IOException {
		ObjectMapper mapper = new ObjectMapper();
		JsonNode root = mapper.readTree(REAL_BBMODEL_FILE);
		MobProjectModel model =
				BlockbenchBbmodelTestParser.parse(root, "conformance-carcomido", "conformance-project").model();

		String exported = BBModelExporterV5.export(model);
		JsonNode reexported = mapper.readTree(exported);

		assertThat(reexported.path("groups")).hasSize(6);
		assertThat(reexported.path("elements")).hasSize(24);
		assertThat(reexported.path("meta").path("format_version").asText()).isEqualTo("5.0");
	}

	@Test
	void bonesConNombresEspecialesDelSampleRealSePreservanTalCual() throws IOException {
		// El sample real no usa hitbox/tag_name/mount_* (convenciones de
		// FreeMinecraftModels, ver TECHNICAL_REFERENCES.md) -- se deja
		// constancia explícita de que este fixture NO cubre ese caso
		// (ticket 013 lo valida cuando exista un fixture que sí los use).
		ObjectMapper mapper = new ObjectMapper();
		JsonNode root = mapper.readTree(REAL_BBMODEL_FILE);
		MobProjectModel model =
				BlockbenchBbmodelTestParser.parse(root, "conformance-carcomido", "conformance-project").model();

		List<String> boneNames = model.bones().stream().map(b -> b.name()).toList();
		assertThat(boneNames).doesNotContain("hitbox").noneMatch(name -> name.startsWith("mount_"));
	}

}
