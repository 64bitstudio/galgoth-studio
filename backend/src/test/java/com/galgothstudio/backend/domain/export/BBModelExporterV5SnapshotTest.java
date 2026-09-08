package com.galgothstudio.backend.domain.export;

import static org.assertj.core.api.Assertions.assertThat;

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
 * Ticket 012, AC #1: `BBModelExporterV5` se compara ESTRUCTURALMENTE
 * contra un snapshot aprobado (fixture interna), usando
 * `contracts/fixtures/model-spec-example.json` (expandido en este mismo
 * ticket con casos borde: bone con rotación no trivial, múltiples
 * cuboids por bone, cuboid parentado directo a un bone raíz) como
 * entrada. El snapshot (`bbmodel-golden/model-spec-example.bbmodel.json`)
 * se generó ejecutando el exportador una vez y fue revisado a mano antes
 * de aprobarlo -- un cambio que lo rompe sin querer falla este test, uno
 * intencional requiere actualizar el snapshot explícitamente (nunca en
 * silencio).
 */
class BBModelExporterV5SnapshotTest {

	private static final File FIXTURE_FILE = new File("../contracts/fixtures/model-spec-example.json");
	private static final File GOLDEN_FILE =
			new File("src/test/resources/fixtures/bbmodel-golden/model-spec-example.bbmodel.json");

	@Test
	void bbModelExporterV5CoincideConElSnapshotAprobado() throws IOException, org.json.JSONException {
		ObjectMapper mapper =
				new ObjectMapper().registerModule(new Vec3JacksonModule()).registerModule(new Vec4JacksonModule());
		MobProjectModel model = mapper.readValue(FIXTURE_FILE, MobProjectModel.class);

		String exported = BBModelExporterV5.export(model);
		String golden = Files.readString(GOLDEN_FILE.toPath());

		JSONAssert.assertEquals(golden, exported, JSONCompareMode.STRICT);
	}

	@Test
	void elSnapshotAprobadoRealmenteCubreLosCasosBordeQueDiceElTicket() throws IOException {
		String golden = Files.readString(GOLDEN_FILE.toPath());
		com.fasterxml.jackson.databind.JsonNode root = new ObjectMapper().readTree(golden);

		// Jerarquía: 6 bones, con parent/child real (body -> resto).
		assertThat(root.path("groups")).hasSize(6);
		// Múltiples cuboids: 3 en total, 2 de ellos parentados al MISMO bone raíz "body"
		// (torso_main directo, head_main vía el bone "head").
		assertThat(root.path("elements")).hasSize(3);
		// Rotación no trivial preservada tanto en un bone (armRight) como en un cuboid
		// (arm_right_upper) -- ninguno de los dos se omite ni se trunca.
		boolean armRightGroupHasRotation = false;
		for (com.fasterxml.jackson.databind.JsonNode group : root.path("groups")) {
			if (group.path("uuid").asText().equals("arm_right") && group.has("rotation")) {
				armRightGroupHasRotation = true;
			}
		}
		assertThat(armRightGroupHasRotation).isTrue();
		boolean cuboidHasRotation = false;
		for (com.fasterxml.jackson.databind.JsonNode element : root.path("elements")) {
			if (element.path("uuid").asText().equals("arm_right_upper") && element.has("rotation")) {
				cuboidHasRotation = true;
			}
		}
		assertThat(cuboidHasRotation).isTrue();
	}

}
