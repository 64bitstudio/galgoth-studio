package com.galgothstudio.backend.aiorchestrator.edit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.galgothstudio.backend.aiorchestrator.planner.InvalidGeometryProposalException;
import com.galgothstudio.backend.aiorchestrator.provider.MockReasoningProvider;
import com.galgothstudio.backend.domain.jackson.Vec3JacksonModule;
import com.galgothstudio.backend.domain.jackson.Vec4JacksonModule;
import com.galgothstudio.backend.domain.model.BaseType;
import com.galgothstudio.backend.domain.model.Bone;
import com.galgothstudio.backend.domain.model.Cuboid;
import com.galgothstudio.backend.domain.model.CuboidFaces;
import com.galgothstudio.backend.domain.model.ExportSettings;
import com.galgothstudio.backend.domain.model.Face;
import com.galgothstudio.backend.domain.model.FormatVersion;
import com.galgothstudio.backend.domain.model.MobProjectModel;
import com.galgothstudio.backend.domain.model.TextureDocument;
import com.galgothstudio.backend.domain.model.UvLayout;
import com.galgothstudio.backend.domain.model.Vec3;
import com.galgothstudio.backend.domain.model.Vec4;
import com.galgothstudio.backend.domain.uv.AlphaAutoPackStrategy;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * `AiGeometryEditPlannerService` con `MockReasoningProvider` (025) --
 * nunca llama a la API real de Anthropic. Primer test real que ejercita
 * el DEFAULT literal de `MockReasoningProvider` (el ejemplo de edición
 * del master prompt §9.3, nunca antes usado por ningún flujo real desde
 * el ticket 025).
 */
class AiGeometryEditPlannerServiceTest {

	private static final CuboidFaces PLACEHOLDER_FACES;

	static {
		Face placeholder = new Face(new Vec4(0, 0, 0, 0), null);
		PLACEHOLDER_FACES = new CuboidFaces(placeholder, placeholder, placeholder, placeholder, placeholder, placeholder);
	}

	private static ObjectMapper objectMapper() {
		return new ObjectMapper().registerModule(new Vec3JacksonModule()).registerModule(new Vec4JacksonModule());
	}

	/** Bones/cuboids con los MISMOS ids que el default literal de `MockReasoningProvider` (`hand_right`/`hand_left`/`shoulder_right_detail`) -- para que la resolución de `target` sea real, no una coincidencia. */
	private static MobProjectModel modelWithHandsAndShoulder() {
		Bone body = new Bone("body", "body", null, new Vec3(0, 0, 0), new Vec3(0, 0, 0));
		Cuboid handRight = new Cuboid("hand_right", "hand_right", "body", new Vec3(0, 0, 0), new Vec3(2, 2, 2), new Vec3(0, 0, 0), new Vec3(0, 0, 0), PLACEHOLDER_FACES);
		Cuboid handLeft = new Cuboid("hand_left", "hand_left", "body", new Vec3(0, 0, 0), new Vec3(2, 2, 2), new Vec3(0, 0, 0), new Vec3(0, 0, 0), PLACEHOLDER_FACES);
		Cuboid shoulderRightDetail = new Cuboid(
				"shoulder_right_detail", "shoulder_right_detail", "body", new Vec3(0, 0, 0), new Vec3(1, 1, 1), new Vec3(0, 0, 0), new Vec3(0, 0, 0),
				PLACEHOLDER_FACES);
		TextureDocument texture = new TextureDocument(64, 64, null);
		return new MobProjectModel(
				"mob-1", "project-1", "Test Mob", BaseType.HUMANOID, MobProjectModel.UNITS_MINECRAFT_PIXELS, List.of(body),
				List.of(handRight, handLeft, shoulderRightDetail), texture, new UvLayout(64, 64, List.of()), List.of(),
				new ExportSettings(FormatVersion.V5), List.of());
	}

	private AiGeometryEditPlannerService newService(MockReasoningProvider mockProvider) {
		return new AiGeometryEditPlannerService(mockProvider, objectMapper(), new AlphaAutoPackStrategy());
	}

	@Test
	void el_default_literal_del_master_prompt_9_3_se_aplica_de_verdad_sobre_ids_reales() {
		MockReasoningProvider mockProvider = new MockReasoningProvider(); // sin configurar -- usa el default literal §9.3
		AiGeometryEditPlannerService service = newService(mockProvider);

		EditPlanResult result = service.plan(modelWithHandsAndShoulder(), "Haz las manos más grandes y los hombros más irregulares");

		assertThat(result.summary()).isEqualTo("Increase both hands and add asymmetry to shoulders");
		assertThat(result.afterModel().cuboids()).hasSize(3); // resize/move, no crea ni borra cuboids
		Cuboid resizedHandRight = result.afterModel().cuboids().stream().filter(c -> c.id().equals("hand_right")).findFirst().orElseThrow();
		assertThat(resizedHandRight.to()).isNotEqualTo(new Vec3(2, 2, 2)); // el resizeCuboid sí cambió sus dimensiones reales
	}

	@Test
	void una_operacion_fuera_de_la_whitelist_se_reporta_como_InvalidGeometryProposalException() {
		MockReasoningProvider mockProvider = new MockReasoningProvider();
		mockProvider.setNextResponse("{\"summary\":\"x\",\"operations\":[{\"op\":\"deleteEverything\"}]}");
		AiGeometryEditPlannerService service = newService(mockProvider);
		MobProjectModel model = modelWithHandsAndShoulder();

		assertThatThrownBy(() -> service.plan(model, "instrucción cualquiera")).isInstanceOf(InvalidGeometryProposalException.class);
	}

	@Test
	void referenciar_un_id_que_no_existe_en_el_modelo_actual_se_rechaza() {
		MockReasoningProvider mockProvider = new MockReasoningProvider();
		mockProvider.setNextResponse(
				"{\"summary\":\"x\",\"operations\":[{\"op\":\"resizeCuboid\",\"target\":\"no_existe\",\"scale\":[1.2,1.2,1.2]}]}");
		AiGeometryEditPlannerService service = newService(mockProvider);
		MobProjectModel model = modelWithHandsAndShoulder();

		assertThatThrownBy(() -> service.plan(model, "instrucción cualquiera")).isInstanceOf(InvalidGeometryProposalException.class);
	}

	@Test
	void un_json_malformado_se_reporta_como_InvalidGeometryProposalException_conservando_el_providerResponse() {
		MockReasoningProvider mockProvider = new MockReasoningProvider();
		mockProvider.setNextResponse("esto no es JSON");
		AiGeometryEditPlannerService service = newService(mockProvider);
		MobProjectModel model = modelWithHandsAndShoulder();

		assertThatThrownBy(() -> service.plan(model, "instrucción cualquiera"))
				.isInstanceOf(InvalidGeometryProposalException.class)
				.satisfies(e -> assertThat(((InvalidGeometryProposalException) e).providerResponse().provider()).isEqualTo("mock"));
	}

}
