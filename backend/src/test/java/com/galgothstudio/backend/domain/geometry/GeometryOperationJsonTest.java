package com.galgothstudio.backend.domain.geometry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.galgothstudio.backend.domain.jackson.Vec3JacksonModule;
import com.galgothstudio.backend.domain.model.Vec3;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Ticket 005, AC #2 (rama "fuera de whitelist"): un {@code "op"} que no
 * está en la whitelist cerrada de {@link GeometryOperation} debe hacer
 * fallar la deserialización del batch completo -- ninguna operación llega
 * a existir como objeto Java, así que {@link GeometryEngine#apply} nunca
 * se invoca con un batch parcialmente parseado.
 *
 * <p>También verifica que cada uno de los 9 tipos deserializa desde el
 * formato de wire descrito en el master prompt §9.3
 * ({@code {"op": "resizeCuboid", "target": "...", "scale": [...]}}) y
 * round-tripea sin pérdida.
 */
class GeometryOperationJsonTest {

	private static ObjectMapper mapper() {
		return new ObjectMapper().registerModule(new Vec3JacksonModule());
	}

	@Test
	void unaOperacionFueraDeLaWhitelist_haceFallarLaDeserializacionDelBatchCompleto() {
		String json = """
				[
				  {"op": "resizeCuboid", "target": "hand_right", "scale": [1.2, 1.15, 1.2]},
				  {"op": "deleteEverything", "target": "hand_right"}
				]
				""";

		assertThatThrownBy(() -> mapper().readValue(json, new com.fasterxml.jackson.core.type.TypeReference<List<GeometryOperation>>() {
		})).isInstanceOf(JsonProcessingException.class);
	}

	@Test
	void resizeCuboid_deserializaDelFormatoDelMasterPrompt() throws Exception {
		String json = """
				{"op": "resizeCuboid", "target": "hand_right", "scale": [1.2, 1.15, 1.2]}
				""";

		GeometryOperation op = mapper().readValue(json, GeometryOperation.class);

		assertThat(op).isInstanceOf(ResizeCuboid.class);
		ResizeCuboid resize = (ResizeCuboid) op;
		assertThat(resize.target()).isEqualTo("hand_right");
		assertThat(resize.scale()).isEqualTo(new Vec3(1.2, 1.15, 1.2));
	}

	@Test
	void moveCuboid_deserializaDelFormatoDelMasterPrompt() throws Exception {
		String json = """
				{"op": "moveCuboid", "target": "shoulder_right_detail", "delta": [-0.5, 0.25, 0]}
				""";

		GeometryOperation op = mapper().readValue(json, GeometryOperation.class);

		assertThat(op).isInstanceOf(MoveCuboid.class);
		MoveCuboid move = (MoveCuboid) op;
		assertThat(move.target()).isEqualTo("shoulder_right_detail");
		assertThat(move.delta()).isEqualTo(new Vec3(-0.5, 0.25, 0));
	}

	@Test
	void todosLosTiposDeOperacionRoundTripeanPorJson() throws Exception {
		List<GeometryOperation> operations = List.of(
				new CreateBone("tmp-1", "bone", null, new Vec3(0, 0, 0), new Vec3(0, 0, 0)),
				new CreateCuboid("tmp-2", "cub", "tmp-1", new Vec3(0, 0, 0), new Vec3(1, 1, 1), new Vec3(0, 0, 0), new Vec3(0, 0, 0)),
				new ResizeCuboid("tmp-2", new Vec3(1.1, 1.1, 1.1)),
				new MoveCuboid("tmp-2", new Vec3(1, 0, 0)),
				new RotateCuboid("tmp-2", new Vec3(0, 0, 15)),
				new SetBonePivot("tmp-1", new Vec3(0, 1, 0)),
				new SetBoneRotation("tmp-1", new Vec3(0, 0, 5)),
				new ParentBone("tmp-1", null),
				new RemoveCuboid("tmp-2"));

		ObjectMapper mapper = mapper();
		// mapper.writeValueAsString(Object) perdería el parámetro genérico
		// <GeometryOperation> por erasure (usaría el tipo concreto en
		// runtime de cada elemento, sin pasar por el TypeSerializer de la
		// interfaz sellada) y serializaría sin el campo "op" -- hay que
		// fijar el tipo estático explícitamente con un TypeReference,
		// igual que ya hace la lectura.
		var listType = new com.fasterxml.jackson.core.type.TypeReference<List<GeometryOperation>>() {
		};
		String json = mapper.writerFor(listType).writeValueAsString(operations);
		List<GeometryOperation> parsed = mapper.readValue(json, listType);

		assertThat(parsed).isEqualTo(operations);
	}

}
