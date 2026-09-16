package com.galgothstudio.backend.domain.uv;

/**
 * Perfil de densidad de texel para el cálculo de footprint de {@code AutoUv}
 * -- ticket 042, Diseño técnico §7 de
 * `docs/definiciones/galgoth-studio-fase3-textura.md` (VERSIÓN FINAL,
 * cerrada definitivamente por el PO el 10 sep 2026): {@code x1}/{@code x2}
 * NO son una dimensión de atlas -- son un multiplicador lineal de texels
 * por unidad de modelo Minecraft/Blockbench que alimenta
 * {@link BoxUvMath#footprintOf(com.galgothstudio.backend.domain.model.Cuboid, TexelDensity)};
 * el atlas resultante es una CONSECUENCIA del packing a esa densidad
 * (ver {@link AtlasResolutionCalculator}), nunca un valor elegido de
 * antemano.
 *
 * <p>Ejemplo (§7): una cara física de 8×8 unidades produce un footprint
 * de 8×8 texels a {@code X1}, o 16×16 texels a {@code X2} -- el mismo
 * cuboid, empaquetado a densidades distintas, produce footprints de
 * tamaño distinto.
 */
public enum TexelDensity {

	/** 1 texel por unidad de modelo -- densidad estándar/default, tanto para presets Minecraft como para {@code BaseType.CUSTOM}. */
	X1(1),

	/** 2 texels por unidad de modelo (el doble de densidad lineal) -- upgrade explícito, nunca automático. */
	X2(2),

	/**
	 * 4 texels por unidad de modelo -- ticket 109
	 * (`docs/definiciones/densidad-de-texel-y-resolucion-de-textura.md`).
	 *
	 * <p>Agregado por un hallazgo medido contra un mob real en `studio-dev`
	 * (`Carcomido v2`, 46 cuboides): a {@link #X1}, 178 de sus 276 caras
	 * quedaban bajo 16 px² y los rasgos que dan identidad al personaje
	 * recibían 1-2 téxeles -- un ojo de 2×1.2 unidades no entra en 2
	 * píxeles, así que el generador de imagen solo podía devolver ruido con
	 * la paleta correcta. {@link #X2} no alcanzaba: dejaba los colmillos en
	 * 2×2. A X4 ese mismo colmillo recibe 4×4 y el ojo 8×5.
	 */
	X4(4);

	private final int texelsPerUnit;

	TexelDensity(int texelsPerUnit) {
		this.texelsPerUnit = texelsPerUnit;
	}

	public int texelsPerUnit() {
		return texelsPerUnit;
	}

}
