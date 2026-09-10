package com.galgothstudio.backend.domain.uv;

import static org.assertj.core.api.Assertions.assertThat;

import com.galgothstudio.backend.domain.model.BaseType;
import com.galgothstudio.backend.domain.model.Bone;
import com.galgothstudio.backend.domain.model.Cuboid;
import com.galgothstudio.backend.domain.model.CuboidFaces;
import com.galgothstudio.backend.domain.model.ExportSettings;
import com.galgothstudio.backend.domain.model.Face;
import com.galgothstudio.backend.domain.model.FaceName;
import com.galgothstudio.backend.domain.model.FormatVersion;
import com.galgothstudio.backend.domain.model.MobProjectModel;
import com.galgothstudio.backend.domain.model.TextureDocument;
import com.galgothstudio.backend.domain.model.UvLayout;
import com.galgothstudio.backend.domain.model.UvRegion;
import com.galgothstudio.backend.domain.model.UvRegionStatus;
import com.galgothstudio.backend.domain.model.UvReservation;
import com.galgothstudio.backend.domain.model.UvReservationReason;
import com.galgothstudio.backend.domain.model.Vec3;
import com.galgothstudio.backend.domain.model.Vec4;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Ticket 044, Diseño técnico §3 de
 * `docs/definiciones/galgoth-studio-fase3-textura.md` --
 * {@link LegacyUvNormalizationService}.
 */
class LegacyUvNormalizationServiceTest {

	private static final int WIDTH = 64;
	private static final int HEIGHT = 64;

	private final LegacyUvNormalizationService service = new LegacyUvNormalizationService(new AlphaAutoPackStrategy());

	private static CuboidFaces emptyFaces() {
		Face placeholder = new Face(new Vec4(0, 0, 0, 0), null);
		return new CuboidFaces(placeholder, placeholder, placeholder, placeholder, placeholder, placeholder);
	}

	private static Bone aBone() {
		return new Bone(UUID.randomUUID().toString(), "torso", null, new Vec3(0, 0, 0), new Vec3(0, 0, 0));
	}

	private static Cuboid aCuboid(String boneId) {
		return new Cuboid(
				UUID.randomUUID().toString(), "body", boneId, new Vec3(-4, 0, -2), new Vec3(4, 8, 2), new Vec3(0, 0, 0),
				new Vec3(0, 0, 0), emptyFaces());
	}

	private static MobProjectModel modelWith(List<Bone> bones, List<Cuboid> cuboids, UvLayout uv) {
		return new MobProjectModel(
				"mob-1", "project-1", "Test Mob", BaseType.HUMANOID, MobProjectModel.UNITS_MINECRAFT_PIXELS, bones,
				cuboids, new TextureDocument(WIDTH, HEIGHT, null), uv, new ArrayList<>(),
				new ExportSettings(FormatVersion.V5), new ArrayList<>());
	}

	/** UV deliberadamente distinta de lo que {@link AlphaAutoPackStrategy} calcularía para el mismo cuboid -- dispara la condición (b). */
	private static UvLayout mismatchedUv(Cuboid cuboid) {
		List<UvRegion> regions = new ArrayList<>();
		for (FaceName face : FaceName.values()) {
			regions.add(new UvRegion(cuboid.id(), face, new Vec4(50, 50, 58, 58)));
		}
		return new UvLayout(WIDTH, HEIGHT, regions);
	}

	private static UvLayout freshUvOf(Cuboid cuboid) {
		UvLayoutStrategy.Result fresh = new AlphaAutoPackStrategy().layout(List.of(cuboid), WIDTH, HEIGHT);
		return new UvLayout(WIDTH, HEIGHT, fresh.regions());
	}

	// -- condición (a): PAINTED/ORPHAN -> siempre no-op, sin importar (b) --------

	@Test
	void unaRegionPaintedEsSiempreNoOpAunqueLaUvAlmacenadaDifieraDeLaFresca() {
		Bone bone = aBone();
		Cuboid cuboid = aCuboid(bone.id());
		List<UvRegion> regions = new ArrayList<>();
		regions.add(new UvRegion(cuboid.id(), FaceName.NORTH, new Vec4(50, 50, 58, 58), UvRegionStatus.PAINTED));
		for (FaceName face : List.of(FaceName.SOUTH, FaceName.EAST, FaceName.WEST, FaceName.UP, FaceName.DOWN)) {
			regions.add(new UvRegion(cuboid.id(), face, new Vec4(50, 50, 58, 58)));
		}
		MobProjectModel model = modelWith(List.of(bone), List.of(cuboid), new UvLayout(WIDTH, HEIGHT, regions));

		MobProjectModel result = service.normalizeIfSafe(model);

		assertThat(result).isEqualTo(model);
	}

	@Test
	void unaRegionOrphanEsSiempreNoOpAunqueLaUvAlmacenadaDifieraDeLaFresca() {
		Bone bone = aBone();
		Cuboid cuboid = aCuboid(bone.id());
		List<UvRegion> regions = new ArrayList<>();
		regions.add(new UvRegion(cuboid.id(), FaceName.NORTH, new Vec4(50, 50, 58, 58), UvRegionStatus.ORPHAN));
		for (FaceName face : List.of(FaceName.SOUTH, FaceName.EAST, FaceName.WEST, FaceName.UP, FaceName.DOWN)) {
			regions.add(new UvRegion(cuboid.id(), face, new Vec4(50, 50, 58, 58)));
		}
		MobProjectModel model = modelWith(List.of(bone), List.of(cuboid), new UvLayout(WIDTH, HEIGHT, regions));

		MobProjectModel result = service.normalizeIfSafe(model);

		assertThat(result).isEqualTo(model);
	}

	// -- condición (b): sin PAINTED/ORPHAN, pero la UV ya coincide -> no-op --------

	@Test
	void sinPaintedNiOrphanYLaUvAlmacenadaYaCoincideConLaFrescaEsNoOp() {
		Bone bone = aBone();
		Cuboid cuboid = aCuboid(bone.id());
		UvLayout alreadyFresh = freshUvOf(cuboid);
		MobProjectModel model = modelWith(List.of(bone), List.of(cuboid), alreadyFresh);

		MobProjectModel result = service.normalizeIfSafe(model);

		assertThat(result).isEqualTo(model);
	}

	// -- ambas condiciones se cumplen -> normaliza, transitorio, en memoria --------

	@Test
	void sinPaintedNiOrphanYLaUvAlmacenadaDifiereDeLaFrescaSeNormaliza() {
		Bone bone = aBone();
		Cuboid cuboid = aCuboid(bone.id());
		UvReservation reservation = new UvReservation(
				"reservation-1", new Vec4(0, 0, 8, 8), UvReservationReason.RESIZE_ABANDONED, "otro-cuboid",
				FaceName.NORTH);
		UvLayout stale = new UvLayout(WIDTH, HEIGHT, mismatchedUv(cuboid).regions(), List.of(reservation));
		MobProjectModel model = modelWith(List.of(bone), List.of(cuboid), stale);

		MobProjectModel result = service.normalizeIfSafe(model);

		assertThat(result).isNotEqualTo(model);
		UvLayout freshLayout = freshUvOf(cuboid);
		assertThat(result.uv().regions()).containsExactlyInAnyOrderElementsOf(freshLayout.regions());
		// las reservas (bookkeeping ajeno a esta migración) se preservan tal cual.
		assertThat(result.uv().reservations()).isEqualTo(List.of(reservation));
		// el modelo original NUNCA se muta -- la normalización es transitoria, en memoria.
		assertThat(model.uv()).isEqualTo(stale);
	}

	@Test
	void unModeloSinNingunCuboidNoTieneNadaQueNormalizarYEsNoOp() {
		MobProjectModel model = modelWith(List.of(), List.of(), new UvLayout(WIDTH, HEIGHT, List.of()));

		MobProjectModel result = service.normalizeIfSafe(model);

		assertThat(result).isEqualTo(model);
	}

}
