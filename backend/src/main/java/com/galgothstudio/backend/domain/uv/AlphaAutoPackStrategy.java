package com.galgothstudio.backend.domain.uv;

import com.galgothstudio.backend.domain.model.Cuboid;
import com.galgothstudio.backend.domain.model.CuboidFaces;
import com.galgothstudio.backend.domain.model.FaceName;
import com.galgothstudio.backend.domain.model.UvRegion;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Única implementación de {@link UvLayoutStrategy} para reflow completo --
 * *shelf-packing* determinista tipo caja de Minecraft, `docs/definiciones/galgoth-studio-mvp.md`
 * (Diseño técnico §6): objetivo es VALIDEZ, no densidad de atlas (no hay
 * textura real que optimizar todavía, solo placeholder).
 *
 * <p>La matemática de box-unwrap/footprint (desenvolvimiento de caja de
 * Minecraft) vive en {@link BoxUvMath} -- extraída en el ticket 041 para
 * que {@link StableUvStrategy} la reutilice sin duplicarla ni divergir del
 * cálculo ya verificado (ticket 006/007, fixture compartida
 * `contracts/fixtures/uv-layout-fixture.json`). Esta clase solo aporta el
 * *shelf-packing* (reflow completo, sin memoria del layout anterior).
 *
 * <p>Empaqueta cada footprint en la siguiente posición libre del atlas,
 * fila por fila (izquierda a derecha, de arriba hacia abajo), en el mismo
 * orden que trae la lista de cuboids de entrada -- determinista: la misma
 * lista de entrada siempre produce el mismo layout (AC de determinismo
 * del ticket 006). Si el conjunto no cabe en {@code textureWidth}x{@code textureHeight},
 * lanza {@link UvAtlasOverflowException} con las dimensiones mínimas que
 * sí lo harían caber -- el atlas nunca crece en silencio.
 *
 * <p>{@code @Component} agregado en el ticket 028 -- primer consumidor
 * Spring-managed real ({@code GeometryPlannerService}, que inyecta
 * {@link UvLayoutStrategy} por interfaz); hasta entonces solo se
 * instanciaba a mano en tests/exportadores.
 */
@Component
public final class AlphaAutoPackStrategy implements UvLayoutStrategy {

	private record Placement(int x, int y) {
	}

	/** Paquete-visible (no {@code private}) desde el ticket 042: {@link AtlasResolutionCalculator} lee {@code totalHeight()}/{@code maxRowWidth()} vía {@link #packedBoundsOf}. */
	record PackResult(List<Placement> placements, int totalHeight, int maxRowWidth) {
	}

	@Override
	public Result layout(List<Cuboid> cuboids, int textureWidth, int textureHeight) {
		return layout(cuboids, textureWidth, textureHeight, TexelDensity.X1);
	}

	/**
	 * Ticket 042, Diseño técnico §7: variante aditiva que empaqueta a una
	 * {@link TexelDensity} explícita -- NO forma parte del contrato
	 * {@link UvLayoutStrategy} (que no tiene noción de densidad; ningún
	 * {@code MobProjectModel}/{@code UvLayout} persiste todavía qué
	 * densidad está vigente, fuera de alcance de este ticket). Único
	 * consumidor real: {@link TexelDensityUpgrade} (upgrade explícito
	 * x1→x2 ANTES de contenido {@code PAINTED}). La sobrecarga de 3
	 * argumentos de arriba (contrato {@link UvLayoutStrategy}, tickets
	 * 006/007/041) es exactamente equivalente a llamar esta con
	 * {@link TexelDensity#X1} -- CERO cambio de comportamiento para el
	 * código ya existente.
	 */
	public Result layout(List<Cuboid> cuboids, int textureWidth, int textureHeight, TexelDensity density) {
		List<BoxUvMath.Footprint> footprints = footprintsOf(cuboids, density);

		PackResult attempt = packWithinWidth(footprints, textureWidth);
		if (attempt.totalHeight() > textureHeight || attempt.maxRowWidth() > textureWidth) {
			int requiredWidth = Math.max(
					textureWidth, footprints.stream().mapToInt(BoxUvMath.Footprint::width).max().orElse(textureWidth));
			PackResult fallback = packWithinWidth(footprints, requiredWidth);
			int requiredHeight = Math.max(textureHeight, fallback.totalHeight());
			throw new UvAtlasOverflowException(textureWidth, textureHeight, requiredWidth, requiredHeight);
		}

		List<Cuboid> updatedCuboids = new ArrayList<>(cuboids.size());
		List<UvRegion> regions = new ArrayList<>();
		for (int i = 0; i < cuboids.size(); i++) {
			Cuboid cuboid = cuboids.get(i);
			Placement placement = attempt.placements().get(i);
			CuboidFaces faces = BoxUvMath.boxUnwrapFaces(cuboid, placement.x(), placement.y(), density);

			updatedCuboids.add(
					new Cuboid(
							cuboid.id(), cuboid.name(), cuboid.boneId(), cuboid.from(), cuboid.to(), cuboid.origin(),
							cuboid.rotation(), faces));
			for (FaceName faceName : FaceName.values()) {
				regions.add(new UvRegion(cuboid.id(), faceName, BoxUvMath.faceOf(faces, faceName).uv()));
			}
		}

		return new Result(updatedCuboids, regions);
	}

	private static List<BoxUvMath.Footprint> footprintsOf(List<Cuboid> cuboids, TexelDensity density) {
		List<BoxUvMath.Footprint> footprints = new ArrayList<>(cuboids.size());
		for (Cuboid cuboid : cuboids) {
			footprints.add(BoxUvMath.footprintOf(cuboid, density));
		}
		return footprints;
	}

	/**
	 * Caja envolvente (ancho/alto totales) del shelf-packing de
	 * {@code footprints}, empaquetando con el ancho mínimo con el que CADA
	 * footprint individual entra en su propia fila (el footprint más ancho
	 * de la lista) -- SIN calcular placements. Ticket 042: expuesto
	 * paquete-visible para que {@link AtlasResolutionCalculator} decida el
	 * tamaño de atlas ANTES de que exista ninguno, reusando el MISMO
	 * algoritmo de {@link #packWithinWidth} en vez de duplicarlo.
	 */
	static PackResult packedBoundsOf(List<Cuboid> cuboids, TexelDensity density) {
		List<BoxUvMath.Footprint> footprints = footprintsOf(cuboids, density);
		int narrowestSafeWidth = footprints.stream().mapToInt(BoxUvMath.Footprint::width).max().orElseThrow();
		return packWithinWidth(footprints, narrowestSafeWidth);
	}

	/** Shelf-packing sin límite de alto -- envuelve de fila cuando se excede {@code width}. */
	private static PackResult packWithinWidth(List<BoxUvMath.Footprint> footprints, int width) {
		List<Placement> placements = new ArrayList<>(footprints.size());
		int cursorX = 0;
		int cursorY = 0;
		int rowHeight = 0;
		int maxRowWidth = 0;
		for (BoxUvMath.Footprint footprint : footprints) {
			if (cursorX > 0 && cursorX + footprint.width() > width) {
				maxRowWidth = Math.max(maxRowWidth, cursorX);
				cursorX = 0;
				cursorY += rowHeight;
				rowHeight = 0;
			}
			placements.add(new Placement(cursorX, cursorY));
			cursorX += footprint.width();
			rowHeight = Math.max(rowHeight, footprint.height());
		}
		maxRowWidth = Math.max(maxRowWidth, cursorX);
		return new PackResult(placements, cursorY + rowHeight, maxRowWidth);
	}

}
