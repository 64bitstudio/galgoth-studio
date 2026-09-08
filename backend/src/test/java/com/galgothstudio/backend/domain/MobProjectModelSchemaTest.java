package com.galgothstudio.backend.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import java.io.File;
import java.io.IOException;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Espejo de frontend/src/domain/__tests__/schema.spec.ts -- valida
 * contracts/fixtures/model-spec-example.json contra
 * contracts/schemas/mob-project-model.schema.json desde el lado Java
 * (AC #1 del ticket 004).
 */
class MobProjectModelSchemaTest {

	private static final File SCHEMA_FILE = new File("../contracts/schemas/mob-project-model.schema.json");
	private static final File FIXTURE_FILE = new File("../contracts/fixtures/model-spec-example.json");

	private static JsonSchema loadSchema() throws IOException {
		ObjectMapper mapper = new ObjectMapper();
		JsonNode schemaNode = mapper.readTree(SCHEMA_FILE);
		return JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(schemaNode);
	}

	@Test
	void validaElFixtureSinErrores() throws IOException {
		JsonSchema schema = loadSchema();
		JsonNode fixture = new ObjectMapper().readTree(FIXTURE_FILE);

		Set<com.networknt.schema.ValidationMessage> errors = schema.validate(fixture);

		assertThat(errors).isEmpty();
	}

	@Test
	void rechazaUnDocumentoAlQueLeFaltaUnCampoRequerido() throws IOException {
		JsonSchema schema = loadSchema();
		ObjectMapper mapper = new ObjectMapper();
		com.fasterxml.jackson.databind.node.ObjectNode fixture =
				(com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(FIXTURE_FILE);
		fixture.remove("texture");

		Set<com.networknt.schema.ValidationMessage> errors = schema.validate(fixture);

		assertThat(errors).isNotEmpty();
	}

}
