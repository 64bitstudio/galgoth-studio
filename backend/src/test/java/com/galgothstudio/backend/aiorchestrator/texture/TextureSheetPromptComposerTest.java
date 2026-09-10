package com.galgothstudio.backend.aiorchestrator.texture;

import static org.assertj.core.api.Assertions.assertThat;

import com.galgothstudio.backend.domain.model.FaceName;
import com.galgothstudio.backend.domain.model.Vec3;
import com.galgothstudio.backend.domain.model.Vec4;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * `TextureSheetPromptComposer` -- ticket 053, Diseño técnico §11 punto 3
 * y §21, AC: "el prompt... incluye un background/mask determinista que
 * delimita visualmente cada sheetRect".
 */
class TextureSheetPromptComposerTest {

	private static TextureGenerationSheet sheet() {
		CuboidFacePlacement north = new CuboidFacePlacement(
				"cube-a", FaceName.NORTH, new Vec4(0, 0, 8, 8), new Vec4(20, 20, 28, 28), new Vec3(4, 4, 4), "front");
		CuboidFacePlacement up = new CuboidFacePlacement(
				"cube-a", FaceName.UP, new Vec4(10, 0, 18, 8), new Vec4(0, 0, 8, 8), new Vec3(4, 4, 4), "top");
		return new TextureGenerationSheet(
				"head-1", "head", List.of(north, up), "cabeza", "dominante #111111, acento #222222",
				"north: cuero desgastado", "ref-1", 20, 10);
	}

	@Test
	void elPromptIncluyeLasDimensionesDeLaSheetYLaEtiquetaSemantica() {
		String prompt = TextureSheetPromptComposer.compose(sheet());

		assertThat(prompt).contains("20x10").contains("head").contains("cabeza");
	}

	@Test
	void elPromptIncluyeLaPaletaYLasNotasDeMaterialDelTexturePlan() {
		String prompt = TextureSheetPromptComposer.compose(sheet());

		assertThat(prompt).contains("#111111").contains("#222222").contains("cuero desgastado");
	}

	@Test
	void elPromptDelimitaVisualmenteCadaSheetRect_conElGutterExplicito_AC_backgroundMaskDeterminista() {
		String prompt = TextureSheetPromptComposer.compose(sheet());

		assertThat(prompt).contains(TextureGenerationSheetPlanner.GUTTER_PX + "px");
		assertThat(prompt).contains("[0,0]-[8,8]"); // sheetRect de "north"
		assertThat(prompt).contains("[10,0]-[18,8]"); // sheetRect de "up"
		assertThat(prompt).contains("front");
		assertThat(prompt).contains("top");
	}

	@Test
	void esDeterminista_mismoSheet_mismoPrompt() {
		TextureGenerationSheet sheet = sheet();

		assertThat(TextureSheetPromptComposer.compose(sheet)).isEqualTo(TextureSheetPromptComposer.compose(sheet));
	}

	@Test
	void sinNotasDeMaterial_noAgregaLaLineaDeNotas() {
		TextureGenerationSheet sheet = new TextureGenerationSheet(
				"head-1", "head", sheet().placements(), "cabeza", "dominante #111111, acento #222222", "", "ref-1", 20, 10);

		assertThat(TextureSheetPromptComposer.compose(sheet)).doesNotContain("Notas de material");
	}

}
