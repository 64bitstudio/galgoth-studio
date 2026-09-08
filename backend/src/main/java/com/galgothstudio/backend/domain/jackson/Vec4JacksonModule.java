package com.galgothstudio.backend.domain.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.galgothstudio.backend.domain.model.Vec4;
import java.io.IOException;

/** Serializa/deserializa {@link Vec4} como array JSON [a,b,c,d] -- mismo patrón que {@link Vec3JacksonModule}. */
public class Vec4JacksonModule extends SimpleModule {

	public Vec4JacksonModule() {
		addSerializer(Vec4.class, new Serializer());
		addDeserializer(Vec4.class, new Deserializer());
	}

	static class Serializer extends StdSerializer<Vec4> {
		Serializer() {
			super(Vec4.class);
		}

		@Override
		public void serialize(Vec4 value, JsonGenerator gen, SerializerProvider provider) throws IOException {
			gen.writeStartArray();
			gen.writeNumber(value.a());
			gen.writeNumber(value.b());
			gen.writeNumber(value.c());
			gen.writeNumber(value.d());
			gen.writeEndArray();
		}
	}

	static class Deserializer extends StdDeserializer<Vec4> {
		Deserializer() {
			super(Vec4.class);
		}

		@Override
		public Vec4 deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
			double[] values = p.readValueAs(double[].class);
			return Vec4.fromArray(values);
		}
	}

}
