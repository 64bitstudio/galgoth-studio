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

/** Unidad directa de {@link TexturePlanValidator} contra `contracts/fixtures/texture-plan-example.json` (ticket 052) -- mismo patrón que {@code ModelIntentValidatorTest} (028). */
class TexturePlanValidatorTest {

	private static final File FIXTURE_FILE = new File("../contracts/fixtures/texture-plan-example.json");

	@Test
	void elEjemploDeFixtureEsValido() throws Exception {
		String rawJson = Files.readString(FIXTURE_FILE.toPath());

		List<String> errors = new TexturePlanValidator(new ObjectMapper()).validate(rawJson);

		assertThat(errors).isEmpty();
	}

	/** Sonar (S5976): estos casos comparten la misma forma -- JSON inválido de una manera distinta -> `validate` produce errores no vacíos -- parametrizados en vez de tests casi idénticos. */
	static Stream<Arguments> jsonsInvalidos() {
		return Stream.of(
				Arguments.of(
						"campo requerido faltante (falta palette)",
						"""
						{
						  "boneLabels": [{"boneId": "b1", "boneName": "body", "semanticLabel": "torso"}],
						  "materialNotes": [{"boneId": "b1", "boneName": "body", "face": "north", "note": "cuero"}]
						}
						"""),
				Arguments.of(
						"color hex con formato inválido",
						"""
						{
						  "boneLabels": [{"boneId": "b1", "boneName": "body", "semanticLabel": "torso"}],
						  "palette": {"dominantColorHex": "no-es-hex", "accentColorHex": "#FFFFFF"},
						  "materialNotes": [{"boneId": "b1", "boneName": "body", "face": "north", "note": "cuero"}]
						}
						"""),
				Arguments.of(
						"face fuera del enum permitido",
						"""
						{
						  "boneLabels": [{"boneId": "b1", "boneName": "body", "semanticLabel": "torso"}],
						  "palette": {"dominantColorHex": "#000000", "accentColorHex": "#FFFFFF"},
						  "materialNotes": [{"boneId": "b1", "boneName": "body", "face": "diagonal", "note": "cuero"}]
						}
						"""),
				Arguments.of("JSON malformado", "{ esto no es JSON válido"),
				Arguments.of(
						"campo extra no declarado",
						"""
						{
						  "boneLabels": [{"boneId": "b1", "boneName": "body", "semanticLabel": "torso"}],
						  "palette": {"dominantColorHex": "#000000", "accentColorHex": "#FFFFFF"},
						  "materialNotes": [{"boneId": "b1", "boneName": "body", "face": "north", "note": "cuero"}],
						  "campoInventado": "no debería aceptarse"
						}
						"""));
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("jsonsInvalidos")
	void unJsonInvalidoProduceErroresDeSchemaEnVezDeLanzar(String caso, String rawJson) {
		List<String> errors = new TexturePlanValidator(new ObjectMapper()).validate(rawJson);

		assertThat(errors).isNotEmpty();
	}

}
