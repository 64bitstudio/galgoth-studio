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
 * `contracts/schemas/model-intent.schema.json` (ticket 028, AC #1) --
 * ANTES de intentar deserializarlo como {@link com.galgothstudio.backend.domain.model.ModelIntent}
 * o de planear ninguna geometría. Mismo patrón que
 * {@link MobProjectModelValidator} (ticket 020): valida sobre el árbol
 * JSON crudo, no sobre un objeto ya deserializado -- así un JSON
 * inválido (campo faltante, tipo incorrecto) se reporta con mensajes
 * legibles en vez de fallar con una excepción de deserialización opaca.
 */
@Component
public class ModelIntentValidator {

	private static final String SCHEMA_CLASSPATH_RESOURCE = "/schemas/model-intent.schema.json";

	private final ObjectMapper objectMapper;
	private final JsonSchema schema;

	public ModelIntentValidator(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
		this.schema = loadSchema(objectMapper);
	}

	private static JsonSchema loadSchema(ObjectMapper objectMapper) {
		try (InputStream in = ModelIntentValidator.class.getResourceAsStream(SCHEMA_CLASSPATH_RESOURCE)) {
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

	/** @return mensajes de error legibles, vacío si `rawJson` valida contra el schema de ModelIntent. */
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
