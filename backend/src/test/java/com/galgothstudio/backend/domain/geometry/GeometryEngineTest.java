package com.galgothstudio.backend.domain.geometry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.galgothstudio.backend.domain.CoordinateSystem;
import com.galgothstudio.backend.domain.model.Bone;
import com.galgothstudio.backend.domain.model.Cuboid;
import com.galgothstudio.backend.domain.model.MobProjectModel;
import com.galgothstudio.backend.domain.model.Vec3;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Ticket 005 -- Geometry Engine (backend). Cubre las 5 AC del ticket:
 * <ol>
 *   <li>Las 9 operaciones producen el efecto esperado usando el CoordinateSystemContract de 004.</li>
 *   <li>Whitelist/payload inválido -&gt; rechazo atómico (batch completo, sin mutación).</li>
 *   <li>tempRef de createBone resuelto a un UUID real en un createCuboid del mismo batch.</li>
 *   <li>resizeCuboid/moveCuboid con dimensión resultante &lt;= 0 -&gt; rechazado antes de aplicarse.</li>
 *   <li>removeCuboid deja el modelo con referencias válidas (cascade de uv.regions).</li>
 * </ol>
 * El rechazo de "operación fuera de whitelist" a nivel JSON (deserialización)
 * se cubre en {@link GeometryOperationJsonTest}.
 */
class GeometryEngineTest {

	private static final File COORD_FIXTURE_FILE = new File("../contracts/fixtures/coordinate-system-fixture.json");

	private record SingleCase(double[] point, double[] origin, double[] rotationDeg, double[] expected) {
	}

	private record ParentChildCase(
			double[] point,
			double[] cuboidOrigin,
			double[] cuboidRotationDeg,
			double[] boneOrigin,
			double[] boneRotationDeg) {
	}

	private record CoordFixture(SingleCase singleAxisRotation, ParentChildCase parentChildComposition) {
	}

	private static CoordFixture loadCoordFixture() throws IOException {
		ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		return mapper.readValue(COORD_FIXTURE_FILE, CoordFixture.class);
	}

	// -- AC #1: createBone / createCuboid --------------------------------

	@Test
	void createBone_agregaUnBoneRaizConUuidGenerado() {
		MobProjectModel model = GeometryFixtures.emptyModel();
		GeometryOperation op = new CreateBone("tmp-torso", "torso", null, new Vec3(0, 24, 0), new Vec3(0, 0, 0));

		MobProjectModel result = GeometryEngine.apply(model, List.of(op));

		assertThat(result.bones()).hasSize(1);
		Bone created = result.bones().get(0);
		assertThat(created.name()).isEqualTo("torso");
		assertThat(created.parentId()).isNull();
		assertThat(UUID.fromString(created.id())).isNotNull(); // no lanza -> es un UUID válido
		assertThat(created.id()).isNotEqualTo("tmp-torso");
	}

	@Test
	void createBone_conParentIdRealExistenteLoParenta() {
		String torsoId = UUID.randomUUID().toString();
		MobProjectModel model = GeometryFixtures.modelWithBoneAndCuboid(torsoId, UUID.randomUUID().toString());
		GeometryOperation op = new CreateBone("tmp-arm", "arm", torsoId, new Vec3(4, 22, 0), new Vec3(0, 0, 0));

		MobProjectModel result = GeometryEngine.apply(model, List.of(op));

		Bone arm = result.bones().stream().filter(b -> b.name().equals("arm")).findFirst().orElseThrow();
		assertThat(arm.parentId()).isEqualTo(torsoId);
	}

	@Test
	void createCuboid_agregaUnCuboidConCarasPlaceholder() {
		String boneId = UUID.randomUUID().toString();
		MobProjectModel model = GeometryFixtures.modelWithBoneAndCuboid(boneId, UUID.randomUUID().toString());
		GeometryOperation op = new CreateCuboid(
				"tmp-hand", "hand", boneId, new Vec3(-2, 0, -2), new Vec3(2, 4, 2), new Vec3(0, 2, 0),
				new Vec3(0, 0, 0));

		MobProjectModel result = GeometryEngine.apply(model, List.of(op));

		Cuboid hand = result.cuboids().stream().filter(c -> c.name().equals("hand")).findFirst().orElseThrow();
		assertThat(hand.boneId()).isEqualTo(boneId);
		assertThat(hand.faces().north().texture()).isNull();
		assertThat(hand.faces().north().uv().toArray()).containsExactly(0, 0, 0, 0);
	}

	@Test
	void createCuboid_conDimensionesNoPositivasSeRechaza() {
		String boneId = UUID.randomUUID().toString();
		MobProjectModel model = GeometryFixtures.modelWithBoneAndCuboid(boneId, UUID.randomUUID().toString());
		// to.x == from.x -> dimensión 0 en X.
		GeometryOperation op = new CreateCuboid(
				"tmp-bad", "bad", boneId, new Vec3(0, 0, 0), new Vec3(0, 4, 4), new Vec3(0, 0, 0), new Vec3(0, 0, 0));
		List<GeometryOperation> operations = List.of(op);

		assertThatThrownBy(() -> GeometryEngine.apply(model, operations)).isInstanceOf(GeometryValidationException.class);
	}

	// -- AC #3: tempRef createBone -> createCuboid en el mismo batch -----

	@Test
	void createBoneSeguidoDeCreateCuboidConTempRef_resuelveAUnUuidRealGeneradoPorElBackend() {
		MobProjectModel model = GeometryFixtures.emptyModel();
		GeometryOperation createBone = new CreateBone("tmp-torso", "torso", null, new Vec3(0, 24, 0), new Vec3(0, 0, 0));
		GeometryOperation createCuboid = new CreateCuboid(
				"tmp-body", "body", "tmp-torso", new Vec3(-4, 12, -2), new Vec3(4, 24, 2), new Vec3(0, 24, 0),
				new Vec3(0, 0, 0));

		MobProjectModel result = GeometryEngine.apply(model, List.of(createBone, createCuboid));

		Bone torso = result.bones().get(0);
		Cuboid body = result.cuboids().get(0);
		assertThat(body.boneId()).isEqualTo(torso.id());
		assertThat(body.boneId()).isNotEqualTo("tmp-torso"); // nunca el tempRef ni un id provisto externamente
		assertThat(UUID.fromString(body.boneId())).isNotNull();
	}

	// -- AC #2: rechazo atómico (whitelist ya cubierta a nivel JSON; aquí payload inválido) --

	@Test
	void batchConUnaOperacionInvalida_seRechazaCompletoYElModeloOriginalNoSeToca() {
		String boneId = UUID.randomUUID().toString();
		String cuboidId = UUID.randomUUID().toString();
		MobProjectModel model = GeometryFixtures.modelWithBoneAndCuboid(boneId, cuboidId);
		int bonesBefore = model.bones().size();
		int cuboidsBefore = model.cuboids().size();

		GeometryOperation valid = new MoveCuboid(cuboidId, new Vec3(1, 0, 0));
		GeometryOperation invalid = new ResizeCuboid(cuboidId, new Vec3(1, 0, 1)); // scale.y = 0 -> inválido
		List<GeometryOperation> operations = List.of(valid, invalid);

		assertThatThrownBy(() -> GeometryEngine.apply(model, operations)).isInstanceOf(GeometryValidationException.class);

		// El modelo de entrada es inmutable y nunca se tocó -- sigue igual.
		assertThat(model.bones()).hasSize(bonesBefore);
		assertThat(model.cuboids()).hasSize(cuboidsBefore);
		assertThat(model.cuboids().get(0).from()).isEqualTo(new Vec3(-4, 12, -2));
	}

	@Test
	void operacionConReferenciaNoResuelta_seRechaza() {
		MobProjectModel model = GeometryFixtures.modelWithBoneAndCuboid(
				UUID.randomUUID().toString(), UUID.randomUUID().toString());
		GeometryOperation op = new MoveCuboid("no-existe-ni-es-tempref", new Vec3(1, 0, 0));
		List<GeometryOperation> operations = List.of(op);

		assertThatThrownBy(() -> GeometryEngine.apply(model, operations)).isInstanceOf(GeometryValidationException.class);
	}

	// -- AC #4: resizeCuboid / moveCuboid con dimensión resultante <= 0 --

	@Test
	void resizeCuboid_escalaAlrededorDelCentroManteniendoloFijo() {
		String boneId = UUID.randomUUID().toString();
		String cuboidId = UUID.randomUUID().toString();
		MobProjectModel model = GeometryFixtures.modelWithBoneAndCuboid(boneId, cuboidId);
		// from=(-4,12,-2) to=(4,24,2) -> centro=(0,18,0), tamaño=(8,12,4)
		GeometryOperation op = new ResizeCuboid(cuboidId, new Vec3(1.5, 1.0, 2.0));

		MobProjectModel result = GeometryEngine.apply(model, List.of(op));

		Cuboid resized = result.cuboids().get(0);
		// nuevo tamaño = (12, 12, 8) centrado en (0,18,0)
		assertVec3Close(resized.from(), -6, 12, -4);
		assertVec3Close(resized.to(), 6, 24, 4);
	}

	@Test
	void resizeCuboid_conScaleNoPositivoSeRechaza() {
		String boneId = UUID.randomUUID().toString();
		String cuboidId = UUID.randomUUID().toString();
		MobProjectModel model = GeometryFixtures.modelWithBoneAndCuboid(boneId, cuboidId);
		GeometryOperation op = new ResizeCuboid(cuboidId, new Vec3(1, 0, 1));
		List<GeometryOperation> operations = List.of(op);

		assertThatThrownBy(() -> GeometryEngine.apply(model, operations)).isInstanceOf(GeometryValidationException.class);
	}

	@Test
	void moveCuboid_trasladaFromToYOrigenPorIgualSinCambiarDimensiones() {
		String boneId = UUID.randomUUID().toString();
		String cuboidId = UUID.randomUUID().toString();
		MobProjectModel model = GeometryFixtures.modelWithBoneAndCuboid(boneId, cuboidId);
		GeometryOperation op = new MoveCuboid(cuboidId, new Vec3(-0.5, 0.25, 0));

		MobProjectModel result = GeometryEngine.apply(model, List.of(op));

		Cuboid moved = result.cuboids().get(0);
		assertVec3Close(moved.from(), -4.5, 12.25, -2);
		assertVec3Close(moved.to(), 3.5, 24.25, 2);
		assertVec3Close(moved.origin(), -0.5, 24.25, 0);
	}

	@Test
	void moveCuboid_laGuardaDeDimensionCompartidaConResizeCuboidRechazaDimensionNoPositiva() {
		// moveCuboid es traslación rígida (decisión de producto, ver Hecho
		// del ticket): un delta normal nunca degenera las dimensiones, así
		// que este caso no es alcanzable end-to-end vía una operación
		// moveCuboid real. La guarda que AC #4 exige para "resizeCuboid/moveCuboid"
		// es la MISMA función compartida (validatePositiveDimensions,
		// package-private) que moveCuboid invoca tras trasladar -- se
		// ejercita aquí directamente, sin reflexión.
		Vec3 degenerateFrom = new Vec3(0, 0, 0);
		Vec3 degenerateTo = new Vec3(0, 4, 4);

		assertThatThrownBy(() -> GeometryEngine.validatePositiveDimensions(degenerateFrom, degenerateTo, "moveCuboid"))
				.isInstanceOf(GeometryValidationException.class);
	}

	// -- AC #1 (rotación) -- rotateCuboid / setBonePivot / setBoneRotation, verificados con CoordinateSystem --

	@Test
	void rotateCuboid_sumaDeltaYElResultadoCoincideConCoordinateSystemContract() throws IOException {
		SingleCase fixture = loadCoordFixture().singleAxisRotation();
		String boneId = UUID.randomUUID().toString();
		String cuboidId = UUID.randomUUID().toString();
		Bone bone = new Bone(boneId, "bone", null, new Vec3(0, 0, 0), new Vec3(0, 0, 0));
		Cuboid cuboid = new Cuboid(
				cuboidId, "cuboid", boneId, new Vec3(0, 0, 0), new Vec3(2, 2, 2), Vec3.fromArray(fixture.origin()),
				new Vec3(0, 0, 0), GeometryFixtures.placeholderFaces());
		MobProjectModel model = withBoneAndCuboid(bone, cuboid);

		// Aplica la rotación en dos deltas (30 + 60) -- verifica que el
		// motor ACUMULA correctamente, no solo que "funciona" con un único delta.
		double z = fixture.rotationDeg()[2];
		GeometryOperation firstHalf = new RotateCuboid(cuboidId, new Vec3(0, 0, z / 2));
		GeometryOperation secondHalf = new RotateCuboid(cuboidId, new Vec3(0, 0, z / 2));

		MobProjectModel result = GeometryEngine.apply(model, List.of(firstHalf, secondHalf));

		Cuboid rotated = result.cuboids().get(0);
		assertVec3Close(rotated.rotation(), fixture.rotationDeg()[0], fixture.rotationDeg()[1], fixture.rotationDeg()[2]);

		// El efecto esperado (dónde termina un punto del mundo) se calcula
		// con la MISMA fórmula que usa el resto del sistema (CoordinateSystemContract) --
		// no una reimplementación propia del Geometry Engine.
		Vec3 worldPoint = CoordinateSystem.applyPivotRotation(
				Vec3.fromArray(fixture.point()), rotated.origin(), rotated.rotation());
		assertVec3Close(worldPoint, fixture.expected()[0], fixture.expected()[1], fixture.expected()[2]);
	}

	@Test
	void setBonePivotYSetBoneRotation_reemplazanAbsolutoYComponenConElBoneViaCoordinateSystemContract()
			throws IOException {
		ParentChildCase fixture = loadCoordFixture().parentChildComposition();
		String boneId = UUID.randomUUID().toString();
		String cuboidId = UUID.randomUUID().toString();
		Bone bone = new Bone(boneId, "bone", null, new Vec3(0, 0, 0), new Vec3(0, 0, 0));
		Cuboid cuboid = new Cuboid(
				cuboidId, "cuboid", boneId, new Vec3(0, 0, 0), new Vec3(1, 1, 1),
				Vec3.fromArray(fixture.cuboidOrigin()), Vec3.fromArray(fixture.cuboidRotationDeg()),
				GeometryFixtures.placeholderFaces());
		MobProjectModel model = withBoneAndCuboid(bone, cuboid);

		GeometryOperation setPivot = new SetBonePivot(boneId, Vec3.fromArray(fixture.boneOrigin()));
		GeometryOperation setRotation = new SetBoneRotation(boneId, Vec3.fromArray(fixture.boneRotationDeg()));

		MobProjectModel result = GeometryEngine.apply(model, List.of(setPivot, setRotation));

		Bone updatedBone = result.bones().get(0);
		Cuboid updatedCuboid = result.cuboids().get(0);
		assertVec3Close(updatedBone.pivot(), fixture.boneOrigin()[0], fixture.boneOrigin()[1], fixture.boneOrigin()[2]);
		assertVec3Close(
				updatedBone.rotation(), fixture.boneRotationDeg()[0], fixture.boneRotationDeg()[1],
				fixture.boneRotationDeg()[2]);

		// Camino 1: composición real bone->cuboid del contrato, con los
		// valores que quedaron guardados tras aplicar las operaciones.
		Vec3 viaComposition = CoordinateSystem.composeBoneChildTransform(
				Vec3.fromArray(fixture.point()), updatedCuboid.origin(), updatedCuboid.rotation(),
				updatedBone.pivot(), updatedBone.rotation());

		// Camino 2 (independiente, mismo patrón que CoordinateSystemTest):
		// como ambos pivotes coinciden y ambas rotaciones son en Z, debe
		// coincidir con una única rotación de ángulo combinado.
		double combinedZ = fixture.boneRotationDeg()[2] + fixture.cuboidRotationDeg()[2];
		Vec3 viaCombinedAngle =
				CoordinateSystem.applyPivotRotation(Vec3.fromArray(fixture.point()), updatedCuboid.origin(), new Vec3(0, 0, combinedZ));

		assertVec3Close(viaComposition, viaCombinedAngle.x(), viaCombinedAngle.y(), viaCombinedAngle.z());
	}

	// -- parentBone --------------------------------------------------------

	@Test
	void parentBone_reasignaElPadre() {
		String rootId = UUID.randomUUID().toString();
		String childId = UUID.randomUUID().toString();
		String newParentId = UUID.randomUUID().toString();
		Bone root = new Bone(rootId, "root", null, new Vec3(0, 0, 0), new Vec3(0, 0, 0));
		Bone child = new Bone(childId, "child", rootId, new Vec3(0, 0, 0), new Vec3(0, 0, 0));
		Bone newParent = new Bone(newParentId, "newParent", null, new Vec3(0, 0, 0), new Vec3(0, 0, 0));
		MobProjectModel model = withBones(root, child, newParent);

		MobProjectModel result = GeometryEngine.apply(model, List.of(new ParentBone(childId, newParentId)));

		Bone updatedChild =
				result.bones().stream().filter(b -> b.id().equals(childId)).findFirst().orElseThrow();
		assertThat(updatedChild.parentId()).isEqualTo(newParentId);
	}

	@Test
	void parentBone_autoParentarseSeRechaza() {
		String id = UUID.randomUUID().toString();
		Bone bone = new Bone(id, "bone", null, new Vec3(0, 0, 0), new Vec3(0, 0, 0));
		MobProjectModel model = withBones(bone);
		List<GeometryOperation> operations = List.of(new ParentBone(id, id));

		assertThatThrownBy(() -> GeometryEngine.apply(model, operations)).isInstanceOf(GeometryValidationException.class);
	}

	@Test
	void parentBone_queCrearíaUnCicloSeRechaza() {
		String aId = UUID.randomUUID().toString();
		String bId = UUID.randomUUID().toString();
		Bone a = new Bone(aId, "a", null, new Vec3(0, 0, 0), new Vec3(0, 0, 0));
		Bone b = new Bone(bId, "b", aId, new Vec3(0, 0, 0), new Vec3(0, 0, 0)); // b es hijo de a
		MobProjectModel model = withBones(a, b);

		// Intentar hacer que 'a' (padre de 'b') pase a ser hijo de 'b' -> ciclo.
		List<GeometryOperation> operations = List.of(new ParentBone(aId, bId));

		assertThatThrownBy(() -> GeometryEngine.apply(model, operations)).isInstanceOf(GeometryValidationException.class);
	}

	// -- AC #5: removeCuboid deja referencias válidas ----------------------

	@Test
	void removeCuboid_eliminaElCuboidYCascadeaLasUvRegionsQueLoReferencian() {
		String boneId = UUID.randomUUID().toString();
		String cuboidId = UUID.randomUUID().toString();
		MobProjectModel model = GeometryFixtures.modelWithBoneCuboidAndUvRegion(boneId, cuboidId);
		assertThat(model.uv().regions()).hasSize(1); // precondición: sí había una región referenciándolo

		MobProjectModel result = GeometryEngine.apply(model, List.of(new RemoveCuboid(cuboidId)));

		assertThat(result.cuboids()).isEmpty();
		assertThat(result.uv().regions()).isEmpty();
		assertThat(result.bones()).hasSize(1); // el bone (padre) sigue intacto y válido
	}

	// -- helpers -------------------------------------------------------------

	private static void assertVec3Close(Vec3 actual, double x, double y, double z) {
		assertThat(actual.x()).isCloseTo(x, within(1e-9));
		assertThat(actual.y()).isCloseTo(y, within(1e-9));
		assertThat(actual.z()).isCloseTo(z, within(1e-9));
	}

	private static MobProjectModel withBoneAndCuboid(Bone bone, Cuboid cuboid) {
		MobProjectModel base = GeometryFixtures.emptyModel();
		return new MobProjectModel(
				base.mobId(), base.projectId(), base.name(), base.baseType(), base.units(), List.of(bone),
				List.of(cuboid), base.texture(), base.uv(), base.animations(), base.exportSettings(),
				base.referenceImages());
	}

	private static MobProjectModel withBones(Bone... bones) {
		MobProjectModel base = GeometryFixtures.emptyModel();
		return new MobProjectModel(
				base.mobId(), base.projectId(), base.name(), base.baseType(), base.units(), List.of(bones), List.of(),
				base.texture(), base.uv(), base.animations(), base.exportSettings(), base.referenceImages());
	}

}
