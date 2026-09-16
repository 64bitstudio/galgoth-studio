package com.galgothstudio.backend.aiorchestrator;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * AC explícito del ticket 105: el benchmark existe para probar que la
 * mejora GENERALIZA, no para que un personaje concreto funcione. Si algún
 * componente de producción tuviera una rama especial para "Carcomido", el
 * benchmark estaría midiendo su propio atajo -- el anti-patrón que el
 * Product Owner nombró literalmente ("hardcodear para el caso Carcomido").
 *
 * <p>Este test es la verificación real de ese criterio, no una promesa en
 * un documento: recorre el código de producción COMPLETO del backend y
 * falla si el nombre del personaje aparece en el CÓDIGO.
 *
 * <p><b>Los comentarios se excluyen a propósito, y no es una concesión
 * cómoda</b>: al escribir este test, las únicas 5 apariciones del
 * backend resultaron ser Javadoc de PROCEDENCIA -- "estos valores salen
 * del sample real `carcomido_minecraft_cuboids.bbmodel`", "hallazgo real
 * reproducido con el mob `Carcomido_v1`". Esa trazabilidad es justamente
 * lo que el equipo quiere conservar (documenta de dónde salió un dato
 * real en vez de dejarlo como número mágico), y no afecta en nada lo que
 * el programa hace. Lo que el AC prohíbe es que el COMPORTAMIENTO dependa
 * del personaje: un {@code if} sobre su nombre, un literal usado en
 * runtime. Eso sí lo detecta este test, porque el literal sobrevive al
 * borrado de comentarios.
 */
class NoBenchmarkSpecificCodeTest {

	private static final Path PRODUCTION_SOURCES = Path.of("src/main/java");

	/** El nombre del personaje del benchmark, en minúsculas -- la comparación normaliza el caso. */
	private static final String BENCHMARK_CHARACTER = "carcomido";

	@Test
	void ningunCodigoDeProduccionRamificaNiLiteralizaAlPersonajeDelBenchmark_AC() throws IOException {
		try (Stream<Path> sources = Files.walk(PRODUCTION_SOURCES)) {
			List<String> offenders = sources.filter(Files::isRegularFile)
					.filter(path -> path.toString().endsWith(".java"))
					.filter(NoBenchmarkSpecificCodeTest::codeMentionsBenchmarkCharacter)
					.map(Path::toString)
					.toList();

			assertThat(offenders)
					.as("ningún archivo de producción debe tener lógica ni literales específicos del benchmark (los comentarios de procedencia sí están permitidos)")
					.isEmpty();
		}
	}

	private static boolean codeMentionsBenchmarkCharacter(Path path) {
		try {
			String source = Files.readString(path, StandardCharsets.UTF_8);
			return withoutComments(source).toLowerCase(Locale.ROOT).contains(BENCHMARK_CHARACTER);
		} catch (IOException e) {
			throw new IllegalStateException("No se pudo leer un archivo de producción: " + path, e);
		}
	}

	/**
	 * Borra comentarios de bloque ({@code /*...*}{@code /}, incluido
	 * Javadoc) y de línea ({@code //...}). Deliberadamente simple: no
	 * pretende ser un parser de Java. El único falso negativo posible sería
	 * un {@code "//"} dentro de un string literal que además contuviera el
	 * nombre del personaje después -- un caso que no existe en este
	 * código base y que, de aparecer, sería justamente el literal que este
	 * test busca.
	 */
	private static String withoutComments(String source) {
		return source.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("(?m)//.*$", " ");
	}

}
