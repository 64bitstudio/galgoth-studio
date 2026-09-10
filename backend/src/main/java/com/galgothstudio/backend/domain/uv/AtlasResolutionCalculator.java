package com.galgothstudio.backend.domain.uv;

import com.galgothstudio.backend.domain.model.Cuboid;
import java.util.List;

/**
 * Calcula el tamaño de atlas que resulta de empaquetar TODOS los cuboids
 * de un mob a una {@link TexelDensity} dada -- ticket 042, Diseño técnico
 * §7 de `docs/definiciones/galgoth-studio-fase3-textura.md` (VERSIÓN
 * FINAL, cerrada definitivamente por el PO el 10 sep 2026): "se elige el
 * menor atlas permitido (potencia de 2) que pueda contenerlos" -- el
 * atlas es una CONSECUENCIA del packing, nunca un valor fijo elegido de
 * antemano (nunca, p. ej., un hardcode de 128×128).
 *
 * <p>Reutiliza {@link AlphaAutoPackStrategy#packedBoundsOf} (paquete-
 * visible desde este ticket) -- el MISMO shelf-packing determinista ya
 * verificado por 041, en vez de duplicarlo: empaqueta con el ancho
 * mínimo con el que CADA footprint individual entra en su propia fila
 * (el footprint más ancho de la lista) y toma la caja envolvente
 * resultante. El atlas final (potencia de 2 de cada eje, que por
 * construcción es &gt;= esa caja envolvente) SIEMPRE alcanza para el
 * packing real posterior, porque el shelf-packing es monótono NO
 * CRECIENTE en alto a medida que el ancho de fila crece -- empaquetar en
 * un ancho igual o mayor nunca necesita más filas.
 */
public final class AtlasResolutionCalculator {

	private AtlasResolutionCalculator() {
	}

	/** Tamaño de atlas resultante -- ancho/alto ya redondeados a la potencia de 2 que los contiene. */
	public record AtlasSize(int width, int height) {
	}

	/**
	 * @param cuboids TODOS los cuboids del mob (nunca un subconjunto) -- nunca vacío, no hay atlas que calcular para un mob sin geometría.
	 * @param density densidad de texel vigente ({@link TexelDensity#X1} por defecto, tanto para presets Minecraft como para {@code BaseType.CUSTOM}; {@link TexelDensity#X2} solo vía upgrade explícito).
	 * @return el menor atlas (potencia de 2 en cada eje) que contiene el footprint empaquetado de {@code cuboids} a {@code density}.
	 */
	public static AtlasSize computeAtlas(List<Cuboid> cuboids, TexelDensity density) {
		if (cuboids.isEmpty()) {
			throw new IllegalArgumentException(
					"No se puede calcular un atlas inicial sin cuboids -- AutoUv necesita al menos un footprint para empaquetar.");
		}

		AlphaAutoPackStrategy.PackResult packed = AlphaAutoPackStrategy.packedBoundsOf(cuboids, density);
		return new AtlasSize(nextPowerOfTwo(packed.maxRowWidth()), nextPowerOfTwo(packed.totalHeight()));
	}

	/** La potencia de 2 más chica que sea &gt;= {@code value} ("el menor atlas permitido que pueda contenerlo", §7) -- un valor ya potencia de 2 se conserva tal cual, nunca se duplica sin necesidad. */
	static int nextPowerOfTwo(int value) {
		if (value <= 1) {
			return 1;
		}
		int highestOneBit = Integer.highestOneBit(value);
		return highestOneBit == value ? value : highestOneBit << 1;
	}

}
