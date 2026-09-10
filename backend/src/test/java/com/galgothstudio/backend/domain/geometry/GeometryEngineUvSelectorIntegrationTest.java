package com.galgothstudio.backend.domain.geometry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.galgothstudio.backend.domain.model.Cuboid;
import com.galgothstudio.backend.domain.model.CuboidFaces;
import com.galgothstudio.backend.domain.model.FaceName;
import com.galgothstudio.backend.domain.model.MobProjectModel;
import com.galgothstudio.backend.domain.model.UvLayout;
import com.galgothstudio.backend.domain.model.UvRegion;
import com.galgothstudio.backend.domain.model.UvRegionStatus;
import com.galgothstudio.backend.domain.model.UvReservation;
import com.galgothstudio.backend.domain.model.Vec3;
import com.galgothstudio.backend.domain.uv.AlphaAutoPackStrategy;
import com.galgothstudio.backend.domain.uv.BoxUvMath;
import com.galgothstudio.backend.domain.uv.PaintedRegionResizeConfirmationRequiredException;
import com.galgothstudio.backend.domain.uv.StableUvStrategy;
import com.galgothstudio.backend.domain.uv.UvLayoutSelector;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Ticket 041, Diseño técnico §2 de
 * `docs/definiciones/galgoth-studio-fase3-textura.md`: wiring de
 * {@code UvLayoutSelector} en {@link GeometryEngine#apply(MobProjectModel, List, com.galgothstudio.backend.domain.uv.UvLayoutStrategy)}
 * -- uno de los 3 llamadores explícitos del ticket. Complementa (no
 * reemplaza) a {@code GeometryEngineUvIntegrationTest}, que sigue
 * ejercitando {@code AlphaAutoPackStrategy} sola y no se toca.
 */
class GeometryEngineUvSelectorIntegrationTest {

	private static final UvLayoutSelector SELECTOR = new UvLayoutSelector(new AlphaAutoPackStrategy(), new StableUvStrategy());

	private static MobProjectModel withUv(MobProjectModel base, List<UvRegion> regions, List<UvReservation> reservations) {
		return new MobProjectModel(
				base.mobId(), base.projectId(), base.name(), base.baseType(), base.units(), base.bones(), base.cuboids(),
				base.texture(), new UvLayout(base.uv().textureWidth(), base.uv().textureHeight(), regions, reservations),
				base.animations(), base.exportSettings(), base.referenceImages());
	}

	@Test
	void sinPaintedNiOrphan_elCreateCuboidReflowaConAlphaAutoPackStrategyComoAntes() {
		MobProjectModel model = GeometryFixtures.emptyModel();
		GeometryOperation createBone = new CreateBone("tmp-bone", "bone", null, new Vec3(0, 0, 0), new Vec3(0, 0, 0));
		GeometryOperation createCuboid = new CreateCuboid(
				"tmp-cube", "cube", "tmp-bone", new Vec3(-4, -4, -4), new Vec3(4, 4, 4), new Vec3(0, 0, 0), new Vec3(0, 0, 0));

		MobProjectModel result = GeometryEngine.apply(model, List.of(createBone, createCuboid), SELECTOR);

		assertThat(result.uv().regions()).hasSize(6);
		assertThat(result.cuboids().get(0).faces().north().texture()).isZero();
		assertThat(result.uv().reservations()).isEmpty();
	}

	@Test
	void batchDeSoloRemoveCuboid_invocaLaEstrategiaYMarcaOrphanLas6CarasDelCuboidPintado() {
		String boneId = UUID.randomUUID().toString();
		String cuboidId = UUID.randomUUID().toString();
		MobProjectModel base = GeometryFixtures.modelWithBoneAndCuboid(boneId, cuboidId);
		Cuboid cuboid = base.cuboids().get(0);
		CuboidFaces faces = BoxUvMath.boxUnwrapFaces(cuboid, 0, 0);
		List<UvRegion> regions = new ArrayList<>();
		for (FaceName faceName : FaceName.values()) {
			UvRegionStatus status = faceName == FaceName.NORTH ? UvRegionStatus.PAINTED : UvRegionStatus.UNPAINTED;
			regions.add(new UvRegion(cuboidId, faceName, BoxUvMath.faceOf(faces, faceName).uv(), status));
		}
		MobProjectModel model = withUv(base, regions, List.of());

		MobProjectModel result = GeometryEngine.apply(model, List.of(new RemoveCuboid(cuboidId)), SELECTOR);

		assertThat(result.cuboids()).isEmpty();
		assertThat(result.uv().regions()).hasSize(6);
		assertThat(result.uv().regions()).allSatisfy(r -> assertThat(r.status()).isEqualTo(UvRegionStatus.ORPHAN));
		assertThat(result.uv().reservations()).isEmpty();
	}

	@Test
	void resizeQueAfectaUnaCaraPintada_propagaLaExcepcionDeConfirmacionATravesDelMotor() {
		String boneId = UUID.randomUUID().toString();
		String cuboidId = UUID.randomUUID().toString();
		MobProjectModel base = GeometryFixtures.modelWithBoneAndCuboid(boneId, cuboidId);
		Cuboid cuboid = base.cuboids().get(0);
		CuboidFaces faces = BoxUvMath.boxUnwrapFaces(cuboid, 0, 0);
		List<UvRegion> regions = new ArrayList<>();
		for (FaceName faceName : FaceName.values()) {
			UvRegionStatus status = faceName == FaceName.UP ? UvRegionStatus.PAINTED : UvRegionStatus.UNPAINTED;
			regions.add(new UvRegion(cuboidId, faceName, BoxUvMath.faceOf(faces, faceName).uv(), status));
		}
		MobProjectModel model = withUv(base, regions, List.of());
		GeometryOperation resize = new ResizeCuboid(cuboidId, new Vec3(2, 2, 2));
		List<GeometryOperation> operations = List.of(resize);

		assertThatThrownBy(() -> GeometryEngine.apply(model, operations, SELECTOR))
				.isInstanceOf(PaintedRegionResizeConfirmationRequiredException.class);
	}

	@Test
	void unaOperacionQueNoTocaUv_preservaLasReservasExistentesDelModelo() {
		// Ticket 041: antes de este fix, CUALQUIER operación (incluso una
		// que no toca UV) perdía en silencio las reservas ya existentes,
		// porque `GeometryEngine.apply(model, ops)` (3 args) construía el
		// UvLayout resultante con el constructor de 2 argumentos.
		String boneId = UUID.randomUUID().toString();
		String cuboidId = UUID.randomUUID().toString();
		MobProjectModel base = GeometryFixtures.modelWithBoneAndCuboid(boneId, cuboidId);
		UvReservation reservation = new UvReservation(
				"res-1", new com.galgothstudio.backend.domain.model.Vec4(0, 0, 8, 8),
				com.galgothstudio.backend.domain.model.UvReservationReason.RESIZE_ABANDONED, "ghost", FaceName.NORTH);
		MobProjectModel model = withUv(base, base.uv().regions(), List.of(reservation));
		GeometryOperation setRotation = new SetBoneRotation(boneId, new Vec3(0, 0, 45));

		MobProjectModel result = GeometryEngine.apply(model, List.of(setRotation), SELECTOR);

		assertThat(result.uv().reservations()).containsExactly(reservation);
	}

}
