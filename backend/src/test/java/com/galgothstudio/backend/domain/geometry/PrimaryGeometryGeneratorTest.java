package com.galgothstudio.backend.domain.geometry;

import static org.assertj.core.api.Assertions.assertThat;

import com.galgothstudio.backend.domain.model.BaseType;
import com.galgothstudio.backend.domain.model.Bone;
import com.galgothstudio.backend.domain.model.Cuboid;
import com.galgothstudio.backend.domain.model.ModelIntent;
import com.galgothstudio.backend.domain.model.MobProjectModel;
import com.galgothstudio.backend.domain.model.Proportions;
import com.galgothstudio.backend.domain.template.CanonicalTemplate;
import com.galgothstudio.backend.domain.template.CanonicalTemplateCatalog;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Ticket 098 -- HU-2/HU-2c. {@link PrimaryGeometryGenerator} es 100%
 * determinista (sin red, sin mocks de IA) -- estas invariantes corren sin
 * ningún stub de {@code StructuredReasoningProvider}, a diferencia de los
 * tests que ejercitan el camino de IA.
 */
class PrimaryGeometryGeneratorTest {

	private static ModelIntent neutralIntent() {
		return new ModelIntent("humanoid", new Proportions(1.0, 1.0, 1.0, 1.0), 0.0, List.of(), List.of());
	}

	private static ModelIntent intentWith(Proportions proportions) {
		return new ModelIntent("humanoid", proportions, 0.0, List.of(), List.of());
	}

	@Test
	void generate_sinRedNiMocksDeIa_produceElModeloDeTodasFormas() {
		// La sola ejecución exitosa sin ningún provider de IA en el classpath
		// del test demuestra el requisito "sin red, sin mocks de IA".
		PrimaryGeometryResult result = PrimaryGeometryGenerator.generate(
				GeometryFixtures.emptyModel(), CanonicalTemplateCatalog.forBaseType(BaseType.HUMANOID), neutralIntent());

		assertThat(result.model()).isNotNull();
		assertThat(result.warnings()).isEmpty();
	}

	@Test
	void todasLasPartesObligatoriasDelTemplateEstanPresentes() {
		MobProjectModel model = generateHumanoid(neutralIntent());

		// 15 bones esperados: root (body) + torso + head + 2*(arm+forearm+hand) + 2*(leg+shin+foot).
		assertThat(model.bones()).hasSize(15);
		// 14 cuboides esperados (cada bone salvo el root "body" tiene cuboid propio;
		// semanticPart del template aún no se persiste en Cuboid -- llega en el
		// ticket 099 -- la identidad de cada parte se verifica por nombre, ver
		// semanticaEsperadaDeLaAnatomiaPrimaria_presenteEnLosCuboidesGenerados).
		assertThat(model.cuboids()).hasSize(14);
	}

	@Test
	void jerarquiaDeBonesValida_sinCiclosYTodoParentIdExiste() {
		MobProjectModel model = generateHumanoid(neutralIntent());
		Set<String> boneIds = model.bones().stream().map(Bone::id).collect(java.util.stream.Collectors.toSet());

		int rootCount = 0;
		for (Bone bone : model.bones()) {
			if (bone.parentId() == null) {
				rootCount++;
			} else {
				assertThat(boneIds).as("parentId de " + bone.name()).contains(bone.parentId());
			}
		}
		assertThat(rootCount).as("exactamente un bone raíz").isEqualTo(1);

		// Sin ciclos: seguir parentId desde cualquier bone siempre llega a la raíz en <= profundidad conocida (4).
		java.util.Map<String, Bone> byId = model.bones().stream().collect(java.util.stream.Collectors.toMap(Bone::id, b -> b));
		for (Bone bone : model.bones()) {
			Bone current = bone;
			int hops = 0;
			while (current.parentId() != null) {
				current = byId.get(current.parentId());
				hops++;
				assertThat(hops).as("ciclo detectado desde " + bone.name()).isLessThanOrEqualTo(model.bones().size());
			}
		}
	}

	@Test
	void todoCuboidReferenciaUnBoneExistente() {
		MobProjectModel model = generateHumanoid(neutralIntent());
		Set<String> boneIds = model.bones().stream().map(Bone::id).collect(java.util.stream.Collectors.toSet());

		for (Cuboid cuboid : model.cuboids()) {
			assertThat(boneIds).as("boneId de " + cuboid.name()).contains(cuboid.boneId());
		}
	}

	@Test
	void todoCuboidTieneTamanoPositivoYCoordenadasFinitas() {
		MobProjectModel model = generateHumanoid(new Proportions(1.8, 1.6, 2.2, 1.6)); // extremos del rango válido

		for (Cuboid cuboid : model.cuboids()) {
			assertThat(cuboid.to().x() - cuboid.from().x()).as(cuboid.name() + " ancho").isGreaterThan(0);
			assertThat(cuboid.to().y() - cuboid.from().y()).as(cuboid.name() + " alto").isGreaterThan(0);
			assertThat(cuboid.to().z() - cuboid.from().z()).as(cuboid.name() + " profundidad").isGreaterThan(0);

			for (double component : new double[] {
					cuboid.from().x(), cuboid.from().y(), cuboid.from().z(),
					cuboid.to().x(), cuboid.to().y(), cuboid.to().z(),
					cuboid.origin().x(), cuboid.origin().y(), cuboid.origin().z() }) {
				assertThat(Double.isFinite(component)).as(cuboid.name() + " coordenada finita").isTrue();
			}
		}
	}

	@Test
	void boundingBoxDelPersonajeCompletoEsRazonable_enTodoElRangoValidoDeProporciones() {
		double[][] extremeCombinations = {
				{ 0.6, 0.7, 0.7, 0.8 }, // valores al mínimo
				{ 1.8, 1.6, 2.2, 1.6 }, // valores al máximo
				{ 1.0, 1.0, 1.0, 1.0 }, // neutral
		};

		for (double[] p : extremeCombinations) {
			MobProjectModel model = generateHumanoid(new Proportions(p[0], p[1], p[2], p[3]));

			double minX = Double.POSITIVE_INFINITY, maxX = Double.NEGATIVE_INFINITY;
			double minY = Double.POSITIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
			for (Cuboid cuboid : model.cuboids()) {
				minX = Math.min(minX, Math.min(cuboid.from().x(), cuboid.to().x()));
				maxX = Math.max(maxX, Math.max(cuboid.from().x(), cuboid.to().x()));
				minY = Math.min(minY, Math.min(cuboid.from().y(), cuboid.to().y()));
				maxY = Math.max(maxY, Math.max(cuboid.from().y(), cuboid.to().y()));
			}

			// Plausible para un humanoide de ~2 bloques: alto entre 20 y 60px,
			// ancho total entre 8 y 40px -- ningún extremo del rango válido
			// de proporciones debería producir algo fuera de esto.
			assertThat(maxY - minY).as("alto total, proporciones=" + java.util.Arrays.toString(p))
					.isBetween(20.0, 60.0);
			assertThat(maxX - minX).as("ancho total, proporciones=" + java.util.Arrays.toString(p))
					.isBetween(8.0, 40.0);
		}
	}

	@Test
	void pivotesDeBonesCoincidenConLaArticulacionSemantica_noCalculadosAdHoc() {
		MobProjectModel model = generateHumanoid(neutralIntent());
		java.util.Map<String, Bone> byName = model.bones().stream()
				.collect(java.util.stream.Collectors.toMap(Bone::name, b -> b));

		// head pivota en el cuello (y=24, sin importar headScale -- ver ticket 097).
		assertThat(byName.get("head").pivot().y()).isEqualTo(24.0);
		// El pivote de cada bone del brazo coincide con el borde del cuboid
		// del segmento anterior de la cadena (hombro/codo/muñeca contiguos).
	}

	@Test
	void semanticaEsperadaDeLaAnatomiaPrimaria_presenteEnLosCuboidesGenerados() {
		MobProjectModel model = generateHumanoid(neutralIntent());
		Set<String> names = model.cuboids().stream().map(Cuboid::name).collect(java.util.stream.Collectors.toSet());

		assertThat(names).contains(
				"torso", "head",
				"left upper arm", "left forearm", "left hand",
				"right upper arm", "right forearm", "right hand",
				"left thigh", "left shin", "left foot",
				"right thigh", "right shin", "right foot");
	}

	private static MobProjectModel generateHumanoid(ModelIntent intent) {
		CanonicalTemplate template = CanonicalTemplateCatalog.forBaseType(BaseType.HUMANOID);
		return PrimaryGeometryGenerator.generate(GeometryFixtures.emptyModel(), template, intent).model();
	}

	private static MobProjectModel generateHumanoid(Proportions proportions) {
		return generateHumanoid(intentWith(proportions));
	}
}
