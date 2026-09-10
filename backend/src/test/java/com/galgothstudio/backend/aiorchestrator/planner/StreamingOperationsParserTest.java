package com.galgothstudio.backend.aiorchestrator.planner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.galgothstudio.backend.domain.geometry.CreateBone;
import com.galgothstudio.backend.domain.geometry.CreateCuboid;
import com.galgothstudio.backend.domain.geometry.GeometryOperation;
import com.galgothstudio.backend.domain.jackson.Vec3JacksonModule;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * Ticket 038 -- {@link StreamingOperationsParser} debe entregar cada
 * {@link GeometryOperation} completa apenas cierra su llave, sin importar
 * en qué puntos exactos el proveedor cortó el texto en fragmentos (deltas
 * SSE reales de la API de Anthropic pueden cortar en cualquier byte).
 */
class StreamingOperationsParserTest {

	private static final String TWO_OPERATIONS_JSON =
			"""
			[
			  {"op":"createBone","tempId":"root","name":"root","parentId":null,"pivot":[0,0,0],"rotation":[0,0,0]},
			  {"op":"createCuboid","tempId":"c1","name":"torso","boneId":"root","from":[0,0,0],"to":[4,4,4],"origin":[2,2,2],"rotation":[0,0,0]}
			]
			""";

	private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new Vec3JacksonModule());

	@Test
	void un_solo_delta_con_el_array_completo_entrega_las_dos_operaciones_en_orden() {
		List<GeometryOperation> received = new ArrayList<>();
		StreamingOperationsParser parser = new StreamingOperationsParser(objectMapper, received::add);

		parser.feed(TWO_OPERATIONS_JSON);

		assertThat(received).hasSize(2);
		assertThat(received.get(0)).isInstanceOf(CreateBone.class);
		assertThat(received.get(1)).isInstanceOf(CreateCuboid.class);
		assertThat(((CreateBone) received.get(0)).name()).isEqualTo("root");
		assertThat(((CreateCuboid) received.get(1)).name()).isEqualTo("torso");
	}

	@Test
	void cada_operacion_se_entrega_apenas_cierra_su_propia_llave_no_al_final_del_array() {
		List<GeometryOperation> received = new ArrayList<>();
		StreamingOperationsParser parser = new StreamingOperationsParser(objectMapper, received::add);

		String firstObjectOnly = "[{\"op\":\"createBone\",\"tempId\":\"root\",\"name\":\"root\",\"parentId\":null,\"pivot\":[0,0,0],\"rotation\":[0,0,0]}";
		parser.feed(firstObjectOnly);

		assertThat(received).hasSize(1); // ya se entregó, aunque el array todavía no cerró ni el segundo objeto empezó
	}

	@Test
	void tolera_el_texto_partido_en_fragmentos_de_un_solo_caracter_en_cualquier_punto() {
		List<GeometryOperation> received = new ArrayList<>();
		StreamingOperationsParser parser = new StreamingOperationsParser(objectMapper, received::add);

		for (char c : TWO_OPERATIONS_JSON.toCharArray()) {
			parser.feed(String.valueOf(c));
		}

		assertThat(received).hasSize(2);
	}

	@Test
	void tolera_particiones_aleatorias_del_texto_en_fragmentos_de_tamano_variable() {
		// Deltas SSE reales no cortan en límites "convenientes" -- se
		// simulan 20 particiones aleatorias distintas del mismo JSON para
		// no depender de un único punto de corte específico.
		Random random = new Random(42);
		for (int trial = 0; trial < 20; trial++) {
			List<GeometryOperation> received = new ArrayList<>();
			StreamingOperationsParser parser = new StreamingOperationsParser(objectMapper, received::add);

			int cursor = 0;
			while (cursor < TWO_OPERATIONS_JSON.length()) {
				int chunkSize = 1 + random.nextInt(5);
				int end = Math.min(cursor + chunkSize, TWO_OPERATIONS_JSON.length());
				parser.feed(TWO_OPERATIONS_JSON.substring(cursor, end));
				cursor = end;
			}

			assertThat(received).as("intento #%d", trial).hasSize(2);
		}
	}

	@Test
	void ignora_espacios_saltos_de_linea_y_comas_entre_elementos() {
		List<GeometryOperation> received = new ArrayList<>();
		StreamingOperationsParser parser = new StreamingOperationsParser(objectMapper, received::add);

		parser.feed("[\n\n  {\"op\":\"createBone\",\"tempId\":\"root\",\"name\":\"root\",\"parentId\":null,\"pivot\":[0,0,0],\"rotation\":[0,0,0]}   ,\n]");

		assertThat(received).hasSize(1);
	}

	@Test
	void una_llave_dentro_de_un_string_no_cuenta_como_apertura_o_cierre_de_elemento() {
		List<GeometryOperation> received = new ArrayList<>();
		StreamingOperationsParser parser = new StreamingOperationsParser(objectMapper, received::add);

		// El nombre contiene literalmente "{" y "}" -- no debe confundir el
		// conteo de profundidad, que solo cuenta llaves FUERA de strings.
		parser.feed("[{\"op\":\"createBone\",\"tempId\":\"root\",\"name\":\"ro{ot}\",\"parentId\":null,\"pivot\":[0,0,0],\"rotation\":[0,0,0]}]");

		assertThat(received).hasSize(1);
		assertThat(((CreateBone) received.get(0)).name()).isEqualTo("ro{ot}");
	}

	@Test
	void una_llave_escapada_dentro_de_un_string_tampoco_rompe_el_conteo() {
		List<GeometryOperation> received = new ArrayList<>();
		StreamingOperationsParser parser = new StreamingOperationsParser(objectMapper, received::add);

		// Comilla escapada dentro del string (\") no debe cerrar el string
		// antes de tiempo -- el caracter siguiente ('}') sigue "dentro".
		parser.feed("[{\"op\":\"createBone\",\"tempId\":\"root\",\"name\":\"ro\\\"ot\",\"parentId\":null,\"pivot\":[0,0,0],\"rotation\":[0,0,0]}]");

		assertThat(received).hasSize(1);
		assertThat(((CreateBone) received.get(0)).name()).isEqualTo("ro\"ot");
	}

	@Test
	void un_elemento_que_no_deserializa_como_operacion_valida_lanza_excepcion_especifica() {
		StreamingOperationsParser parser = new StreamingOperationsParser(objectMapper, op -> { });

		assertThatThrownBy(() -> parser.feed("[{\"op\":\"noExisteEnLaWhitelist\",\"foo\":\"bar\"}]"))
				.isInstanceOf(StreamingOperationParseException.class);
	}

}
