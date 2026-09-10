package com.galgothstudio.backend.domain.uv;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.galgothstudio.backend.domain.model.Cuboid;
import com.galgothstudio.backend.domain.model.CuboidFaces;
import com.galgothstudio.backend.domain.model.Face;
import com.galgothstudio.backend.domain.model.Vec3;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Ticket 042, Diseño técnico §7 de
 * `docs/definiciones/galgoth-studio-fase3-textura.md`: el atlas inicial
 * es SIEMPRE la potencia de 2 que contiene el footprint empaquetado de
 * TODOS los cuboids a la densidad elegida -- nunca un valor fijo
 * hardcodeado.
 */
class AtlasResolutionCalculatorTest {

	private static Cuboid cube(String id, double size) {
		double half = size / 2;
		Face placeholder = new Face(null, null);
		CuboidFaces emptyFaces = new CuboidFaces(placeholder, placeholder, placeholder, placeholder, placeholder, placeholder);
		return new Cuboid(
				id, "cube-" + id, "bone-1", new Vec3(-half, -half, -half), new Vec3(half, half, half), new Vec3(0, 0, 0),
				new Vec3(0, 0, 0), emptyFaces);
	}

	@Test
	void unSoloCuboid_elAtlasEsLaPotenciaDe2QueContieneSuFootprint() {
		// footprint de un cubo 8x8x8 a X1 = 32x16 -- ya es potencia de 2 en
		// ambos ejes, así que el atlas debe ser EXACTAMENTE eso, nunca más
		// grande sin necesidad ("el menor atlas permitido que pueda
		// contenerlos").
		Cuboid head = cube("head", 8);

		AtlasResolutionCalculator.AtlasSize atlas = AtlasResolutionCalculator.computeAtlas(List.of(head), TexelDensity.X1);

		assertThat(atlas).isEqualTo(new AtlasResolutionCalculator.AtlasSize(32, 16));
	}

	@Test
	void unFootprintQueNoEsPotenciaDe2_seRedondeaHaciaArriba() {
		// footprint de un cubo 12x12x12 a X1 = 2*(12+12)=48 de ancho,
		// (12+12)=24 de alto -- ninguno de los dos es potencia de 2.
		Cuboid body = cube("body", 12);

		AtlasResolutionCalculator.AtlasSize atlas = AtlasResolutionCalculator.computeAtlas(List.of(body), TexelDensity.X1);

		assertThat(atlas).isEqualTo(new AtlasResolutionCalculator.AtlasSize(64, 32));
	}

	@Test
	void aDensidadX2_elAtlasResultanteEsMayorQueADensidadX1_paraLaMismaGeometria() {
		// AC del ticket: "el atlas resultante cambia de tamaño en
		// consecuencia (test que compara los dos tamaños de atlas para la
		// misma geometría)".
		List<Cuboid> cuboids = List.of(cube("head", 8), cube("body", 12));

		AtlasResolutionCalculator.AtlasSize atlasX1 = AtlasResolutionCalculator.computeAtlas(cuboids, TexelDensity.X1);
		AtlasResolutionCalculator.AtlasSize atlasX2 = AtlasResolutionCalculator.computeAtlas(cuboids, TexelDensity.X2);

		assertThat(atlasX2.width()).isGreaterThan(atlasX1.width());
		assertThat(atlasX2.height()).isGreaterThan(atlasX1.height());
	}

	@Test
	void sinCuboids_noHayAtlasQueCalcular_seReportaExplicitamente() {
		assertThatThrownBy(() -> AtlasResolutionCalculator.computeAtlas(List.of(), TexelDensity.X1))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void elAtlasCalculado_siempreAlcanzaParaElPackingRealPosterior() {
		// Un mob más realista (varios cuboids de tamaños distintos, como el
		// rig default de MockReasoningProvider) -- el atlas calculado nunca
		// debe hacer que AlphaAutoPackStrategy lance UvAtlasOverflowException
		// al empaquetar la MISMA lista de cuboids dentro de ese atlas.
		List<Cuboid> cuboids = List.of(
				cube("torso", 10), cube("head", 8), cube("arm-left", 6), cube("arm-right", 6), cube("leg-left", 6),
				cube("leg-right", 6));

		AtlasResolutionCalculator.AtlasSize atlas = AtlasResolutionCalculator.computeAtlas(cuboids, TexelDensity.X1);

		UvLayoutStrategy.Result result = new AlphaAutoPackStrategy().layout(cuboids, atlas.width(), atlas.height());
		assertThat(result.regions()).hasSize(cuboids.size() * 6);
	}

	@Test
	void nextPowerOfTwo_valoresYaPotenciaDe2SeConservan_elRestoRedondeaHaciaArriba() {
		assertThat(AtlasResolutionCalculator.nextPowerOfTwo(1)).isEqualTo(1);
		assertThat(AtlasResolutionCalculator.nextPowerOfTwo(16)).isEqualTo(16);
		assertThat(AtlasResolutionCalculator.nextPowerOfTwo(17)).isEqualTo(32);
		assertThat(AtlasResolutionCalculator.nextPowerOfTwo(0)).isEqualTo(1);
	}

}
