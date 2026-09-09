package com.galgothstudio.backend.domain.export.bbmodel;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import java.io.IOException;

/**
 * Serializa {@link BBV4OutlinerLeaf} como un string uuid suelto, y
 * {@link BBV4OutlinerGroup} como un objeto inline completo (`name`,
 * `origin`, `rotation` opcional, `color` fijo en 0 -- este ciclo no tiene
 * color de outliner funcional --, `uuid`, `export`, `isOpen`, `children`).
 */
final class BBV4OutlinerEntrySerializer extends JsonSerializer<BBV4OutlinerEntry> {

	@Override
	public void serialize(BBV4OutlinerEntry value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
		switch (value) {
			case BBV4OutlinerLeaf(String uuid) -> gen.writeString(uuid);
			case BBV4OutlinerGroup(String name, String uuid, var origin, var rotation, boolean export, boolean isOpen, var children) -> {
				gen.writeStartObject();
				gen.writeStringField("name", name);
				serializers.defaultSerializeField("origin", origin, gen);
				if (rotation != null) {
					serializers.defaultSerializeField("rotation", rotation, gen);
				}
				gen.writeNumberField("color", 0);
				gen.writeStringField("uuid", uuid);
				gen.writeBooleanField("export", export);
				gen.writeBooleanField("isOpen", isOpen);
				gen.writeFieldName("children");
				serializers.defaultSerializeValue(children, gen);
				gen.writeEndObject();
			}
		}
	}

}
