package com.galgothstudio.backend.modelvalidation;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Unidad directa de {@link ModelIntentValidator} contra `contracts/fixtures/model-intent-example.json` (el ejemplo literal del master prompt §9.1, ticket 028). */
class ModelIntentValidatorTest {

	private static final File FIXTURE_FILE = new File("../contracts/fixtures/model-intent-example.json");

	@Test
	void elEjemploLiteralDelMasterPromptEsValido() throws Exception {
		String rawJson = Files.readString(FIXTURE_FILE.toPath());

		List<String> errors = new ModelIntentValidator(new ObjectMapper()).validate(rawJson);

		assertThat(errors).isEmpty();
	}

	/** Sonar (S5976): estos 4 casos comparten la misma forma -- JSON inválido de una manera distinta -> `validate` produce errores no vacíos -- parametrizados en vez de 4 tests casi idénticos. */
	static Stream<Arguments> jsonsInvalidos() {
		return Stream.of(
				Arguments.of(
						"campo requerido faltante",
						"""
						{"silhouette": "test", "asymmetry": 0.5, "features": ["x"], "materials": ["y"]}
						"""),
				Arguments.of(
						"asymmetry fuera de rango 0-1",
						"""
						{
						  "silhouette": "test",
						  "proportions": {"headScale": 1, "armLength": 1, "handScale": 1, "shoulderWidth": 1},
						  "asymmetry": 1.5,
						  "features": ["x"],
						  "materials": ["y"]
						}
						"""),
				Arguments.of("JSON malformado", "{ esto no es JSON válido"),
				Arguments.of(
						"campo extra no declarado",
						"""
						{
						  "silhouette": "test",
						  "proportions": {"headScale": 1, "armLength": 1, "handScale": 1, "shoulderWidth": 1},
						  "asymmetry": 0.5,
						  "features": ["x"],
						  "materials": ["y"],
						  "campoInventado": "no debería aceptarse"
						}
						"""));
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("jsonsInvalidos")
	void unJsonInvalidoProduceErroresDeSchemaEnVezDeLanzar(String caso, String rawJson) {
		List<String> errors = new ModelIntentValidator(new ObjectMapper()).validate(rawJson);

		assertThat(errors).isNotEmpty();
	}

}
