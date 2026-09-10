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
	X2(2);

	private final int texelsPerUnit;

	TexelDensity(int texelsPerUnit) {
		this.texelsPerUnit = texelsPerUnit;
	}

	public int texelsPerUnit() {
		return texelsPerUnit;
	}

}
