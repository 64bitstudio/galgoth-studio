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
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Ticket 041, Diseño técnico §2 de
 * `docs/definiciones/galgoth-studio-fase3-textura.md` -- cubre los 3
 * casos del algoritmo (resize con confirmación, add sin espacio libre,
 * delete con textura pintada), la definición de "espacio verdaderamente
 * libre" y la paridad de {@code BoxUvMath} con {@link AlphaAutoPackStrategy}.
 */
class StableUvStrategyTest {

	private static Cuboid cube(String id, double size) {
		double half = size / 2;
		Face placeholder = new Face(null, null);
		CuboidFaces emptyFaces = new CuboidFaces(placeholder, placeholder, placeholder, placeholder, placeholder, placeholder);
		return new Cuboid(
				id, "cube-" + id, "bone-1", new Vec3(-half, -half, -half), new Vec3(half, half, half), new Vec3(0, 0, 0),
				new Vec3(0, 0, 0), emptyFaces);
	}

	private static List<UvRegion> regionsFor(String cuboidId, CuboidFaces faces, Map<FaceName, UvRegionStatus> statusByFace) {
		List<UvRegion> regions = new ArrayList<>();
		for (FaceName faceName : FaceName.values()) {
			UvRegionStatus status = statusByFace.getOrDefault(faceName, UvRegionStatus.UNPAINTED);
			regions.add(new UvRegion(cuboidId, faceName, BoxUvMath.faceOf(faces, faceName).uv(), status));
		}
		return regions;
	}

	private static boolean overlaps(Vec4 a, Vec4 b) {
		return a.a() < b.c() && b.a() < a.c() && a.b() < b.d() && b.b() < a.d();
	}

	// -- Resize con cara PAINTED: requiere confirmación ---------------------

	@Test
	void resizeQueCambiaElFootprintDeUnaCaraPainted_lanzaExcepcionDeConfirmacionSinMutarNada() {
		Cuboid head = cube("head", 8); // footprint 32x16
		CuboidFaces oldFaces = BoxUvMath.boxUnwrapFaces(head, 0, 0);
		List<UvRegion> oldRegions = regionsFor("head", oldFaces, Map.of(FaceName.NORTH, UvRegionStatus.PAINTED));
		UvLayout previousLayout = new UvLayout(64, 64, oldRegions);
		Cuboid resizedHead = cube("head", 12); // dimensiones distintas -> footprint distinto

		assertThatThrownBy(() -> new StableUvStrategy().layout(List.of(resizedHead), 64, 64, previousLayout))
				.isInstanceOf(PaintedRegionResizeConfirmationRequiredException.class)
				.satisfies(ex -> {
					var confirmation = (PaintedRegionResizeConfirmationRequiredException) ex;
					assertThat(confirmation.affectedFaces())
							.containsExactly(new PaintedRegionResizeConfirmationRequiredException.AffectedFace("head", FaceName.NORTH));
				});
		// "sin mutar nada": previousLayout es un record inmutable -- si la
		// excepción se lanzó ANTES de construir cualquier Result, no hay
		// nada más que pudiera haber mutado.
		assertThat(previousLayout.regions()).isEqualTo(oldRegions);
	}

	@Test
	void resizeSinNingunaCaraPainted_seAplicaDirectoSinExcepcion() {
		Cuboid head = cube("head", 8);
		CuboidFaces oldFaces = BoxUvMath.boxUnwrapFaces(head, 0, 0);
		List<UvRegion> oldRegions = regionsFor("head", oldFaces, Map.of());
		UvLayout previousLayout = new UvLayout(64, 64, oldRegions);
		Cuboid resizedHead = cube("head", 12);

		UvLayoutStrategy.Result result = new StableUvStrategy().layout(List.of(resizedHead), 64, 64, previousLayout);

		assertThat(result.regions()).hasSize(6);
		assertThat(result.reservations()).isEmpty();
	}

	// -- Resize confirmado: reserva + reflow + UNPAINTED ---------------------

	@Test
	void resizeConfirmado_creaUvReservationDelRectViejoYReempaquetaLaCaraComoUnpainted() {
		Cuboid head = cube("head", 8);
		CuboidFaces oldFaces = BoxUvMath.boxUnwrapFaces(head, 0, 0);
		Vec4 oldNorthRect = BoxUvMath.faceOf(oldFaces, FaceName.NORTH).uv();
		List<UvRegion> oldRegions = regionsFor("head", oldFaces, Map.of(FaceName.NORTH, UvRegionStatus.PAINTED));
		UvLayout previousLayout = new UvLayout(64, 64, oldRegions);
		Cuboid resizedHead = cube("head", 12);

		UvLayoutStrategy.Result result = new StableUvStrategy().layout(List.of(resizedHead), 64, 64, previousLayout, true);

		assertThat(result.reservations()).hasSize(1);
		UvReservation reservation = result.reservations().get(0);
		assertThat(reservation.rect()).isEqualTo(oldNorthRect);
		assertThat(reservation.reason()).isEqualTo(UvReservationReason.RESIZE_ABANDONED);
		assertThat(reservation.sourceCuboidId()).isEqualTo("head");
		assertThat(reservation.sourceFace()).isEqualTo(FaceName.NORTH);

		UvRegion newNorth = result.regions()
				.stream()
				.filter(r -> r.cuboidId().equals("head") && r.face() == FaceName.NORTH)
				.findFirst()
				.orElseThrow();
		assertThat(newNorth.status()).isEqualTo(UvRegionStatus.UNPAINTED);
		assertThat(newNorth.rect()).isNotEqualTo(oldNorthRect);
		assertThat(result.regions()).hasSize(6);
	}

	// -- "Espacio verdaderamente libre": Add nunca sobre una reserva ---------

	@Test
	void espacioVerdaderamenteLibre_unAddNuncaSeColocaSobreUnaReservaExistente() {
		// Atlas 64x32. "head" ocupa la mitad superior-izquierda (0,0)-(32,16).
		// Una reserva (de un resize abandonado anterior) ocupa el resto de
		// la fila superior (32,0)-(64,16). Si el algoritmo ignorara la
		// reserva, colocaría el nuevo cuboid ahí -- la única forma de que
		// termine en la fila inferior (y>=16) es que la reserva cuente como
		// ocupada.
		Cuboid head = cube("head", 8); // footprint 32x16
		CuboidFaces headFaces = BoxUvMath.boxUnwrapFaces(head, 0, 0);
		List<UvRegion> headRegions = regionsFor("head", headFaces, Map.of());
		UvReservation reservation =
				new UvReservation("res-1", new Vec4(32, 0, 64, 16), UvReservationReason.RESIZE_ABANDONED, "ghost", FaceName.NORTH);
		UvLayout previousLayout = new UvLayout(64, 32, headRegions, List.of(reservation));

		Cuboid arm = cube("arm", 8); // nuevo cuboid -> Add, footprint 32x16 también
		UvLayoutStrategy.Result result = new StableUvStrategy().layout(List.of(head, arm), 64, 32, previousLayout);

		List<UvRegion> armRegions = result.regions().stream().filter(r -> r.cuboidId().equals("arm")).toList();
		assertThat(armRegions).hasSize(6);
		for (UvRegion region : armRegions) {
			assertThat(overlaps(region.rect(), reservation.rect()))
					.as("la región %s de 'arm' no debe superponerse a la reserva", region.face())
					.isFalse();
			for (UvRegion headRegion : headRegions) {
				assertThat(overlaps(region.rect(), headRegion.rect()))
						.as("la región %s de 'arm' no debe superponerse a 'head'", region.face())
						.isFalse();
			}
		}
		// "head" -- no tocado por el Add.
		List<UvRegion> resultHeadRegions = result.regions().stream().filter(r -> r.cuboidId().equals("head")).toList();
		assertThat(resultHeadRegions).containsExactlyInAnyOrderElementsOf(headRegions);
	}

	// -- Add sin espacio libre: overflow, nunca crece ni reempaqueta --------

	@Test
	void addSinEspacioLibre_lanzaUvAtlasOverflowSinCrecerElAtlasNiReempaquetarLoExistente() {
		Cuboid head = cube("head", 8); // footprint 32x16 -- exactamente el atlas completo
		CuboidFaces headFaces = BoxUvMath.boxUnwrapFaces(head, 0, 0);
		List<UvRegion> headRegions = regionsFor("head", headFaces, Map.of());
		UvLayout previousLayout = new UvLayout(32, 16, headRegions);
		Cuboid arm = cube("arm", 8); // no hay espacio libre en absoluto

		assertThatThrownBy(() -> new StableUvStrategy().layout(List.of(head, arm), 32, 16, previousLayout))
				.isInstanceOf(UvAtlasOverflowException.class);
	}

	// -- Delete con textura pintada: ORPHAN sin nueva UvReservation ----------

	@Test
	void deleteDeCuboidConCaraPintada_marcaLas6CarasOrphanSinCrearUvReservation() {
		Cuboid head = cube("head", 8);
		CuboidFaces oldFaces = BoxUvMath.boxUnwrapFaces(head, 0, 0);
		List<UvRegion> oldRegions = regionsFor("head", oldFaces, Map.of(FaceName.NORTH, UvRegionStatus.PAINTED));
		UvLayout previousLayout = new UvLayout(64, 64, oldRegions);

		// "head" ya no está en la lista de cuboids -- fue eliminado.
		UvLayoutStrategy.Result result = new StableUvStrategy().layout(List.of(), 64, 64, previousLayout);

		assertThat(result.cuboids()).isEmpty();
		assertThat(result.regions()).hasSize(6);
		assertThat(result.regions()).allSatisfy(r -> assertThat(r.status()).isEqualTo(UvRegionStatus.ORPHAN));
		assertThat(result.regions()).extracting(UvRegion::rect)
				.containsExactlyInAnyOrderElementsOf(oldRegions.stream().map(UvRegion::rect).toList());
		assertThat(result.reservations()).isEmpty();
	}

	@Test
	void deleteDeCuboidSinTextura_tambienQuedaOrphanConsistenteConLaDefinicionGeneral() {
		Cuboid head = cube("head", 8);
		CuboidFaces oldFaces = BoxUvMath.boxUnwrapFaces(head, 0, 0);
		List<UvRegion> oldRegions = regionsFor("head", oldFaces, Map.of());
		UvLayout previousLayout = new UvLayout(64, 64, oldRegions);

		UvLayoutStrategy.Result result = new StableUvStrategy().layout(List.of(), 64, 64, previousLayout);

		assertThat(result.regions()).extracting(UvRegion::status).allMatch(UvRegionStatus.ORPHAN::equals);
	}

	// -- Un cuboid sin cambios reales queda 100% intacto (posición y status) --

	@Test
	void unCuboidNoTocado_conservaExactamenteSuRectYStatusOriginal() {
		Cuboid head = cube("head", 8);
		CuboidFaces oldFaces = BoxUvMath.boxUnwrapFaces(head, 10, 10);
		List<UvRegion> oldRegions = regionsFor("head", oldFaces, Map.of(FaceName.UP, UvRegionStatus.PAINTED));
		UvLayout previousLayout = new UvLayout(64, 64, oldRegions);

		UvLayoutStrategy.Result result = new StableUvStrategy().layout(List.of(head), 64, 64, previousLayout);

		assertThat(result.regions()).containsExactlyInAnyOrderElementsOf(oldRegions);
		assertThat(result.reservations()).isEmpty();
	}

	// -- Paridad: StableUvStrategy usa la MISMA matemática que AlphaAutoPackStrategy --

	@Test
	void unAddSobreAtlasVacio_produceElMismoResultadoQueAlphaAutoPackStrategyParaLaMismaGeometria() {
		// Atlas vacío en ambos casos -- el primer cuboid siempre cae en
		// (0,0): si BoxUvMath divergiera entre las dos estrategias, este
		// test lo detectaría.
		Cuboid head = cube("head", 8);
		Cuboid body = cube("body", 12);

		UvLayoutStrategy.Result stableResult = new StableUvStrategy().layout(List.of(head, body), 64, 64);
		UvLayoutStrategy.Result alphaResult = new AlphaAutoPackStrategy().layout(List.of(head, body), 64, 64);

		assertThat(stableResult.cuboids()).isEqualTo(alphaResult.cuboids());
		assertThat(stableResult.regions()).containsExactlyInAnyOrderElementsOf(alphaResult.regions());
	}

}
