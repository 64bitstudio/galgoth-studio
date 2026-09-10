package com.galgothstudio.backend.domain.export;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.galgothstudio.backend.domain.jackson.Vec3JacksonModule;
import com.galgothstudio.backend.domain.jackson.Vec4JacksonModule;
import com.galgothstudio.backend.domain.model.MobProjectModel;
import com.galgothstudio.backend.domain.model.UvLayout;
import com.galgothstudio.backend.domain.model.UvRegionStatus;
import com.galgothstudio.backend.domain.uv.AlphaAutoPackStrategy;
import com.galgothstudio.backend.domain.uv.LegacyUvNormalizationService;
import com.galgothstudio.backend.domain.uv.UvLayoutStrategy;
import org.junit.jupiter.api.Test;

/**
 * Ticket 044, AC de regresión explícito: una revisión real de Fase 1+2
 * (JSON sin el campo {@code status} en sus regiones ni {@code reservations}
 * en su {@code uv} -- forma pre-040, ver `UvRegionUvReservationContractTest`)
 * se exporta a través del pipeline nuevo (`LegacyUvNormalizationService
 * .normalizeIfSafe` -> `BBModelExporterV5.export`) y produce EXACTAMENTE
 * el mismo `.bbmodel` que producía el pipeline de ANTES de este ticket
 * (`BBModelExporterV5.export(model, uvLayoutStrategy)`, que SIEMPRE
 * recomputaba la UV vía {@link AlphaAutoPackStrategy} sin importar lo que
 * hubiera almacenado).
 *
 * <p>La UV almacenada en el fixture es DELIBERADAMENTE distinta de lo que
 * {@link AlphaAutoPackStrategy} calcula hoy para esta geometría (simula el
 * "ajuste menor entre iteraciones de 006/007" que el Diseño técnico §3
 * documenta como motivo real de `LegacyUvNormalizationService`) -- así el
 * test ejercita la rama que SÍ normaliza, la más exigente de las dos.
 */
class LegacyRevisionExportRegressionTest {

	private static final String LEGACY_REVISION_JSON = """
			{
			  "mobId": "legacy-mob", "projectId": "legacy-project", "name": "Legacy Carcomido",
			  "baseType": "humanoid", "units": "minecraft_pixels",
			  "bones": [
			    {"id": "body", "name": "body", "parentId": null, "pivot": [0, 0, 0], "rotation": [0, 0, 0]}
			  ],
			  "cuboids": [
			    {"id": "torso", "name": "torso", "boneId": "body", "from": [-4, 0, -2], "to": [4, 8, 2],
			     "origin": [0, 0, 0], "rotation": [0, 0, 0],
			     "faces": {
			       "north": {"uv": [90, 90, 98, 98], "texture": 0},
			       "south": {"uv": [90, 90, 98, 98], "texture": 0},
			       "east": {"uv": [90, 90, 98, 98], "texture": 0},
			       "west": {"uv": [90, 90, 98, 98], "texture": 0},
			       "up": {"uv": [90, 90, 98, 98], "texture": 0},
			       "down": {"uv": [90, 90, 98, 98], "texture": 0}
			     }}
			  ],
			  "texture": {"width": 64, "height": 64, "storageKey": null},
			  "uv": {
			    "textureWidth": 64, "textureHeight": 64,
			    "regions": [
			      {"cuboidId": "torso", "face": "north", "rect": [90, 90, 98, 98]},
			      {"cuboidId": "torso", "face": "south", "rect": [90, 90, 98, 98]},
			      {"cuboidId": "torso", "face": "east", "rect": [90, 90, 98, 98]},
			      {"cuboidId": "torso", "face": "west", "rect": [90, 90, 98, 98]},
			      {"cuboidId": "torso", "face": "up", "rect": [90, 90, 98, 98]},
			      {"cuboidId": "torso", "face": "down", "rect": [90, 90, 98, 98]}
			    ]
			  },
			  "animations": [], "exportSettings": {"preferredFormatVersion": "v5"}, "referenceImages": []
			}
			""";

	private static ObjectMapper objectMapper() {
		return new ObjectMapper().registerModule(new Vec3JacksonModule()).registerModule(new Vec4JacksonModule());
	}

	@Test
	void unaRevisionLegacySinStatusNiReservationsSeExportaIgualQueAntesDelTicket044() throws Exception {
		MobProjectModel legacyModel = objectMapper().readValue(LEGACY_REVISION_JSON, MobProjectModel.class);
		// AC #1 de 040: campos ausentes deserializan con default seguro.
		assertThat(legacyModel.uv().reservations()).isEmpty();
		assertThat(legacyModel.uv().regions()).hasSize(6).allMatch(r -> r.status() == UvRegionStatus.UNPAINTED);

		LegacyUvNormalizationService legacyUvNormalizationService = new LegacyUvNormalizationService(new AlphaAutoPackStrategy());
		MobProjectModel normalized = legacyUvNormalizationService.normalizeIfSafe(legacyModel);
		String actualBbmodel = BBModelExporterV5.export(normalized);

		// "Antes de este ticket": el exportador SIEMPRE recomputaba la UV vía
		// AlphaAutoPackStrategy, sin importar lo que trajera la Revision
		// almacenada -- se reconstruye acá esa semántica exacta, inline, para
		// no depender del overload de 2 argumentos que este ticket elimina.
		int width = legacyModel.texture().width();
		int height = legacyModel.texture().height();
		UvLayoutStrategy.Result alwaysRecomputed =
				new AlphaAutoPackStrategy().layout(legacyModel.cuboids(), width, height);
		MobProjectModel modelAsBeforeThisTicket = new MobProjectModel(
				legacyModel.mobId(), legacyModel.projectId(), legacyModel.name(), legacyModel.baseType(),
				legacyModel.units(), legacyModel.bones(), alwaysRecomputed.cuboids(), legacyModel.texture(),
				new UvLayout(width, height, alwaysRecomputed.regions()), legacyModel.animations(),
				legacyModel.exportSettings(), legacyModel.referenceImages());
		String expectedBbmodel = BBModelExporterV5.export(modelAsBeforeThisTicket);

		assertThat(actualBbmodel).isEqualTo(expectedBbmodel);
		// prueba de que el test realmente ejercitó la rama que normaliza (la
		// UV almacenada en el fixture es distinta de la fresca a propósito).
		assertThat(normalized.uv().regions()).isNotEqualTo(legacyModel.uv().regions());
		// la Revision original (tal como se deserializó) nunca se muta.
		assertThat(legacyModel.uv().regions()).extracting(r -> r.rect().a()).containsOnly(90.0);
	}

}
