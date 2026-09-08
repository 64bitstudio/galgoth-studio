package com.galgothstudio.backend.domain.export.bbmodel;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import java.io.IOException;

/**
 * Serializa {@link BBOutlinerLeaf} como un string uuid suelto, y
 * {@link BBOutlinerGroupRef} como {@code {uuid, isOpen, children}} -- el
 * array {@code outliner} de Blockbench mezcla ambas formas en el mismo
 * nivel (ver docstring de {@link BBOutlinerEntry}).
 */
final class BBOutlinerEntrySerializer extends JsonSerializer<BBOutlinerEntry> {

	@Override
	public void serialize(BBOutlinerEntry value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
		switch (value) {
			case BBOutlinerLeaf leaf -> gen.writeString(leaf.uuid());
			case BBOutlinerGroupRef group -> {
				gen.writeStartObject();
				gen.writeStringField("uuid", group.uuid());
				gen.writeBooleanField("isOpen", group.isOpen());
				gen.writeFieldName("children");
				serializers.defaultSerializeValue(group.children(), gen);
				gen.writeEndObject();
			}
		}
	}

}
