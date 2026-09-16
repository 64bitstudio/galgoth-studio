package com.galgothstudio.backend.aiorchestrator.planner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.galgothstudio.backend.aiorchestrator.planner.SecondaryGeometryPlanner.BoneDescriptor;
import com.galgothstudio.backend.aiorchestrator.provider.MockReasoningProvider;
import com.galgothstudio.backend.domain.geometry.CreateCuboid;
import com.galgothstudio.backend.domain.geometry.GeometryOperation;
import com.galgothstudio.backend.domain.jackson.Vec3JacksonModule;
import com.galgothstudio.backend.domain.jackson.Vec4JacksonModule;
import com.galgothstudio.backend.domain.model.ModelIntent;
import com.galgothstudio.backend.domain.model.Proportions;
import com.galgothstudio.backend.domain.model.Vec3;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * `SecondaryGeometryPlanner` con `MockReasoningProvider` (ticket 099) --
 * nunca llama a la API real de Anthropic.
 */
class SecondaryGeometryPlannerTest {

	private static ObjectMapper objectMapper() {
		return new ObjectMapper().registerModule(new Vec3JacksonModule()).registerModule(new Vec4JacksonModule());
	}

	private static ModelIntent aModelIntent() {
		return new ModelIntent(
				"hunched humanoid", new Proportions(1.0, 1.0, 1.0, 1.0), 0.5, List.of("garras grandes", "ropa desgarrada"),
				List.of("piel verdosa"));
	}

	private static List<BoneDescriptor> primaryBones() {
		return List.of(
				new BoneDescriptor("torso", "torso", new Vec3(0, 12, 0)),
				new BoneDescriptor("left_hand", "leftHand", new Vec3(6, 10, 0)));
	}

	@Test
	void unaPropuestaValida_seDeserializaComoOperacionesDeGeometria() {
		MockReasoningProvider mockProvider = new MockReasoningProvider();
		mockProvider.setNextResponse(
				"""
				[
				  {"op":"createCuboid","tempId":"claw1","name":"claw","boneId":"left_hand","from":[4,8,-3],"to":[6,9,-2],"origin":[5,8,-2],"rotation":[0,0,0],"semanticPart":"CLAW"}
				]
				""");
		SecondaryGeometryPlanner planner = new SecondaryGeometryPlanner(mockProvider, objectMapper());

		RawOperationsResult result = planner.requestOperations(aModelIntent(), primaryBones(), 10);

		assertThat(result.operations()).hasSize(1);
		GeometryOperation op = result.operations().get(0);
		assertThat(op).isInstanceOf(CreateCuboid.class);
		CreateCuboid cuboid = (CreateCuboid) op;
		assertThat(cuboid.boneId()).isEqualTo("left_hand");
		assertThat(cuboid.semanticPart()).isEqualTo("CLAW");
	}

	@Test
	void unaListaVacia_esValida_unPersonajeSimplePuedeNoNecesitarGeometriaSecundaria() {
		MockReasoningProvider mockProvider = new MockReasoningProvider();
		mockProvider.setNextResponse("[]");
		SecondaryGeometryPlanner planner = new SecondaryGeometryPlanner(mockProvider, objectMapper());

		RawOperationsResult result = planner.requestOperations(aModelIntent(), primaryBones(), 10);

		assertThat(result.operations()).isEmpty();
	}

	@Test
	void unaOperacionFueraDeLaWhitelist_lanzaInvalidGeometryProposalException() {
		MockReasoningProvider mockProvider = new MockReasoningProvider();
		// "createBone" no está permitido en geometría secundaria -- pero a
		// nivel de deserialización Jackson, createBone SÍ es un tipo válido
		// de GeometryOperation (whitelist compartida con GeometryPlannerService) --
		// el rechazo real de "tipo no permitido en este contexto" lo hace
		// SecondaryGeometryConstraints, no la deserialización. Acá se cubre
		// el caso de JSON verdaderamente inválido (fuera de los 9 tipos).
		mockProvider.setNextResponse("""
				[{"op":"noExiste","foo":"bar"}]
				""");
		SecondaryGeometryPlanner planner = new SecondaryGeometryPlanner(mockProvider, objectMapper());

		assertThatThrownBy(() -> planner.requestOperations(aModelIntent(), primaryBones(), 10))
				.isInstanceOf(InvalidGeometryProposalException.class);
	}

	@Test
	void sinRespuestaExplicita_elMockDefaultUsaUnBonePrimarioRealDelPrompt_nuncaUnIdInventado() {
		MockReasoningProvider mockProvider = new MockReasoningProvider(); // sin setNextResponse -- usa el default por promptVersion.
		SecondaryGeometryPlanner planner = new SecondaryGeometryPlanner(mockProvider, objectMapper());

		RawOperationsResult result = planner.requestOperations(aModelIntent(), primaryBones(), 10);

		assertThat(result.operations()).hasSize(1);
		CreateCuboid cuboid = (CreateCuboid) result.operations().get(0);
		assertThat(cuboid.boneId()).isIn("torso", "left_hand"); // uno de los bones REALES pasados, nunca inventado.
	}

	@Test
	void planStreaming_entregaCadaOperacionAMedidaQueLlega() {
		MockReasoningProvider mockProvider = new MockReasoningProvider();
		mockProvider.setNextResponse(
				"""
				[
				  {"op":"createCuboid","tempId":"c1","name":"garra1","boneId":"left_hand","from":[4,8,-3],"to":[5,9,-2],"origin":[4.5,8,-2],"rotation":[0,0,0],"semanticPart":"CLAW"},
				  {"op":"createCuboid","tempId":"c2","name":"garra2","boneId":"left_hand","from":[5,8,-3],"to":[6,9,-2],"origin":[5.5,8,-2],"rotation":[0,0,0],"semanticPart":"CLAW"}
				]
				""");
		SecondaryGeometryPlanner planner = new SecondaryGeometryPlanner(mockProvider, objectMapper());
		List<GeometryOperation> streamed = new ArrayList<>();

		RawOperationsResult result = planner.planStreaming(aModelIntent(), primaryBones(), 10, streamed::add);

		assertThat(streamed).hasSize(2);
		assertThat(result.operations()).hasSize(2);
	}
}
