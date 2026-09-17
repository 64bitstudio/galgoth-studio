package com.galgothstudio.backend.aiorchestrator;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Ticket 116 -- el canal de advertencias. Lo que se fija acá no es el
 * formato por el formato: es que la advertencia sobreviva al viaje a la
 * base y vuelva con su tipo intacto, que es lo único que la hace
 * consultable de verdad y no "un string en un log".
 */
class GenerationWarningTest {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void unaAdvertenciaSobreviveElViajeAJsonYVuelveConSuTipo() throws Exception {
		GenerationWarning original = new GenerationWarning(
				GenerationWarning.Type.GEOMETRIA_ENGROSADA, "más fino que un téxel a esta densidad (eje z: 0.1 -> 0.25)", "Chest Crack");

		String json = objectMapper.writeValueAsString(List.of(original));
		List<GenerationWarning> vuelta = objectMapper.readValue(json, new TypeReference<List<GenerationWarning>>() {});

		assertThat(vuelta).singleElement().isEqualTo(original);
	}

	/**
	 * El tipo es un enum cerrado, no un string libre, justamente para poder
	 * filtrar por categoría sin volver a parsear texto -- mismo criterio que
	 * ya seguía {@code TextureContentValidator.Finding}.
	 */
	@Test
	void elTipoViajaComoNombreDeEnum_filtrableSinParsearTexto() throws Exception {
		String json = objectMapper.writeValueAsString(
				new GenerationWarning(GenerationWarning.Type.BANDA_NEGRA_ANCHA, "3 cara(s) con banda ancha"));

		assertThat(json).contains("\"BANDA_NEGRA_ANCHA\"");
	}

	/** Una advertencia del job entero (no de una pieza concreta) no inventa un subject. */
	@Test
	void unaAdvertenciaDelJobEnteroNoInventaUnSubject() {
		GenerationWarning warning = new GenerationWarning(GenerationWarning.Type.BORDES_RELLENADOS, "rellenados en 12 de 230 caras");

		assertThat(warning.subject()).isNull();
	}

	/**
	 * AC del 116, y la razón por la que `warnings_jsonb` es nullable: un job
	 * que CORRIÓ siempre escribe `[]` -- nunca deja NULL -- así que "no hubo
	 * advertencias" no se confunde con "no se midió".
	 *
	 * <p>La distinción vive en la BASE, no en la API: NULL queda reservado
	 * para los jobs anteriores a este ticket, y sirve para analizar el
	 * histórico. La API normaliza las dos a `[]`, porque un job viejo sin
	 * advertencias registradas no tiene nada útil que mostrarle al consumidor
	 * (y porque devolver colecciones nulas es, con razón, un smell).
	 */
	@Test
	void unJobQueCorrioSerializaListaVacia_nuncaNull_AC() throws Exception {
		assertThat(objectMapper.writeValueAsString(List.<GenerationWarning>of())).isEqualTo("[]");
	}

}
