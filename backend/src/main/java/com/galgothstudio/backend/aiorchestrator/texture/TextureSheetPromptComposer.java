package com.galgothstudio.backend.aiorchestrator.texture;

import com.galgothstudio.backend.domain.model.Vec4;
import java.util.Locale;

/**
 * Compone el prompt determinista para
 * {@code ImageGenerationProvider.generateTextureSheet(...)} a partir de
 * un {@link TextureGenerationSheet} ya armado por
 * {@link TextureGenerationSheetPlanner} -- Diseño técnico §11 punto 3 y
 * §21 (Aislamiento espacial) de
 * `docs/definiciones/galgoth-studio-fase3-textura.md`, ticket 053, AC:
 * "el prompt... incluye un background/mask determinista que delimita
 * visualmente cada sheetRect".
 *
 * <p><b>Nota de contrato, reportada explícitamente</b>: el
 * {@code ImageGenerationProvider.TextureGenerationSheetRequest} real
 * (ticket 051) NO tiene un campo de imagen de máscara separado -- solo
 * {@code prompt}/{@code referenceImageBytes}/{@code sheetWidth}/
 * {@code sheetHeight}/{@code style}. El "background/mask determinista"
 * de este punto se materializa como una descripción TEXTUAL determinista
 * de la grilla de rectángulos dentro del prompt (mismo mecanismo que ya
 * usa {@code OpenAiImageProvider} para plegar {@code style} como texto en
 * vez de un canal de imagen separado) -- nunca una máscara bitmap real.
 * Si un futuro proveedor de imagen soporta un canal de máscara real, este
 * composer es el punto de extensión natural.
 *
 * <p>{@code style} (1 de las 4 opciones del selector de usuario) NO es
 * responsabilidad de este composer -- viaja aparte en
 * {@code TextureGenerationSheetRequest.style()} y ya lo pliega
 * {@code OpenAiImageProvider} al final del prompt compuesto; mezclar
 * ambos acá duplicaría esa responsabilidad. Elegir el estilo es decisión
 * de usuario orquestada por 054, fuera del alcance de este ticket
 * (100% determinista, sin IA, sin estado de sesión de usuario).
 */
public final class TextureSheetPromptComposer {

	private TextureSheetPromptComposer() {
	}

	public static String compose(TextureGenerationSheet sheet) {
		StringBuilder sb = new StringBuilder();
		appendHeader(sb, sheet);
		appendGrid(sb, sheet);
		return sb.toString();
	}

	private static void appendHeader(StringBuilder sb, TextureGenerationSheet sheet) {
		sb.append("Generá una única imagen de ")
				.append(sheet.sheetWidth())
				.append('x')
				.append(sheet.sheetHeight())
				.append(" píxeles para la parte \"")
				.append(sheet.boneName())
				.append("\" (")
				.append(sheet.semanticLabel())
				.append(") de un mob de Minecraft.\n")
				.append("Paleta: ")
				.append(sheet.dominantPalette())
				.append(".\n");
		if (sheet.materialNotes() != null && !sheet.materialNotes().isBlank()) {
			sb.append("Notas de material: ").append(sheet.materialNotes()).append(".\n");
		}
	}

	private static void appendGrid(StringBuilder sb, TextureGenerationSheet sheet) {
		sb.append("\nLa imagen se divide en ")
				.append(sheet.placements().size())
				.append(" regiones fijas, separadas por un margen de ")
				.append(TextureGenerationSheetPlanner.GUTTER_PX)
				.append("px sin contenido. Respetá EXACTAMENTE estos límites -- el contenido de cada región debe "
						+ "quedar contenido dentro de su rectángulo [x0,y0]-[x1,y1], sin invadir el de las demás "
						+ "regiones ni el margen entre ellas:\n");
		for (CuboidFacePlacement placement : sheet.placements()) {
			appendPlacementLine(sb, placement);
		}
	}

	private static void appendPlacementLine(StringBuilder sb, CuboidFacePlacement placement) {
		Vec4 rect = placement.sheetRect();
		sb.append("- Cuboid ")
				.append(placement.cuboidId())
				.append(", cara ")
				.append(placement.face().name().toLowerCase(Locale.ROOT))
				.append(" (")
				.append(placement.orientationHint())
				.append("): [")
				.append((int) rect.a())
				.append(',')
				.append((int) rect.b())
				.append("]-[")
				.append((int) rect.c())
				.append(',')
				.append((int) rect.d())
				.append("]\n");
	}

}
