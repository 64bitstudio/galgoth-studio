package com.galgothstudio.backend.aiorchestrator.texture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.galgothstudio.backend.domain.model.FaceName;
import com.galgothstudio.backend.domain.model.Vec3;
import com.galgothstudio.backend.domain.model.Vec4;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * `TextureSheetPromptComposer` -- ticket 053, Diseño técnico §11 punto 3
 * y §21, AC: "el prompt... incluye un background/mask determinista que
 * delimita visualmente cada sheetRect". Ticket 101: alineamiento de
 * coordenadas al tamaño REAL inflado + nota de material por línea (vía
 * {@link TextureGenerationPlan}), no un bloque compartido.
 */
class TextureSheetPromptComposerTest {

	private static final CuboidFacePlacement NORTH = new CuboidFacePlacement(
			"cube-a", FaceName.NORTH, new Vec4(0, 0, 8, 8), new Vec4(20, 20, 28, 28), new Vec3(4, 4, 4), "front");
	private static final CuboidFacePlacement UP = new CuboidFacePlacement(
			"cube-a", FaceName.UP, new Vec4(10, 0, 18, 8), new Vec4(0, 0, 8, 8), new Vec3(4, 4, 4), "top");

	private static TextureGenerationSheet sheet() {
		return new TextureGenerationSheet(
				"head-1", "head", List.of(NORTH, UP), "cabeza", "dominante #111111, acento #222222", "north: cuero desgastado",
				"ref-1", 20, 10);
	}

	private static TextureGenerationPlan planFor(TextureGenerationSheet sheet, String semanticPart, String materialNote) {
		List<TextureGenerationPlan.Entry> entries = sheet.placements()
				.stream()
				.map(p -> new TextureGenerationPlan.Entry(p.cuboidId(), p.face(), semanticPart, materialNote, p.atlasUvRect()))
				.toList();
		return new TextureGenerationPlan(entries);
	}

	@Test
	void elPromptIncluyeElTamanoRealInfladoDeLaImagenYLaEtiquetaSemantica() {
		TextureGenerationSheet sheet = sheet();
		String prompt = TextureSheetPromptComposer.compose(sheet, 40, 20, planFor(sheet, null, null));

		// El tamaño ORIGINAL del sheet (20x10) NO debe aparecer como tamaño de
		// canvas -- el modelo generador debe recibir el tamaño REAL inflado
		// (40x20), nunca el pequeño (ver Javadoc de la clase, ticket 101).
		assertThat(prompt).contains("40x20").doesNotContain("Generá una única imagen de 20x10").contains("head").contains("cabeza");
	}

	@Test
	void elPromptIncluyeLaPaleta() {
		TextureGenerationSheet sheet = sheet();
		String prompt = TextureSheetPromptComposer.compose(sheet, 20, 10, planFor(sheet, null, null));

		assertThat(prompt).contains("#111111").contains("#222222");
	}

	@Test
	void sinInflado_lasCoordenadasQuedanIgualQueElSheetRectOriginal() {
		TextureGenerationSheet sheet = sheet();
		// inflatedWidth/Height == sheetWidth/Height -- escala 1:1, mismo caso
		// que un proveedor que no infla nada (ej. MockImageProvider).
		String prompt = TextureSheetPromptComposer.compose(sheet, 20, 10, planFor(sheet, null, null));

		assertThat(prompt).contains("[0,0]-[8,8]").contains("[10,0]-[18,8]").contains("front").contains("top");
	}

	@Test
	void conInflado2x_lasCoordenadasDeGrillaSeEscalanAlMismoFactor_AC_alineamientoPrompt_API() {
		TextureGenerationSheet sheet = sheet();
		// Tamaño real pedido a la API: el DOBLE del sheet planeado en cada eje
		// -- las coordenadas de grilla deben describir el canvas real (40x20),
		// no el original (20x10): [0,0]-[8,8] -> [0,0]-[16,16].
		String prompt = TextureSheetPromptComposer.compose(sheet, 40, 20, planFor(sheet, null, null));

		assertThat(prompt).contains("[0,0]-[16,16]") // "north" escalado x2
				.contains("[20,0]-[36,16]"); // "up" escalado x2
	}

	@Test
	void cadaLineaDeCoordenadasLlevaAdjuntoElSemanticPartYLaNotaDeMaterialDeSuPropiaEntrada_AC_T2() {
		TextureGenerationSheet sheet = sheet();
		TextureGenerationPlan plan = new TextureGenerationPlan(
				List.of(
						new TextureGenerationPlan.Entry("cube-a", FaceName.NORTH, "TORSO", "cuero desgastado con parches", NORTH.atlasUvRect()),
						new TextureGenerationPlan.Entry("cube-a", FaceName.UP, "HEAD_TOP", "piel agrietada", UP.atlasUvRect())));

		String prompt = TextureSheetPromptComposer.compose(sheet, 20, 10, plan);

		// El bloque compartido de notas del composer anterior a 101 ya no existe.
		assertThat(prompt).contains("TORSO").contains("material: cuero desgastado con parches").contains("HEAD_TOP")
				.contains("material: piel agrietada").doesNotContain("Notas de material:");
	}

	@Test
	void sinSemanticPartNiNotaDeMaterialEnLaEntrada_noAgregaParentesisNiSufijoVacio() {
		TextureGenerationSheet sheet = sheet();
		String prompt = TextureSheetPromptComposer.compose(sheet, 20, 10, planFor(sheet, null, null));

		assertThat(prompt).doesNotContain("()").doesNotContain("-- material:");
	}

	@Test
	void esDeterminista_mismosArgumentos_mismoPrompt() {
		TextureGenerationSheet sheet = sheet();
		TextureGenerationPlan plan = planFor(sheet, "TORSO", "cuero desgastado");

		assertThat(TextureSheetPromptComposer.compose(sheet, 40, 20, plan)).isEqualTo(TextureSheetPromptComposer.compose(sheet, 40, 20, plan));
	}

	@Test
	void siElPlanNoTieneUnaEntradaParaUnPlacementDeLaSheet_fallaExplicito_nuncaOmiteLaLineaEnSilencio() {
		TextureGenerationSheet sheet = sheet();
		TextureGenerationPlan planIncompleto = new TextureGenerationPlan(
				List.of(new TextureGenerationPlan.Entry("cube-a", FaceName.NORTH, null, null, NORTH.atlasUvRect()))); // falta la entrada de UP

		assertThatThrownBy(() -> TextureSheetPromptComposer.compose(sheet, 20, 10, planIncompleto)).isInstanceOf(IllegalStateException.class);
	}

	/**
	 * Ticket 113 -- el prompt ya NO le pide al modelo reservar el margen
	 * entre regiones. Pedírselo hacía que lo reservara DENTRO del rectángulo
	 * y lo pintara oscuro: bandas negras de 1-2 px medidas en un atlas real,
	 * visibles como costuras en el render. El gutter sigue en el layout y el
	 * slicer sigue recortando exacto, así que el bleed hacia afuera ya se
	 * descarta solo -- no hace falta pedirlo dos veces.
	 */
	@Test
	void elPromptPideLlenarCadaRegionDeBordeABorde_yNoPideReservarMargen_AC() {
		TextureGenerationSheet sheet = sheet();

		String prompt = TextureSheetPromptComposer.compose(sheet, 20, 10, planFor(sheet, null, null));

		assertThat(prompt)
				.contains("de borde a borde")
				.contains("los cuatro lados")
				.doesNotContain("sin contenido")
				.doesNotContain("margen entre ellas");
	}

}
