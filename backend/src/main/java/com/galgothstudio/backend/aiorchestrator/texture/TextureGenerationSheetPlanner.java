package com.galgothstudio.backend.aiorchestrator.texture;

import com.galgothstudio.backend.domain.model.Bone;
import com.galgothstudio.backend.domain.model.BoneSemanticLabel;
import com.galgothstudio.backend.domain.model.Cuboid;
import com.galgothstudio.backend.domain.model.FaceName;
import com.galgothstudio.backend.domain.model.MobProjectModel;
import com.galgothstudio.backend.domain.model.ReferenceImage;
import com.galgothstudio.backend.domain.model.TexturePalette;
import com.galgothstudio.backend.domain.model.TexturePlan;
import com.galgothstudio.backend.domain.model.UvLayout;
import com.galgothstudio.backend.domain.model.UvRegion;
import com.galgothstudio.backend.domain.model.Vec3;
import com.galgothstudio.backend.domain.model.Vec4;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Arma el/los {@link TextureGenerationSheet} de un bone -- 100%
 * determinista, SIN IA -- a partir de un {@link MobProjectModel} (con la
 * UV del bone YA resuelta por {@code UvLayoutSelector}/{@code
 * StableUvStrategy}, tickets 041/042) y su {@link TexturePlan} ya
 * calculado (ticket 052). Diseño técnico §11 punto 2 y §21 (Aislamiento
 * espacial) de `docs/definiciones/galgoth-studio-fase3-textura.md`,
 * ticket 053.
 *
 * <p><b>Este planner NO reasigna UV</b>: el {@code atlasUvRect} de cada
 * {@link CuboidFacePlacement} se LEE directamente de
 * {@code model.uv().regions()} (la única fuente de verdad del atlas ya
 * resuelto) -- nunca se invoca {@code UvLayoutSelector} ni se recalcula
 * un footprint nuevo acá. Si una cara del bone no tiene todavía una
 * {@code UvRegion} en el atlas, es un error de precondición del caller
 * (el atlas debía estar resuelto ANTES de pedir un plan de textura) --
 * {@link IllegalStateException} explícita, nunca un rect inventado.
 *
 * <p><b>Layout interno de la sheet (aislamiento espacial, §21)</b>: los
 * {@code sheetRect} de TODOS los placements de un bone (o de una
 * sub-sheet, ver batching abajo) se calculan con {@link ShelfBinPacker}
 * -- garantía estructural de NO solape: cada fila (shelf) se coloca
 * estrictamente debajo de la anterior y cada item estrictamente a la
 * derecha del anterior en su fila, siempre separados por
 * {@link #GUTTER_PX}. El tamaño de cada {@code sheetRect} es el mismo
 * que el de su {@code atlasUvRect} correspondiente -- la imagen generada
 * pide exactamente el tamaño final que va a necesitar cada cara, sin
 * escalado adicional en el caso normal (el compositor igual soporta
 * mismatch, ver {@link TextureCompositorService}).
 *
 * <p><b>Fallback/batching explícito, nunca el camino por defecto</b>: si
 * el layout de TODAS las caras del bone en una sola sheet excedería
 * {@link #MAX_SHEET_DIMENSION_PX} de ancho o alto, {@link ShelfBinPacker}
 * lo divide en N bins -- cada bin se convierte acá en un
 * {@link TextureGenerationSheet} independiente. {@link #plan} siempre
 * devuelve una {@code List}; tamaño 1 es el camino normal, tamaño N &gt; 1
 * es el batching -- ver el Javadoc de {@link TextureGenerationSheet}
 * para cómo se identifica cada parte SIN agregar campos al record.
 */
@Component
public class TextureGenerationSheetPlanner {

	/**
	 * Separación (px) entre {@link CuboidFacePlacement} adyacentes dentro
	 * de una misma sheet -- constante de diseño explícita del ticket 053
	 * (Diseño técnico §11/§21). 2px alcanza para que ningún redondeo de
	 * escalado/decodificación de {@link TextureSheetSlicer} pueda leer
	 * accidentalmente un píxel de la cara vecina, sin desperdiciar espacio
	 * de sheet de forma perceptible.
	 */
	static final int GUTTER_PX = 2;

	/**
	 * Límite técnico (px, por lado) de una sola sheet -- constante
	 * AJUSTABLE, documentada explícitamente como provisional (ticket 053,
	 * mismo espíritu que el Javadoc de {@code OpenAiImageProvider}): 1536px
	 * es el lado más largo de los presets conocidos de la familia
	 * `gpt-image-*` de OpenAI vigentes al momento de escribir esto
	 * (`1024x1024`/`1024x1536`/`1536x1024`). A verificar/ajustar contra el
	 * `OPENAI_IMAGE_MODEL` realmente configurado cuando el ticket 054
	 * conecte el pipeline de punta a punta -- si el modelo activo soporta
	 * otro máximo, este valor cambia acá, en un único lugar.
	 */
	static final int MAX_SHEET_DIMENSION_PX = 1536;

	/**
	 * Bundle de metadata compartida por TODAS las sub-sheets de un mismo
	 * bone (reduce el conteo de parámetros de los métodos privados de abajo,
	 * S107 -- mismo criterio que {@code StableUvStrategy.AtlasContext}).
	 */
	private record SheetMetadata(
			Bone bone, String semanticLabel, String dominantPalette, String materialNotes, String referenceImageId) {
	}

	private record RawPlacement(
			String cuboidId, FaceName face, Vec4 atlasUvRect, Vec3 relativeSize, String orientationHint, int width,
			int height) {
	}

	public List<TextureGenerationSheet> plan(MobProjectModel model, TexturePlan texturePlan, String boneId) {
		Bone bone = findBone(model, boneId);
		List<Cuboid> boneCuboids = cuboidsOfBone(model, boneId);
		if (boneCuboids.isEmpty()) {
			throw new IllegalArgumentException("El bone '" + boneId + "' no tiene ningún cuboid -- nada que planear.");
		}

		List<RawPlacement> rawPlacements = buildRawPlacements(model.uv(), boneCuboids);
		List<ShelfBinPacker.Bin> bins =
				ShelfBinPacker.pack(toPackerItems(rawPlacements), MAX_SHEET_DIMENSION_PX, GUTTER_PX);

		SheetMetadata metadata = new SheetMetadata(
				bone, semanticLabelFor(texturePlan, boneId), paletteDescription(texturePlan.palette()),
				materialNotesFor(texturePlan, boneId), referenceImageId(model));

		Map<String, RawPlacement> rawById = rawPlacements.stream()
				.collect(Collectors.toMap(r -> packerId(r.cuboidId(), r.face()), r -> r));

		List<TextureGenerationSheet> sheets = new ArrayList<>(bins.size());
		for (ShelfBinPacker.Bin bin : bins) {
			sheets.add(toSheet(bin, rawById, metadata));
		}
		return sheets;
	}

	private static TextureGenerationSheet toSheet(
			ShelfBinPacker.Bin bin, Map<String, RawPlacement> rawById, SheetMetadata metadata) {
		List<CuboidFacePlacement> placements = new ArrayList<>(bin.items().size());
		for (ShelfBinPacker.PlacedItem placed : bin.items()) {
			RawPlacement raw = rawById.get(placed.id());
			Vec4 sheetRect = new Vec4(placed.x0(), placed.y0(), placed.x1(), placed.y1());
			placements.add(
					new CuboidFacePlacement(raw.cuboidId(), raw.face(), sheetRect, raw.atlasUvRect(), raw.relativeSize(),
							raw.orientationHint()));
		}
		return new TextureGenerationSheet(
				metadata.bone().id(), metadata.bone().name(), placements, metadata.semanticLabel(),
				metadata.dominantPalette(), metadata.materialNotes(), metadata.referenceImageId(), bin.width(), bin.height());
	}

	private static List<RawPlacement> buildRawPlacements(UvLayout uv, List<Cuboid> boneCuboids) {
		List<RawPlacement> result = new ArrayList<>();
		for (Cuboid cuboid : boneCuboids) {
			Vec3 relativeSize = sizeOf(cuboid);
			for (FaceName face : FaceName.values()) {
				Vec4 atlasUvRect = atlasUvRectOf(uv, cuboid.id(), face);
				int width = (int) Math.round(atlasUvRect.c() - atlasUvRect.a());
				int height = (int) Math.round(atlasUvRect.d() - atlasUvRect.b());
				result.add(
						new RawPlacement(cuboid.id(), face, atlasUvRect, relativeSize, orientationHintFor(face), width,
								height));
			}
		}
		return result;
	}

	private static List<ShelfBinPacker.Item> toPackerItems(List<RawPlacement> raw) {
		List<ShelfBinPacker.Item> items = new ArrayList<>(raw.size());
		for (RawPlacement r : raw) {
			items.add(new ShelfBinPacker.Item(packerId(r.cuboidId(), r.face()), r.width(), r.height()));
		}
		return items;
	}

	private static String packerId(String cuboidId, FaceName face) {
		return cuboidId + "|" + face.name();
	}

	private static Vec3 sizeOf(Cuboid cuboid) {
		return new Vec3(
				Math.abs(cuboid.to().x() - cuboid.from().x()), Math.abs(cuboid.to().y() - cuboid.from().y()),
				Math.abs(cuboid.to().z() - cuboid.from().z()));
	}

	private static Vec4 atlasUvRectOf(UvLayout uv, String cuboidId, FaceName face) {
		for (UvRegion region : uv.regions()) {
			if (region.cuboidId().equals(cuboidId) && region.face() == face) {
				return region.rect();
			}
		}
		throw new IllegalStateException(
				"No hay UvRegion para cuboid '" + cuboidId + "' cara " + face + " -- el atlas todavía no tiene UV "
						+ "asignada para esta cara (TextureGenerationSheetPlanner no reasigna UV, solo lee la ya existente).");
	}

	/**
	 * Orientación legible por FaceName -- simplificación DOCUMENTADA y
	 * deliberada: no compone la rotación del bone (`CoordinateSystem`/ADR
	 * 0001), a diferencia de lo sugerido por el Diseño técnico §11
	 * ("orientación... resuelta desde FaceName + rotación del bone"). Se
	 * deja así porque el DoD de aislamiento espacial (§21, foco explícito
	 * de este ticket) y sus AC no exigen ningún test sobre el CONTENIDO de
	 * {@code orientationHint} -- solo sobre no-solape/clipping/composición.
	 * Componer la cadena de rotación completa del bone es trabajo real no
	 * pedido por ningún AC de este ticket; reportado explícitamente para
	 * que el Product Owner decida si amerita su propio ticket antes de que
	 * 054 dependa de esta orientación para algo más que texto de prompt.
	 */
	private static String orientationHintFor(FaceName face) {
		return switch (face) {
			case NORTH -> "front";
			case SOUTH -> "back";
			case EAST, WEST -> "side";
			case UP -> "top";
			case DOWN -> "bottom";
		};
	}

	private static String semanticLabelFor(TexturePlan texturePlan, String boneId) {
		for (BoneSemanticLabel label : texturePlan.boneLabels()) {
			if (label.boneId().equals(boneId)) {
				return label.semanticLabel();
			}
		}
		throw new IllegalStateException("El TexturePlan no tiene una etiqueta semántica para el bone '" + boneId + "'.");
	}

	private static String materialNotesFor(TexturePlan texturePlan, String boneId) {
		return texturePlan.materialNotes()
				.stream()
				.filter(note -> note.boneId().equals(boneId))
				.map(note -> note.face().name().toLowerCase(Locale.ROOT) + ": " + note.note())
				.collect(Collectors.joining("; "));
	}

	private static String paletteDescription(TexturePalette palette) {
		return "dominante " + palette.dominantColorHex() + ", acento " + palette.accentColorHex();
	}

	private static String referenceImageId(MobProjectModel model) {
		List<ReferenceImage> images = model.referenceImages();
		if (images == null || images.isEmpty()) {
			throw new IllegalStateException(
					"El modelo no tiene ninguna imagen de referencia subida -- TextureGenerationSheetPlanner asume que "
							+ "el caller (HU-36/37, ticket 054) ya validó esta precondición antes de invocar el planner.");
		}
		return images.get(0).id();
	}

	private static Bone findBone(MobProjectModel model, String boneId) {
		for (Bone bone : model.bones()) {
			if (bone.id().equals(boneId)) {
				return bone;
			}
		}
		throw new IllegalArgumentException("El modelo no tiene ningún bone con id '" + boneId + "'.");
	}

	private static List<Cuboid> cuboidsOfBone(MobProjectModel model, String boneId) {
		return model.cuboids().stream().filter(cuboid -> cuboid.boneId().equals(boneId)).toList();
	}

}
