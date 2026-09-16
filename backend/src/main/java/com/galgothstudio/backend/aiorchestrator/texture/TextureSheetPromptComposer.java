package com.galgothstudio.backend.aiorchestrator.texture;

import com.galgothstudio.backend.domain.model.FaceName;
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
 *
 * <p><b>Alineamiento de coordenadas prompt↔API real (ticket 101, causa
 * raíz documentada en `docs/definiciones/anatomia-por-capas-generacion-mobs.md`,
 * "Texture Generation V2")</b>: antes de este ticket, este composer
 * describía el canvas usando {@code sheet.sheetWidth()/sheetHeight()} --
 * el tamaño ORIGINAL, pequeño, calculado por {@code ShelfBinPacker}. Pero
 * la llamada real a la API (ver {@code ImageGenerationProvider.inflatedSheetSize})
 * puede pedir un canvas hasta ~25x más grande (múltiplo de 16, aspect
 * ratio, pixel budget de área, ver Javadoc de
 * {@code OpenAiImageProvider.inflatedSheetSize}) -- el modelo generador
 * de imagen no tenía forma de reconciliar un canvas "chico" descrito en
 * el prompt con el canvas real, mucho más grande, sobre el que en verdad
 * dibuja: el resultado eran colores en regiones incorrectas y zonas en
 * blanco. Corregido: {@link #compose} recibe el tamaño REAL inflado
 * ({@code inflatedWidth}/{@code inflatedHeight}) y describe el canvas
 * Y cada coordenada de grilla en ESE sistema -- el mismo que
 * {@link TextureSheetSlicer} usa después para recortar (ver su Javadoc),
 * sin doble conversión.
 *
 * <p><b>Nota de material por línea, no bloque compartido (decisión T2 del
 * documento de definición, ticket 101)</b>: cada línea de coordenadas
 * lleva adjunta su propia nota de material (vía {@link TextureGenerationPlan}),
 * reemplazando el bloque {@code "Notas de material: ..."} único que
 * describía TODAS las caras del bone de forma genérica.
 */
public final class TextureSheetPromptComposer {

	private TextureSheetPromptComposer() {
	}

	public static String compose(TextureGenerationSheet sheet, int inflatedWidth, int inflatedHeight, TextureGenerationPlan plan) {
		StringBuilder sb = new StringBuilder();
		appendHeader(sb, sheet, inflatedWidth, inflatedHeight);
		appendGrid(sb, sheet, inflatedWidth, inflatedHeight, plan);
		return sb.toString();
	}

	private static void appendHeader(StringBuilder sb, TextureGenerationSheet sheet, int inflatedWidth, int inflatedHeight) {
		sb.append("Generá una única imagen de ")
				.append(inflatedWidth)
				.append('x')
				.append(inflatedHeight)
				.append(" píxeles para la parte \"")
				.append(sheet.boneName())
				.append("\" (")
				.append(sheet.semanticLabel())
				.append(") de un mob de Minecraft.\n")
				.append("Paleta: ")
				.append(sheet.dominantPalette())
				.append(".\n");
	}

	/**
	 * <b>Ticket 113 -- por qué el prompt ya NO pide reservar el margen</b>: el
	 * texto anterior pedía "regiones separadas por un margen de Xpx sin
	 * contenido... sin invadir el margen entre ellas", y el modelo cumplía de
	 * la forma más prudente posible: reservaba ese margen DENTRO de cada
	 * rectángulo y lo pintaba oscuro. Medido sobre un atlas real
	 * (`Carcomido v3`): bandas totalmente negras de 1-2 px en el borde de
	 * buena parte de las caras (12 de 40 a la izquierda, 16 arriba, 20
	 * abajo), visibles en el render como costuras negras entre cuboids.
	 *
	 * <p>El gutter sigue existiendo en el layout de {@link ShelfBinPacker} y
	 * {@link TextureSheetSlicer} sigue recortando por el {@code sheetRect}
	 * exacto -- o sea que el bleed hacia afuera YA se descarta solo (diseño
	 * del ticket 053, "cualquier bleed... queda simplemente fuera de la
	 * subimagen pedida"). Pedirle además al modelo que lo reserve era pedir
	 * dos veces lo mismo, y encima le costaba píxeles útiles de la cara.
	 * Ahora se le pide lo contrario: llenar cada rectángulo de borde a borde.
	 *
	 * <p>El AC del ticket 053 ("el prompt incluye un background/mask
	 * determinista que delimita visualmente cada sheetRect") se sigue
	 * cumpliendo: la delimitación son las coordenadas exactas de cada región,
	 * que siguen listándose una por línea.
	 */
	private static void appendGrid(StringBuilder sb, TextureGenerationSheet sheet, int inflatedWidth, int inflatedHeight, TextureGenerationPlan plan) {
		double scaleX = inflatedWidth / (double) sheet.sheetWidth();
		double scaleY = inflatedHeight / (double) sheet.sheetHeight();
		sb.append("\nLa imagen se divide en ")
				.append(sheet.placements().size())
				.append(" regiones fijas, uno por cara. Pintá CADA región COMPLETA, de borde a borde: el contenido "
						+ "tiene que llegar hasta los cuatro lados de su rectángulo [x0,y0]-[x1,y1]. NO dibujes "
						+ "marcos, bordes oscuros, contornos ni márgenes internos alrededor de ninguna región -- una "
						+ "franja vacía u oscura pegada al borde arruina la cara al aplicarse sobre el modelo 3D. "
						+ "Lo que quede FUERA de los rectángulos se descarta, así que no hace falta que cuides el "
						+ "espacio entre ellos:\n");
		for (CuboidFacePlacement placement : sheet.placements()) {
			appendPlacementLine(sb, placement, scaleX, scaleY, plan);
		}
	}

	private static void appendPlacementLine(StringBuilder sb, CuboidFacePlacement placement, double scaleX, double scaleY, TextureGenerationPlan plan) {
		Vec4 rect = placement.sheetRect();
		TextureGenerationPlan.Entry entry = planEntryFor(placement, plan);
		sb.append("- Cuboid ").append(placement.cuboidId());
		appendIfPresent(sb, entry.semanticPart(), " (", ")");
		sb.append(", cara ")
				.append(placement.face().name().toLowerCase(Locale.ROOT))
				.append(" (")
				.append(placement.orientationHint())
				.append("): [")
				.append(Math.round(rect.a() * scaleX))
				.append(',')
				.append(Math.round(rect.b() * scaleY))
				.append("]-[")
				.append(Math.round(rect.c() * scaleX))
				.append(',')
				.append(Math.round(rect.d() * scaleY))
				.append(']');
		appendIfPresent(sb, entry.materialNote(), " -- material: ", "");
		sb.append('\n');
	}

	private static TextureGenerationPlan.Entry planEntryFor(CuboidFacePlacement placement, TextureGenerationPlan plan) {
		FaceName face = placement.face();
		return plan.find(placement.cuboidId(), face)
				.orElseThrow(() -> new IllegalStateException(
						"TextureGenerationPlan no tiene una entrada para cuboid '" + placement.cuboidId() + "' cara " + face
								+ " -- se construye 1:1 desde los mismos placements de esta sheet (ver TextureGenerationPlan#forSheet), "
								+ "nunca debería faltar una entrada."));
	}

	private static void appendIfPresent(StringBuilder sb, String value, String prefix, String suffix) {
		if (value != null && !value.isBlank()) {
			sb.append(prefix).append(value).append(suffix);
		}
	}

}
