package com.galgothstudio.backend.modelvalidation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Valida el JSON crudo devuelto por un `VisionModelProvider` contra
 * `contracts/schemas/texture-plan.schema.json` (ticket 052, AC #2) --
 * ANTES de deserializarlo como {@link com.galgothstudio.backend.domain.model.TexturePlan}
 * o de que cualquier paso de generación de textura (054) lo consuma.
 * Mismo patrón exacto que {@link ModelIntentValidator} (028): valida
 * sobre el árbol JSON crudo, no sobre un objeto ya deserializado.
 */
@Component
public class TexturePlanValidator {

	private static final String SCHEMA_CLASSPATH_RESOURCE = "/schemas/texture-plan.schema.json";

	private final ObjectMapper objectMapper;
	private final JsonSchema schema;

	public TexturePlanValidator(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
		this.schema = loadSchema(objectMapper);
	}

	private static JsonSchema loadSchema(ObjectMapper objectMapper) {
		try (InputStream in = TexturePlanValidator.class.getResourceAsStream(SCHEMA_CLASSPATH_RESOURCE)) {
			if (in == null) {
				throw new IllegalStateException(
						"No se encontró el JSON Schema empaquetado en '" + SCHEMA_CLASSPATH_RESOURCE
								+ "' -- ¿falta el processResources de contracts/schemas en build.gradle?");
			}
			JsonNode schemaNode = objectMapper.readTree(in);
			return JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(schemaNode);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/** @return mensajes de error legibles, vacío si `rawJson` valida contra el schema de TexturePlan. */
	public List<String> validate(String rawJson) {
		JsonNode node;
		try {
			node = objectMapper.readTree(rawJson);
		} catch (IOException e) {
			return List.of("La respuesta del proveedor no es JSON válido: " + e.getMessage());
		}
		Set<ValidationMessage> messages = schema.validate(node);
		return messages.stream().map(ValidationMessage::getMessage).toList();
	}

}
