package com.galgothstudio.backend.domain.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.galgothstudio.backend.domain.model.Vec3;
import java.io.IOException;

/**
 * Serializa/deserializa {@link Vec3} como array JSON [x,y,z] -- Jackson por
 * defecto serializaría un record de 3 doubles como objeto {"x":...}, lo
 * que no calza con contracts/schemas/mob-project-model.schema.json ni con
 * el tipo TS equivalente (Vec3 = [number, number, number]).
 */
public class Vec3JacksonModule extends SimpleModule {

	public Vec3JacksonModule() {
		addSerializer(Vec3.class, new Serializer());
		addDeserializer(Vec3.class, new Deserializer());
	}

	static class Serializer extends StdSerializer<Vec3> {
		Serializer() {
			super(Vec3.class);
		}

		@Override
		public void serialize(Vec3 value, JsonGenerator gen, SerializerProvider provider) throws IOException {
			gen.writeStartArray();
			gen.writeNumber(value.x());
			gen.writeNumber(value.y());
			gen.writeNumber(value.z());
			gen.writeEndArray();
		}
	}

	static class Deserializer extends StdDeserializer<Vec3> {
		Deserializer() {
			super(Vec3.class);
		}

		@Override
		public Vec3 deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
			double[] values = p.readValueAs(double[].class);
			return Vec3.fromArray(values);
		}
	}

}
