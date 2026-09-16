package com.galgothstudio.backend.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.galgothstudio.backend.domain.uv.TexelDensity;
import org.junit.jupiter.api.Test;

/**
 * `TextureResolution` -- ticket 103 (HU-10). El tamaño elegido ACOTA la
 * densidad de texel; el atlas sigue saliendo del packing real (§7), nunca
 * se fija de antemano -- decisión explícita del Product Owner, ver el
 * Javadoc del enum.
 */
class TextureResolutionTest {

	/** Atlas simulado: 64px de lado a X1, el doble a X2 -- la relación real que produce duplicar la densidad lineal. */
	private static int atlasSideFor(TexelDensity density) {
		return density == TexelDensity.X2 ? 128 : 64;
	}

	@Test
	void eligeLaMayorDensidadQueEntraEnElTope() {
		// A X2 el atlas mide 128: entra justo en el tope de 128, no en el de 64.
		assertThat(TextureResolution.MAX_128.highestDensityWithin(TextureResolutionTest::atlasSideFor)).isEqualTo(TexelDensity.X2);
		assertThat(TextureResolution.MAX_64.highestDensityWithin(TextureResolutionTest::atlasSideFor)).isEqualTo(TexelDensity.X1);
	}

	@Test
	void unTopeMasGrandeNuncaInflaElAtlas_sigueSiendoConsecuenciaDelPacking() {
		// Con 256 de tope, la mayor densidad disponible sigue siendo X2 -- el
		// tope no "llena" el atlas hasta 256, solo permite más densidad.
		assertThat(TextureResolution.MAX_256.highestDensityWithin(TextureResolutionTest::atlasSideFor)).isEqualTo(TexelDensity.X2);
	}

	@Test
	void siNiLaDensidadMasBajaEntraEnElTope_devuelveLaMasBaja_nuncaFalla() {
		// Un mob con mucha geometría: incluso a X1 el atlas excede cualquier tope.
		assertThat(TextureResolution.MAX_64.highestDensityWithin(_ -> 512)).isEqualTo(TexelDensity.X1);
	}

	@Test
	void cadaOpcionExponeSuTopeEnPixeles() {
		assertThat(TextureResolution.MAX_64.maxAtlasSidePx()).isEqualTo(64);
		assertThat(TextureResolution.MAX_128.maxAtlasSidePx()).isEqualTo(128);
		assertThat(TextureResolution.MAX_256.maxAtlasSidePx()).isEqualTo(256);
	}

}
