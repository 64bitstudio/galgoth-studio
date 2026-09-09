package com.galgothstudio.backend.modelvalidation;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.galgothstudio.backend.domain.jackson.Vec3JacksonModule;
import com.galgothstudio.backend.domain.jackson.Vec4JacksonModule;
import com.galgothstudio.backend.domain.model.MobProjectModel;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Unidad directa de {@link MobProjectModelValidator} -- ver también
 * `MobDraftControllerTest` (ticket 020) para la verificación de que un
 * modelo inválido efectivamente rechaza un `POST /revisions` con 400.
 */
class MobProjectModelValidatorTest {

	private static final File FIXTURE_FILE = new File("../contracts/fixtures/model-spec-example.json");

	private ObjectMapper objectMapper() {
		return new ObjectMapper().registerModule(new Vec3JacksonModule()).registerModule(new Vec4JacksonModule());
	}

	@Test
	void unModeloValidoNoProduceErrores() throws IOException {
		ObjectMapper mapper = objectMapper();
		MobProjectModel model = mapper.readValue(FIXTURE_FILE, MobProjectModel.class);

		List<String> errors = new MobProjectModelValidator(mapper).validate(model);

		assertThat(errors).isEmpty();
	}

	@Test
	void unModeloConTextureNuloProduceErroresDeSchema() throws IOException {
		ObjectMapper mapper = objectMapper();
		ObjectNode node = (ObjectNode) mapper.readTree(Files.readString(FIXTURE_FILE.toPath()));
		node.putNull("texture");
		MobProjectModel invalidModel = mapper.treeToValue(node, MobProjectModel.class);

		List<String> errors = new MobProjectModelValidator(mapper).validate(invalidModel);

		assertThat(errors).isNotEmpty();
	}

}
