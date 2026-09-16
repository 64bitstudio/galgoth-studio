package com.galgothstudio.backend.domain.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.galgothstudio.backend.domain.uv.TexelDensity;
import java.util.function.ToIntFunction;

/**
 * Resolución de textura elegida en Configuración -- ticket 103 (HU-10 de
 * `docs/definiciones/anatomia-por-capas-generacion-mobs.md`).
 *
 * <p><b>Es un TOPE, no un tamaño exacto -- decisión explícita del Product
 * Owner (ticket 103)</b>: el Diseño técnico §7 de
 * `docs/definiciones/galgoth-studio-fase3-textura.md` (cerrado por el PO
 * el 10 sep 2026) fija que el atlas es una CONSECUENCIA del packing a una
 * {@link TexelDensity} dada, "nunca un valor fijo elegido de antemano".
 * El selector de la UI, en cambio, ofrece tamaños absolutos
 * (64×64/128×128/256×256) -- cablearlo literalmente contradiría §7. La
 * resolución del conflicto, consultada y decidida por el PO: el tamaño
 * elegido acota, no fija. {@link #highestDensityWithin} elige la MAYOR
 * densidad cuyo atlas resultante entra en ese tope; el atlas sigue
 * saliendo del packing real, como manda §7.
 *
 * <p>Consecuencia aceptada y documentada de esa decisión: en un mob
 * chico, dos topes distintos pueden dar el mismo atlas (si a la densidad
 * más alta ya entra en el tope más chico, subir el tope no cambia nada).
 * El tope nunca "infla" un atlas para llenarlo -- eso sería exactamente
 * el "valor fijo elegido de antemano" que §7 prohíbe.
 *
 * @param maxAtlasSidePx tope (px por lado) que el atlas resultante no debería superar.
 */
public enum TextureResolution {

	@JsonProperty("64") MAX_64(64),
	@JsonProperty("128") MAX_128(128),
	@JsonProperty("256") MAX_256(256);

	private final int maxAtlasSidePx;

	TextureResolution(int maxAtlasSidePx) {
		this.maxAtlasSidePx = maxAtlasSidePx;
	}

	public int maxAtlasSidePx() {
		return maxAtlasSidePx;
	}

	/**
	 * La mayor {@link TexelDensity} cuyo atlas (ya calculado por el caller
	 * vía {@code AtlasResolutionCalculator}) entra en este tope.
	 *
	 * <p><b>Nunca falla el job</b>: si ni siquiera la densidad más baja
	 * entra en el tope (un mob con mucha geometría y un tope chico), se
	 * devuelve igual la más baja -- el atlas resultante excede el tope
	 * elegido, y eso se registra como advertencia arriba (mismo criterio
	 * que el presupuesto de {@link GeometryDetail}: orientativo, nunca una
	 * cuota rígida que tumbe la generación).
	 *
	 * @param atlasSideForDensity dado un {@link TexelDensity}, el lado mayor (px) del atlas que resulta de empaquetar a esa densidad.
	 */
	public TexelDensity highestDensityWithin(ToIntFunction<TexelDensity> atlasSideForDensity) {
		TexelDensity[] fromHighest = {TexelDensity.X2, TexelDensity.X1};
		for (TexelDensity density : fromHighest) {
			if (atlasSideForDensity.applyAsInt(density) <= maxAtlasSidePx) {
				return density;
			}
		}
		return TexelDensity.X1;
	}

}
