package com.galgothstudio.backend.modelvalidation;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.nio.file.Files;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Fixture del benchmark Carcomido (ticket 105) -- se valida contra el
 * MISMO `model-intent.schema.json` que cualquier respuesta real del
 * proveedor de vision: si el fixture no fuera un `ModelIntent` legítimo,
 * el benchmark estaría midiendo algo que la aplicación real nunca podría
 * recibir.
 */
class CarcomidoModelIntentFixtureTest {

	@Test
	void elFixtureDelBenchmarkCarcomidoEsValidoContraElSchemaReal() throws Exception {
		String rawJson = Files.readString(new File("../contracts/fixtures/carcomido-model-intent.json").toPath());

		List<String> errors = new ModelIntentValidator(new ObjectMapper()).validate(rawJson);

		assertThat(errors).isEmpty();
	}

}
