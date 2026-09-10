package com.galgothstudio.backend.domain.uv;

import com.galgothstudio.backend.domain.model.Cuboid;
import com.galgothstudio.backend.domain.model.CuboidFaces;
import com.galgothstudio.backend.domain.model.FaceName;
import com.galgothstudio.backend.domain.model.UvLayout;
import com.galgothstudio.backend.domain.model.UvRegion;
import com.galgothstudio.backend.domain.model.UvRegionStatus;
import com.galgothstudio.backend.domain.model.UvReservation;
import com.galgothstudio.backend.domain.model.UvReservationReason;
import com.galgothstudio.backend.domain.model.Vec4;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Estrategia de UV estable (ticket 041, Diseño técnico §2 de
 * `docs/definiciones/galgoth-studio-fase3-textura.md`): a diferencia de
 * {@link AlphaAutoPackStrategy} (reflow completo de TODOS los cuboids en
 * cada llamada), esta estrategia NUNCA reposiciona un cuboid que no
 * cambió -- solo actúa sobre los 3 casos que realmente afectan el atlas:
 *
 * <ul>
 * <li><b>Add</b> (cuboid nuevo, no está en {@code previousLayout}): se
 * busca un hueco en el "espacio verdaderamente libre" del atlas (ver
 * {@link #findFreeSpot}); si no cabe, {@link UvAtlasOverflowException} --
 * nunca crece el atlas ni reempaqueta lo existente.</li>
 * <li><b>Resize</b> (cuboid existente cuyo footprint cambió): si alguna de
 * las caras afectadas está {@code PAINTED}, se exige confirmación
 * explícita ({@link PaintedRegionResizeConfirmationRequiredException}) --
 * sin mutar nada. Confirmado, se reserva el rect viejo de cada cara
 * {@code PAINTED} afectada ({@link UvReservation}, {@code RESIZE_ABANDONED})
 * y la cara se reempaqueta en espacio libre, quedando {@code UNPAINTED}.</li>
 * <li><b>Delete</b> (cuboid en {@code previousLayout} que ya no está en la
 * lista de entrada): sus 6 filas de {@code UvRegion} pasan a
 * {@code ORPHAN} en el sitio -- nunca se crea una reserva (el {@code rect}
 * original sigue vivo en su propia fila, no hace falta un tombstone aparte).</li>
 * </ul>
 *
 * <p>Reutiliza la matemática de box-unwrap/footprint de {@link BoxUvMath}
 * -- el mismo cálculo ya verificado de {@link AlphaAutoPackStrategy}, sin
 * duplicarlo ni divergir.
 */
@Component
public final class StableUvStrategy implements UvLayoutStrategy {

	@Override
	public Result layout(List<Cuboid> cuboids, int textureWidth, int textureHeight) {
		return layout(cuboids, textureWidth, textureHeight, new UvLayout(textureWidth, textureHeight, List.of(), List.of()));
	}

	@Override
	public Result layout(List<Cuboid> cuboids, int textureWidth, int textureHeight, UvLayout previousLayout) {
		return layout(cuboids, textureWidth, textureHeight, previousLayout, false);
	}

	/**
	 * Sobrecarga de 5 argumentos de {@link UvLayoutStrategy} (ticket 043 --
	 * el ticket 041 la introdujo aquí ANTES de que existiera la sobrecarga
	 * formal en la interfaz, ahora {@code @Override} real): el flag
	 * explícito de confirmación de pérdida de pintura del caso "Resize"
	 * (Diseño técnico §2) llega hasta acá desde
	 * {@code POST /api/mobs/{mobId}/geometry/apply}
	 * (`project/geometry/MobGeometryApplyService`), vía
	 * {@link UvLayoutSelector} y el motor de geometría -- este es el punto
	 * de entrada real del caso "confirmado", no solo un método de test.
	 */
	@Override
	public Result layout(
			List<Cuboid> cuboids, int textureWidth, int textureHeight, UvLayout previousLayout, boolean confirmPaintLoss) {
		Map<String, Map<FaceName, UvRegion>> oldByCuboid = groupByCuboid(previousLayout.regions());
		Set<String> currentIds = new LinkedHashSet<>();
		for (Cuboid cuboid : cuboids) {
			currentIds.add(cuboid.id());
		}

		List<Vec4> occupied = new ArrayList<>();
		for (UvRegion region : previousLayout.regions()) {
			occupied.add(region.rect());
		}
		for (UvReservation reservation : previousLayout.reservations()) {
			occupied.add(reservation.rect());
		}

		List<UvRegion> regions = new ArrayList<>();
		List<UvReservation> reservations = new ArrayList<>(previousLayout.reservations());
		List<Cuboid> outputCuboids = new ArrayList<>(cuboids.size());
		AtlasContext context = new AtlasContext(textureWidth, textureHeight, occupied, regions, reservations);

		markDeletedAsOrphan(oldByCuboid, currentIds, regions);

		for (Cuboid cuboid : cuboids) {
			Map<FaceName, UvRegion> oldFaces = oldByCuboid.get(cuboid.id());
			Cuboid placed = oldFaces == null
					? handleAdd(cuboid, context)
					: handleExisting(cuboid, oldFaces, context, confirmPaintLoss);
			outputCuboids.add(placed);
		}

		return new Result(outputCuboids, regions, reservations);
	}

	/**
	 * Agrupa los 3 acumuladores mutables de una corrida de {@link #layout}
	 * junto con las dimensiones del atlas -- reduce el conteo de
	 * parámetros de los métodos privados de abajo (S107) sin cambiar la
	 * semántica: siguen siendo las mismas listas mutadas por referencia.
	 */
	private record AtlasContext(
			int textureWidth, int textureHeight, List<Vec4> occupied, List<UvRegion> regions,
			List<UvReservation> reservations) {
	}

	/** Delete: cuboid vivo en el layout anterior, ausente de la lista actual -- sus 6 filas pasan a ORPHAN en el sitio. */
	private static void markDeletedAsOrphan(
			Map<String, Map<FaceName, UvRegion>> oldByCuboid, Set<String> currentIds, List<UvRegion> regions) {
		for (Map.Entry<String, Map<FaceName, UvRegion>> entry : oldByCuboid.entrySet()) {
			if (!currentIds.contains(entry.getKey())) {
				for (UvRegion oldRegion : entry.getValue().values()) {
					regions.add(new UvRegion(oldRegion.cuboidId(), oldRegion.face(), oldRegion.rect(), UvRegionStatus.ORPHAN));
				}
			}
		}
	}

	/** Add: cuboid nuevo, no está en el layout anterior -- busca hueco en espacio verdaderamente libre. */
	private static Cuboid handleAdd(Cuboid cuboid, AtlasContext context) {
		BoxUvMath.Footprint footprint = BoxUvMath.footprintOf(cuboid);
		Vec4 spot = findFreeSpot(footprint, context.textureWidth(), context.textureHeight(), context.occupied())
				.orElseThrow(() -> overflowFor(footprint, context.textureWidth(), context.textureHeight()));
		CuboidFaces faces = BoxUvMath.boxUnwrapFaces(cuboid, (int) spot.a(), (int) spot.b());
		for (FaceName faceName : FaceName.values()) {
			Vec4 rect = BoxUvMath.faceOf(faces, faceName).uv();
			context.regions().add(new UvRegion(cuboid.id(), faceName, rect, UvRegionStatus.UNPAINTED));
			context.occupied().add(rect);
		}
		return withFaces(cuboid, faces);
	}

	/**
	 * Cuboid ya presente en el layout anterior: recalcula el footprint EN
	 * EL MISMO offset que ya tenía -- si da exactamente los mismos 6
	 * rects, nada cambió (ni siquiera hace falta decidir "fue tocado por
	 * esta operación o no": la geometría es la fuente de verdad). Si
	 * cambió, delega en {@link #handleResize}.
	 */
	private static Cuboid handleExisting(
			Cuboid cuboid, Map<FaceName, UvRegion> oldFaces, AtlasContext context, boolean confirmPaintLoss) {
		int offsetX = (int) Math.round(oldFaces.get(FaceName.WEST).rect().a());
		int offsetY = (int) Math.round(oldFaces.get(FaceName.UP).rect().b());
		CuboidFaces recomputed = BoxUvMath.boxUnwrapFaces(cuboid, offsetX, offsetY);

		boolean footprintChanged = false;
		for (FaceName faceName : FaceName.values()) {
			if (!BoxUvMath.faceOf(recomputed, faceName).uv().equals(oldFaces.get(faceName).rect())) {
				footprintChanged = true;
				break;
			}
		}

		if (!footprintChanged) {
			// -- Sin cambio de footprint: se preserva tal cual, incluido el status --
			for (FaceName faceName : FaceName.values()) {
				context.regions().add(oldFaces.get(faceName));
			}
			return withFaces(cuboid, recomputed);
		}

		return handleResize(cuboid, oldFaces, context, confirmPaintLoss);
	}

	/**
	 * Resize con footprint cambiado -- las 6 caras comparten un mismo
	 * anchor de box-unwrap (BoxUvMath), así que el bloque ENTERO se
	 * reubica. Las caras "afectadas" para confirmación/reserva son TODAS
	 * las PAINTED del cuboid, no solo el subconjunto que cambió de rect.
	 */
	private static Cuboid handleResize(
			Cuboid cuboid, Map<FaceName, UvRegion> oldFaces, AtlasContext context, boolean confirmPaintLoss) {
		List<PaintedRegionResizeConfirmationRequiredException.AffectedFace> paintedAffected = new ArrayList<>();
		for (FaceName faceName : FaceName.values()) {
			if (oldFaces.get(faceName).status() == UvRegionStatus.PAINTED) {
				paintedAffected.add(new PaintedRegionResizeConfirmationRequiredException.AffectedFace(cuboid.id(), faceName));
			}
		}

		if (!paintedAffected.isEmpty() && !confirmPaintLoss) {
			throw new PaintedRegionResizeConfirmationRequiredException(paintedAffected);
		}

		BoxUvMath.Footprint footprint = BoxUvMath.footprintOf(cuboid);
		Vec4 spot = findFreeSpot(footprint, context.textureWidth(), context.textureHeight(), context.occupied())
				.orElseThrow(() -> overflowFor(footprint, context.textureWidth(), context.textureHeight()));
		CuboidFaces newFaces = BoxUvMath.boxUnwrapFaces(cuboid, (int) spot.a(), (int) spot.b());

		Set<FaceName> paintedAffectedNames = new LinkedHashSet<>();
		for (var affected : paintedAffected) {
			paintedAffectedNames.add(affected.face());
		}
		for (FaceName faceName : FaceName.values()) {
			Vec4 newRect = BoxUvMath.faceOf(newFaces, faceName).uv();
			if (paintedAffectedNames.contains(faceName)) {
				context.reservations().add(
						new UvReservation(
								UUID.randomUUID().toString(), oldFaces.get(faceName).rect(),
								UvReservationReason.RESIZE_ABANDONED, cuboid.id(), faceName));
			}
			context.regions().add(new UvRegion(cuboid.id(), faceName, newRect, UvRegionStatus.UNPAINTED));
			context.occupied().add(newRect);
		}
		return withFaces(cuboid, newFaces);
	}

	private static Map<String, Map<FaceName, UvRegion>> groupByCuboid(List<UvRegion> regions) {
		Map<String, Map<FaceName, UvRegion>> byCuboid = new java.util.LinkedHashMap<>();
		for (UvRegion region : regions) {
			byCuboid.computeIfAbsent(region.cuboidId(), id -> new EnumMap<>(FaceName.class)).put(region.face(), region);
		}
		return byCuboid;
	}

	private static Cuboid withFaces(Cuboid cuboid, CuboidFaces faces) {
		return new Cuboid(
				cuboid.id(), cuboid.name(), cuboid.boneId(), cuboid.from(), cuboid.to(), cuboid.origin(),
				cuboid.rotation(), faces);
	}

	/**
	 * "Espacio verdaderamente libre" (Diseño técnico §2): atlas completo
	 * MENOS la unión de TODOS los {@code rect} de {@code occupied}
	 * (regiones de cualquier status + reservas). Scan determinista fila
	 * por fila desde {@code (0,0)} -- misma filosofía determinista que
	 * {@link AlphaAutoPackStrategy}.
	 */
	private static Optional<Vec4> findFreeSpot(
			BoxUvMath.Footprint footprint, int atlasWidth, int atlasHeight, List<Vec4> occupied) {
		if (footprint.width() > atlasWidth || footprint.height() > atlasHeight) {
			return Optional.empty();
		}
		for (int y = 0; y + footprint.height() <= atlasHeight; y++) {
			for (int x = 0; x + footprint.width() <= atlasWidth; x++) {
				Vec4 candidate = new Vec4(x, y, (double) x + footprint.width(), (double) y + footprint.height());
				if (occupied.stream().noneMatch(rect -> overlaps(candidate, rect))) {
					return Optional.of(candidate);
				}
			}
		}
		return Optional.empty();
	}

	private static boolean overlaps(Vec4 a, Vec4 b) {
		return a.a() < b.c() && b.a() < a.c() && a.b() < b.d() && b.b() < a.d();
	}

	private static UvAtlasOverflowException overflowFor(BoxUvMath.Footprint footprint, int atlasWidth, int atlasHeight) {
		// A diferencia de AlphaAutoPackStrategy (reflow completo, puede
		// calcular la dimensión mínima EXACTA que haría caber el conjunto
		// completo), acá el atlas ya tiene contenido estable que no se
		// puede reordenar -- las dimensiones "requeridas" son una cota
		// superior conservadora (agregar una fila nueva del alto del
		// footprint), no un mínimo exacto.
		int requiredWidth = Math.max(atlasWidth, footprint.width());
		int requiredHeight = atlasHeight + footprint.height();
		return new UvAtlasOverflowException(atlasWidth, atlasHeight, requiredWidth, requiredHeight);
	}

}
