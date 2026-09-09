package com.galgothstudio.backend.modelvalidation;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.nio.file.Files;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Unidad directa de {@link ModelIntentValidator} contra `contracts/fixtures/model-intent-example.json` (el ejemplo literal del master prompt §9.1, ticket 028). */
class ModelIntentValidatorTest {

	private static final File FIXTURE_FILE = new File("../contracts/fixtures/model-intent-example.json");

	@Test
	void elEjemploLiteralDelMasterPromptEsValido() throws Exception {
		String rawJson = Files.readString(FIXTURE_FILE.toPath());

		List<String> errors = new ModelIntentValidator(new ObjectMapper()).validate(rawJson);

		assertThat(errors).isEmpty();
	}

	@Test
	void unCampoRequeridoFaltanteProduceErroresDeSchema() {
		String rawJson = """
				{"silhouette": "test", "asymmetry": 0.5, "features": ["x"], "materials": ["y"]}
				""";

		List<String> errors = new ModelIntentValidator(new ObjectMapper()).validate(rawJson);

		assertThat(errors).isNotEmpty();
	}

	@Test
	void asymmetryFueraDeRango0a1ProduceErroresDeSchema() {
		String rawJson = """
				{
				  "silhouette": "test",
				  "proportions": {"headScale": 1, "armLength": 1, "handScale": 1, "shoulderWidth": 1},
				  "asymmetry": 1.5,
				  "features": ["x"],
				  "materials": ["y"]
				}
				""";

		List<String> errors = new ModelIntentValidator(new ObjectMapper()).validate(rawJson);

		assertThat(errors).isNotEmpty();
	}

	@Test
	void unJsonMalformadoProduceUnErrorLegibleEnVezDeLanzar() {
		List<String> errors = new ModelIntentValidator(new ObjectMapper()).validate("{ esto no es JSON válido");

		assertThat(errors).isNotEmpty();
	}

	@Test
	void unCampoExtraNoDeclaradoEsRechazado() {
		String rawJson = """
				{
				  "silhouette": "test",
				  "proportions": {"headScale": 1, "armLength": 1, "handScale": 1, "shoulderWidth": 1},
				  "asymmetry": 0.5,
				  "features": ["x"],
				  "materials": ["y"],
				  "campoInventado": "no debería aceptarse"
				}
				""";

		List<String> errors = new ModelIntentValidator(new ObjectMapper()).validate(rawJson);

		assertThat(errors).isNotEmpty();
	}

}
