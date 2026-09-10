package com.galgothstudio.backend.aiorchestrator.texture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.galgothstudio.backend.aiorchestrator.provider.MockVisionProvider;
import com.galgothstudio.backend.domain.jackson.Vec3JacksonModule;
import com.galgothstudio.backend.domain.jackson.Vec4JacksonModule;
import com.galgothstudio.backend.domain.model.BaseType;
import com.galgothstudio.backend.domain.model.Bone;
import com.galgothstudio.backend.domain.model.ExportSettings;
import com.galgothstudio.backend.domain.model.FormatVersion;
import com.galgothstudio.backend.domain.model.MobProjectModel;
import com.galgothstudio.backend.domain.model.TextureDocument;
import com.galgothstudio.backend.domain.model.UvLayout;
import com.galgothstudio.backend.domain.model.Vec3;
import com.galgothstudio.backend.modelvalidation.TexturePlanValidator;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * `TexturePlanService` con `MockVisionProvider` (ticket 025) -- nunca
 * llama a la API real de Anthropic. Ver el Javadoc de
 * {@link TexturePlanService} para el porqué de usar `VisionModelProvider`
 * (no `StructuredReasoningProvider`, el nombrado literalmente por el
 * ticket 052 y el Diseño técnico §11 -- hallazgo real reportado ahí).
 */
class TexturePlanServiceTest {

	private static final String VALID_TEXTURE_PLAN =
			"""
			{
			  "boneLabels": [
			    {"boneId": "body-real-id", "boneName": "body", "semanticLabel": "torso"},
			    {"boneId": "head-real-id", "boneName": "head", "semanticLabel": "cabeza"}
			  ],
			  "palette": {"dominantColorHex": "#4A5238", "accentColorHex": "#8B2E2E"},
			  "materialNotes": [
			    {"boneId": "body-real-id", "boneName": "body", "face": "north", "note": "cuero desgastado con parches"},
			    {"boneId": "head-real-id", "boneName": "head", "face": "north", "note": "piel grisácea agrietada"}
			  ]
			}
			""";

	private static ObjectMapper objectMapper() {
		return new ObjectMapper().registerModule(new Vec3JacksonModule()).registerModule(new Vec4JacksonModule());
	}

	/** Modelo mínimo con 2 bones reales (ids `body-real-id`/`head-real-id`) -- sin cuboids, no hacen falta para este paso. */
	private static MobProjectModel modelWithTwoBones() {
		Bone body = new Bone("body-real-id", "body", null, new Vec3(0, 0, 0), new Vec3(0, 0, 0));
		Bone head = new Bone("head-real-id", "head", "body-real-id", new Vec3(0, 12, 0), new Vec3(0, 0, 0));
		TextureDocument texture = new TextureDocument(64, 64, null);
		return new MobProjectModel(
				"mob-1", "project-1", "Test Mob", BaseType.HUMANOID, MobProjectModel.UNITS_MINECRAFT_PIXELS, List.of(body, head), List.of(),
				texture, new UvLayout(64, 64, List.of()), List.of(), new ExportSettings(FormatVersion.V5), List.of());
	}

	private TexturePlanService newService(MockVisionProvider mockProvider) {
		return new TexturePlanService(mockProvider, new TexturePlanValidator(objectMapper()), objectMapper());
	}

	@Test
	void una_respuesta_valida_del_proveedor_se_parsea_como_TexturePlan_AC1() {
		MockVisionProvider mockProvider = new MockVisionProvider();
		mockProvider.setNextResponse(VALID_TEXTURE_PLAN);
		TexturePlanService service = newService(mockProvider);

		TexturePlanAnalysisResult result = service.analyze(new byte[] {1, 2, 3}, "image/png", modelWithTwoBones());

		assertThat(result.texturePlan().boneLabels()).hasSize(2);
		assertThat(result.texturePlan().boneLabels().getFirst().boneId()).isEqualTo("body-real-id");
		assertThat(result.texturePlan().palette().dominantColorHex()).isEqualTo("#4A5238");
		assertThat(result.texturePlan().materialNotes()).hasSize(2);
		assertThat(result.providerResponse().provider()).isEqualTo("mock");
	}

	@Test
	void una_respuesta_invalida_del_proveedor_detiene_el_flujo_sin_generar_nada_AC2() {
		MockVisionProvider mockProvider = new MockVisionProvider();
		mockProvider.setNextResponse("{\"boneLabels\": []}"); // faltan palette/materialNotes, boneLabels vacío
		TexturePlanService service = newService(mockProvider);

		assertThatThrownBy(() -> service.analyze(new byte[] {1}, "image/png", modelWithTwoBones()))
				.isInstanceOf(InvalidTexturePlanException.class)
				.satisfies(e -> {
					InvalidTexturePlanException invalid = (InvalidTexturePlanException) e;
					assertThat(invalid.validationErrors()).isNotEmpty();
					assertThat(invalid.providerResponse().provider()).isEqualTo("mock"); // se conserva incluso en el camino de fallo
				});
	}

	@Test
	void un_JSON_directamente_malformado_tambien_se_reporta_como_InvalidTexturePlanException() {
		MockVisionProvider mockProvider = new MockVisionProvider();
		mockProvider.setNextResponse("esto no es JSON");
		TexturePlanService service = newService(mockProvider);

		assertThatThrownBy(() -> service.analyze(new byte[] {1}, "image/png", modelWithTwoBones())).isInstanceOf(InvalidTexturePlanException.class);
	}

}
