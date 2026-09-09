package com.galgothstudio.backend.modelvalidation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.galgothstudio.backend.domain.model.MobProjectModel;
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
 * Valida un {@link MobProjectModel} contra `contracts/schemas/mob-project-model.schema.json`
 * -- backend es la autoridad canónica de invariantes (Diseño técnico §5 de
 * `docs/definiciones/galgoth-studio-mvp.md`). Usado por Guardar/Apply/export
 * (ticket 020 es el primero en invocarlo desde código de producción, no
 * solo tests -- ver `MobProjectModelSchemaTest`, ticket 004, que valida
 * el mismo schema desde el lado test con una ruta relativa que no sirve
 * en un JAR desplegado).
 *
 * El schema se empaqueta en el classpath vía el `processResources` de
 * `build.gradle` (copia real de `contracts/schemas/`, nunca una copia a
 * mano que pueda desincronizarse).
 */
@Component
public class MobProjectModelValidator {

	private static final String SCHEMA_CLASSPATH_RESOURCE = "/schemas/mob-project-model.schema.json";

	private final ObjectMapper objectMapper;
	private final JsonSchema schema;

	public MobProjectModelValidator(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
		this.schema = loadSchema(objectMapper);
	}

	private static JsonSchema loadSchema(ObjectMapper objectMapper) {
		try (InputStream in = MobProjectModelValidator.class.getResourceAsStream(SCHEMA_CLASSPATH_RESOURCE)) {
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

	/** @return mensajes de error legibles, vacío si el modelo es válido. */
	public List<String> validate(MobProjectModel model) {
		JsonNode modelNode = objectMapper.valueToTree(model);
		Set<ValidationMessage> messages = schema.validate(modelNode);
		return messages.stream().map(ValidationMessage::getMessage).toList();
	}

}
