package com.galgothstudio.backend.domain.uv;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.galgothstudio.backend.domain.model.Cuboid;
import com.galgothstudio.backend.domain.model.CuboidFaces;
import com.galgothstudio.backend.domain.model.Face;
import com.galgothstudio.backend.domain.model.FaceName;
import com.galgothstudio.backend.domain.model.UvLayout;
import com.galgothstudio.backend.domain.model.UvRegion;
import com.galgothstudio.backend.domain.model.UvRegionStatus;
import com.galgothstudio.backend.domain.model.UvReservation;
import com.galgothstudio.backend.domain.model.UvReservationReason;
import com.galgothstudio.backend.domain.model.Vec3;
import com.galgothstudio.backend.domain.model.Vec4;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Ticket 041, Diseño técnico §2: {@code UvLayoutSelector} decide entre
 * {@link AlphaAutoPackStrategy} y {@link StableUvStrategy} según si
 * {@code previousLayout.regions()} tiene algún {@code PAINTED}/{@code ORPHAN},
 * O {@code previousLayout.reservations()} no está vacío (ticket 057 --
 * {@code requiresStableLayout}, hallazgo real corregido por el PO).
 */
class UvLayoutSelectorTest {

	private final UvLayoutSelector selector = new UvLayoutSelector(new AlphaAutoPackStrategy(), new StableUvStrategy());

	private static Cuboid cube(String id, double size) {
		double half = size / 2;
		Face placeholder = new Face(null, null);
		CuboidFaces emptyFaces = new CuboidFaces(placeholder, placeholder, placeholder, placeholder, placeholder, placeholder);
		return new Cuboid(
				id, "cube-" + id, "bone-1", new Vec3(-half, -half, -half), new Vec3(half, half, half), new Vec3(0, 0, 0),
				new Vec3(0, 0, 0), emptyFaces);
	}

	@Test
	void previousLayoutSinPaintedNiOrphan_delegaEnAlphaAutoPackStrategyConResultadoByteIdentico() {
		Cuboid head = cube("head", 8);
		UvLayout previousLayout = new UvLayout(
				64, 64, List.of(new UvRegion("head", FaceName.NORTH, new com.galgothstudio.backend.domain.model.Vec4(8, 8, 16, 16), UvRegionStatus.UNPAINTED)));

		UvLayoutStrategy.Result viaSelector = selector.layout(List.of(head), 64, 64, previousLayout);
		UvLayoutStrategy.Result viaAlphaDirecto = new AlphaAutoPackStrategy().layout(List.of(head), 64, 64);

		assertThat(viaSelector.cuboids()).isEqualTo(viaAlphaDirecto.cuboids());
		assertThat(viaSelector.regions()).isEqualTo(viaAlphaDirecto.regions());
	}

	@Test
	void previousLayoutConAlMenosUnPainted_delegaEnStableUvStrategy() {
		Cuboid head = cube("head", 8); // footprint 32x16
		// Coloca el layout previo en un offset (10,10) deliberadamente
		// distinto de donde AlphaAutoPackStrategy reflowearía (siempre
		// arranca en (0,0)) -- si el selector eligiera Alpha por error, el
		// resultado NO preservaría este offset.
		CuboidFaces oldFaces = BoxUvMath.boxUnwrapFaces(head, 10, 10);
		List<UvRegion> oldRegions = new ArrayList<>();
		for (FaceName faceName : FaceName.values()) {
			UvRegionStatus status = faceName == FaceName.NORTH ? UvRegionStatus.PAINTED : UvRegionStatus.UNPAINTED;
			oldRegions.add(new UvRegion("head", faceName, BoxUvMath.faceOf(oldFaces, faceName).uv(), status));
		}
		UvLayout previousLayout = new UvLayout(64, 64, oldRegions);

		UvLayoutStrategy.Result result = selector.layout(List.of(head), 64, 64, previousLayout);

		// El offset (10,10) sobrevive -- prueba que se usó StableUvStrategy
		// (que preserva geometría sin cambios), no un reflow de AlphaAutoPackStrategy.
		assertThat(result.cuboids().get(0).faces().up().uv().a()).isEqualTo(18); // offsetX(10) + z(8)
		assertThat(result.cuboids().get(0).faces().west().uv().a()).isEqualTo(10); // offsetX directo
	}

	@Test
	void previousLayoutConAlMenosUnOrphan_delegaEnStableUvStrategy() {
		Cuboid head = cube("head", 8);
		CuboidFaces oldFaces = BoxUvMath.boxUnwrapFaces(head, 10, 10);
		List<UvRegion> oldRegions = new ArrayList<>();
		for (FaceName faceName : FaceName.values()) {
			oldRegions.add(new UvRegion("head", faceName, BoxUvMath.faceOf(oldFaces, faceName).uv(), UvRegionStatus.UNPAINTED));
		}
		// Una fila ORPHAN de OTRO cuboid ya eliminado -- por sí sola basta
		// para activar StableUvStrategy aunque "head" esté 100% UNPAINTED.
		oldRegions.add(
				new UvRegion(
						"deleted-cuboid", FaceName.NORTH, new com.galgothstudio.backend.domain.model.Vec4(40, 40, 48, 48),
						UvRegionStatus.ORPHAN));
		UvLayout previousLayout = new UvLayout(64, 64, oldRegions);

		UvLayoutStrategy.Result result = selector.layout(List.of(head), 64, 64, previousLayout);

		assertThat(result.cuboids().get(0).faces().up().uv().a()).isEqualTo(18);
	}

	// -- Ticket 043: wiring de confirmPaintLoss a través del selector --------

	@Test
	void resizeConCaraPintadaSinConfirmar_delegaEnStableUvStrategyYPropagaLaExcepcion() {
		Cuboid head = cube("head", 8);
		CuboidFaces oldFaces = BoxUvMath.boxUnwrapFaces(head, 0, 0);
		List<UvRegion> oldRegions = new ArrayList<>();
		for (FaceName faceName : FaceName.values()) {
			UvRegionStatus status = faceName == FaceName.NORTH ? UvRegionStatus.PAINTED : UvRegionStatus.UNPAINTED;
			oldRegions.add(new UvRegion("head", faceName, BoxUvMath.faceOf(oldFaces, faceName).uv(), status));
		}
		UvLayout previousLayout = new UvLayout(64, 64, oldRegions);
		Cuboid resizedHead = cube("head", 12);
		List<Cuboid> cuboids = List.of(resizedHead);

		assertThatThrownBy(() -> selector.layout(cuboids, 64, 64, previousLayout, false))
				.isInstanceOf(PaintedRegionResizeConfirmationRequiredException.class);
	}

	@Test
	void resizeConCaraPintadaConfirmado_seAplicaYCreaLaReservaEsperada() {
		Cuboid head = cube("head", 8);
		CuboidFaces oldFaces = BoxUvMath.boxUnwrapFaces(head, 0, 0);
		List<UvRegion> oldRegions = new ArrayList<>();
		for (FaceName faceName : FaceName.values()) {
			UvRegionStatus status = faceName == FaceName.NORTH ? UvRegionStatus.PAINTED : UvRegionStatus.UNPAINTED;
			oldRegions.add(new UvRegion("head", faceName, BoxUvMath.faceOf(oldFaces, faceName).uv(), status));
		}
		UvLayout previousLayout = new UvLayout(64, 64, oldRegions);
		Cuboid resizedHead = cube("head", 12);

		UvLayoutStrategy.Result result = selector.layout(List.of(resizedHead), 64, 64, previousLayout, true);

		assertThat(result.reservations()).hasSize(1);
		assertThat(result.reservations().get(0).sourceFace()).isEqualTo(FaceName.NORTH);
	}

	// -- Ticket 057: requiresStableLayout() debe considerar reservations, no solo PAINTED/ORPHAN --------

	@Test
	void previousLayoutTodoUnpaintedSinReservations_delegaEnAlphaAutoPackStrategy() {
		// Test B (ticket 057): caso "limpio" -- PAINTED=0, ORPHAN=0, reservations=0.
		// Comportamiento sin cambios respecto a Fase 1+2.
		Cuboid head = cube("head", 8);
		UvLayout previousLayout = new UvLayout(
				64, 64,
				List.of(
						new UvRegion(
								"head", FaceName.NORTH, new Vec4(8, 8, 16, 16), UvRegionStatus.UNPAINTED)),
				List.of());

		UvLayoutStrategy.Result viaSelector = selector.layout(List.of(head), 64, 64, previousLayout);
		UvLayoutStrategy.Result viaAlphaDirecto = new AlphaAutoPackStrategy().layout(List.of(head), 64, 64);

		assertThat(viaSelector.cuboids()).isEqualTo(viaAlphaDirecto.cuboids());
		assertThat(viaSelector.regions()).isEqualTo(viaAlphaDirecto.regions());
	}

	@Test
	void previousLayoutSinPaintedNiOrphanConReserva_delegaEnStableUvStrategy() {
		// Test A (ticket 057): PAINTED=0, ORPHAN=0, reservations=1 -- hallazgo
		// real corregido. Antes del fix, esto habría elegido AlphaAutoPackStrategy
		// (que reflowea TODOS los cuboids desde (0,0), ignorando la reserva) y
		// habría podido reempaquetar exactamente encima del rect reservado.
		Cuboid head = cube("head", 8); // footprint 32x16 -- boxUnwrapFaces(0,0) NO se solapa con la reserva de abajo
		List<UvRegion> oldRegions = new ArrayList<>();
		for (FaceName faceName : FaceName.values()) {
			oldRegions.add(new UvRegion("head", faceName, BoxUvMath.faceOf(BoxUvMath.boxUnwrapFaces(head, 0, 0), faceName).uv(), UvRegionStatus.UNPAINTED));
		}
		UvReservation reservedRect = new UvReservation(
				"reservation-1", new Vec4(0, 32, 32, 48), UvReservationReason.RESIZE_ABANDONED, "old-cuboid",
				FaceName.NORTH);
		UvLayout previousLayout = new UvLayout(64, 64, oldRegions, List.of(reservedRect));

		UvLayoutStrategy.Result viaSelector = selector.layout(List.of(head), 64, 64, previousLayout);
		UvLayoutStrategy.Result viaStableDirecto = new StableUvStrategy().layout(List.of(head), 64, 64, previousLayout);

		// El resultado es idéntico al de invocar StableUvStrategy directamente
		// (prueba que el selector delegó ahí, no en AlphaAutoPackStrategy) y la
		// reserva sobrevive intacta en el resultado.
		assertThat(viaSelector.cuboids()).isEqualTo(viaStableDirecto.cuboids());
		assertThat(viaSelector.regions()).isEqualTo(viaStableDirecto.regions());
		assertThat(viaSelector.reservations()).contains(reservedRect);
	}

	@Test
	void resizeDeLaUnicaRegionPintadaCreaReserva_elSiguienteAddUsaStableUvStrategyYNuncaOcupaElRectReservado() {
		// Test C (ticket 057): secuencia completa end-to-end sobre el selector,
		// reutilizando el mecanismo real de StableUvStrategy (resize confirmado
		// crea la UvReservation) -- no una fixture inventada desde cero.
		//
		// 1) Layout inicial: "head" con su cara NORTH ya PAINTED.
		Cuboid head = cube("head", 8);
		CuboidFaces initialFaces = BoxUvMath.boxUnwrapFaces(head, 0, 0);
		List<UvRegion> initialRegions = new ArrayList<>();
		for (FaceName faceName : FaceName.values()) {
			UvRegionStatus status = faceName == FaceName.NORTH ? UvRegionStatus.PAINTED : UvRegionStatus.UNPAINTED;
			initialRegions.add(new UvRegion("head", faceName, BoxUvMath.faceOf(initialFaces, faceName).uv(), status));
		}
		UvLayout initialLayout = new UvLayout(64, 64, initialRegions);

		// 2) Resize confirmado de "head" (8 -> 12): la cara NORTH pintada queda
		// UNPAINTED en el nuevo offset y su rect viejo pasa a reserva.
		Cuboid resizedHead = cube("head", 12);
		UvLayoutStrategy.Result afterResize = selector.layout(List.of(resizedHead), 64, 64, initialLayout, true);

		boolean stillHasPaintedOrOrphan = afterResize.regions()
				.stream()
				.anyMatch(r -> r.status() == UvRegionStatus.PAINTED || r.status() == UvRegionStatus.ORPHAN);
		assertThat(stillHasPaintedOrOrphan).isFalse();
		assertThat(afterResize.reservations()).hasSize(1);
		Vec4 reservedRect = afterResize.reservations().get(0).rect();

		// 3) Sobre ESE nuevo layout (PAINTED=0, ORPHAN=0, reservations=1), se
		// agrega un cuboid nuevo.
		UvLayout layoutAfterResize = new UvLayout(64, 64, afterResize.regions(), afterResize.reservations());
		Cuboid newCuboid = cube("new-cuboid", 6);
		UvLayoutStrategy.Result afterAdd = selector.layout(List.of(resizedHead, newCuboid), 64, 64, layoutAfterResize);

		// 4) El rect asignado al cuboid nuevo NUNCA se solapa con el rect reservado.
		Cuboid placedNewCuboid = afterAdd.cuboids()
				.stream()
				.filter(c -> c.id().equals("new-cuboid"))
				.findFirst()
				.orElseThrow();
		for (FaceName faceName : FaceName.values()) {
			Vec4 newCuboidFaceRect = BoxUvMath.faceOf(placedNewCuboid.faces(), faceName).uv();
			assertThat(overlaps(newCuboidFaceRect, reservedRect))
					.as("cara %s del cuboid nuevo (%s) no debe solapar el rect reservado (%s)", faceName,
							newCuboidFaceRect, reservedRect)
					.isFalse();
		}
		// Y la reserva sigue viva en el resultado (StableUvStrategy la propaga, nunca la descarta).
		assertThat(afterAdd.reservations()).contains(afterResize.reservations().get(0));
	}

	private static boolean overlaps(Vec4 a, Vec4 b) {
		return a.a() < b.c() && b.a() < a.c() && a.b() < b.d() && b.b() < a.d();
	}

}
