package com.galgothstudio.backend.domain.geometry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.galgothstudio.backend.domain.model.Cuboid;
import com.galgothstudio.backend.domain.model.MobProjectModel;
import com.galgothstudio.backend.domain.model.TextureDocument;
import com.galgothstudio.backend.domain.model.UvLayout;
import com.galgothstudio.backend.domain.model.Vec3;
import com.galgothstudio.backend.domain.uv.AlphaAutoPackStrategy;
import com.galgothstudio.backend.domain.uv.UvAtlasOverflowException;
import com.galgothstudio.backend.domain.uv.UvLayoutStrategy;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Ticket 006, integración con el Geometry Engine (005): `AlphaAutoPackStrategy`
 * es la autoridad canónica de UV invocada por {@link GeometryEngine} en
 * cada {@code createCuboid}/{@code resizeCuboid} -- nunca en operaciones
 * que no tocan geometría de cuboid, y siempre intercambiable por otra
 * {@link UvLayoutStrategy} sin tocar el motor (AC #3 del ticket 006).
 */
class GeometryEngineUvIntegrationTest {

	@Test
	void createCuboidViaGeometryEngine_recibeUvValidaDeAlphaAutoPackStrategy() {
		MobProjectModel model = GeometryFixtures.emptyModel();
		GeometryOperation createBone = new CreateBone("tmp-bone", "bone", null, new Vec3(0, 0, 0), new Vec3(0, 0, 0));
		GeometryOperation createCuboid = new CreateCuboid(
				"tmp-cube", "cube", "tmp-bone", new Vec3(-4, -4, -4), new Vec3(4, 4, 4), new Vec3(0, 0, 0),
				new Vec3(0, 0, 0));
		List<GeometryOperation> operations = List.of(createBone, createCuboid);

		MobProjectModel result = GeometryEngine.apply(model, operations, new AlphaAutoPackStrategy());

		Cuboid cuboid = result.cuboids().get(0);
		assertThat(cuboid.faces().north().texture()).isZero(); // ya no es el placeholder null del motor puro
		assertThat(cuboid.faces().north().uv().toArray()).doesNotContain(0.0, 0.0, 0.0, 0.0);
		assertThat(result.uv().regions()).hasSize(6); // una uv.region por cara
	}

	@Test
	void batchSinCreateCuboidNiResizeCuboid_noInvocaAutoUv() {
		// setBoneRotation no toca geometría de cuboid -- AutoUv no debe correr.
		String boneId = UUID.randomUUID().toString();
		String cuboidId = UUID.randomUUID().toString();
		MobProjectModel model = GeometryFixtures.modelWithBoneAndCuboid(boneId, cuboidId);
		List<GeometryOperation> operations = List.of(new SetBoneRotation(boneId, new Vec3(0, 0, 45)));

		MobProjectModel result = GeometryEngine.apply(model, operations, new AlphaAutoPackStrategy());

		// El cuboid del fixture trae faces placeholder (uv=[0,0,0,0], texture=null) --
		// si AutoUv se hubiera invocado igual, esto ya no sería cierto.
		Cuboid cuboid = result.cuboids().get(0);
		assertThat(cuboid.faces().north().texture()).isNull();
		assertThat(cuboid.faces().north().uv().toArray()).containsExactly(0, 0, 0, 0);
	}

	@Test
	void laEstrategiaEsIntercambiableSinTocarElGeometryEngine() {
		// Un UvLayoutStrategy de prueba (no AlphaAutoPackStrategy) que deja
		// los cuboids sin tocar -- demuestra que GeometryEngine no conoce ni
		// depende de AlphaAutoPackStrategy en particular (AC #3).
		UvLayoutStrategy passthroughStrategy = (cuboids, textureWidth, textureHeight) ->
				new UvLayoutStrategy.Result(cuboids, List.of());

		MobProjectModel model = GeometryFixtures.emptyModel();
		GeometryOperation createBone = new CreateBone("tmp-bone", "bone", null, new Vec3(0, 0, 0), new Vec3(0, 0, 0));
		GeometryOperation createCuboid = new CreateCuboid(
				"tmp-cube", "cube", "tmp-bone", new Vec3(-4, -4, -4), new Vec3(4, 4, 4), new Vec3(0, 0, 0),
				new Vec3(0, 0, 0));
		List<GeometryOperation> operations = List.of(createBone, createCuboid);

		MobProjectModel result = GeometryEngine.apply(model, operations, passthroughStrategy);

		// El passthrough no asigna UV real -- el placeholder del motor puro sigue intacto.
		Cuboid cuboid = result.cuboids().get(0);
		assertThat(cuboid.faces().north().texture()).isNull();
		assertThat(result.uv().regions()).isEmpty();
	}

	@Test
	void siElAtlasNoAlcanza_laExcepcionDeUvAtlasOverflowSePropagaAtravesDelMotor() {
		MobProjectModel base = GeometryFixtures.emptyModel();
		// Atlas deliberadamente minúsculo (8x8) para forzar overflow con un cuboid 8x8x8.
		MobProjectModel model = new MobProjectModel(
				base.mobId(), base.projectId(), base.name(), base.baseType(), base.units(), List.of(),
				List.of(), new TextureDocument(8, 8, null), new UvLayout(8, 8, List.of()), base.animations(),
				base.exportSettings(), base.referenceImages());
		GeometryOperation createBone = new CreateBone("tmp-bone", "bone", null, new Vec3(0, 0, 0), new Vec3(0, 0, 0));
		GeometryOperation createCuboid = new CreateCuboid(
				"tmp-cube", "cube", "tmp-bone", new Vec3(-4, -4, -4), new Vec3(4, 4, 4), new Vec3(0, 0, 0),
				new Vec3(0, 0, 0));
		List<GeometryOperation> operations = List.of(createBone, createCuboid);
		AlphaAutoPackStrategy strategy = new AlphaAutoPackStrategy();

		assertThatThrownBy(() -> GeometryEngine.apply(model, operations, strategy))
				.isInstanceOf(UvAtlasOverflowException.class);
	}

	@Test
	void losLimitesDelAtlasSeTomanDeTextureNoDeUvBookkeepingPotencialmenteDesincronizado() {
		// uv.textureWidth/Height queda deliberadamente en 8x8 (valor
		// "viejo"/desincronizado) mientras que texture.width/height (fuente
		// de verdad real per el ticket 006) es 64x64 -- si el motor usara
		// el valor de `uv` en vez de `texture`, esto lanzaría overflow.
		MobProjectModel base = GeometryFixtures.emptyModel();
		MobProjectModel model = new MobProjectModel(
				base.mobId(), base.projectId(), base.name(), base.baseType(), base.units(), List.of(), List.of(),
				new TextureDocument(64, 64, null), new UvLayout(8, 8, List.of()), base.animations(),
				base.exportSettings(), base.referenceImages());
		GeometryOperation createBone = new CreateBone("tmp-bone", "bone", null, new Vec3(0, 0, 0), new Vec3(0, 0, 0));
		GeometryOperation createCuboid = new CreateCuboid(
				"tmp-cube", "cube", "tmp-bone", new Vec3(-4, -4, -4), new Vec3(4, 4, 4), new Vec3(0, 0, 0),
				new Vec3(0, 0, 0));
		List<GeometryOperation> operations = List.of(createBone, createCuboid);

		MobProjectModel result = GeometryEngine.apply(model, operations, new AlphaAutoPackStrategy());

		assertThat(result.uv().textureWidth()).isEqualTo(64);
		assertThat(result.uv().textureHeight()).isEqualTo(64);
		assertThat(result.cuboids().get(0).faces().north().texture()).isZero();
	}

}
