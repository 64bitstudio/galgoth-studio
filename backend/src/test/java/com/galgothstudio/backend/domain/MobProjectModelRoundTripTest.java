package com.galgothstudio.backend.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.galgothstudio.backend.domain.jackson.Vec3JacksonModule;
import com.galgothstudio.backend.domain.jackson.Vec4JacksonModule;
import com.galgothstudio.backend.domain.model.MobProjectModel;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

/**
 * AC #2 del ticket 004, lado Java: parsear contracts/fixtures/model-spec-example.json
 * como {@link MobProjectModel}, volver a serializarlo, y comparar contra
 * el fixture original -- debe ser estructuralmente idéntico (mismo
 * conjunto de campos y valores; JSONAssert compara numéricamente, "0" y
 * "0.0" son el mismo valor). Espejo del test de round-trip TS en
 * frontend/src/domain/__tests__/schema.spec.ts.
 */
class MobProjectModelRoundTripTest {

	private static final File FIXTURE_FILE = new File("../contracts/fixtures/model-spec-example.json");

	@Test
	void parsearYVolverASerializarProduceJsonEstructuralmenteIdentico() throws IOException, org.json.JSONException {
		ObjectMapper mapper =
				new ObjectMapper().registerModule(new Vec3JacksonModule()).registerModule(new Vec4JacksonModule());
		String original = Files.readString(FIXTURE_FILE.toPath());

		MobProjectModel model = mapper.readValue(original, MobProjectModel.class);
		String roundTripped = mapper.writeValueAsString(model);

		JSONAssert.assertEquals(original, roundTripped, JSONCompareMode.STRICT);
	}

}
