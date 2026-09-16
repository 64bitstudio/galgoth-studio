package com.galgothstudio.backend.domain.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.galgothstudio.backend.domain.uv.TexelDensity;

/**
 * Densidad de textura elegida en Configuración -- ticket 109
 * (`docs/definiciones/densidad-de-texel-y-resolucion-de-textura.md`,
 * HU-3), con VoBo del PO.
 *
 * <p><b>Reemplaza a {@code TextureResolution} (ticket 103), que era un
 * TOPE de atlas.</b> Ese diseño se eliminó por dos razones medidas, no
 * por gusto: (1) el PO decidió que el atlas vuelva a ser consecuencia
 * pura del packing, sin tope (Diseño técnico §7), lo que dejaba la lógica
 * de tope como código muerto; y (2) el tope era inerte justo donde
 * importaba -- el atlas de un mob real de 46 cuboides ya medía 256 px de
 * alto a la densidad más baja, así que superaba el tope de 128 y la
 * lógica caía siempre a X1: el control daba más densidad a los mobs
 * simples y ninguna a los complejos, al revés de lo necesario.
 *
 * <p>Ahora el selector elige lo único que el usuario puede elegir de
 * verdad: cuántos téxeles recibe cada unidad de modelo. El tamaño del
 * atlas sale del packing a esa densidad, sin recortes.
 */
public enum TextureDensity {

	/** 1 téxel por unidad -- el comportamiento anterior al ticket 109. Atlas chico, detalle fino ilegible. */
	@JsonProperty("standard") STANDARD(TexelDensity.X1),

	/** 2 téxeles por unidad -- intermedio. */
	@JsonProperty("high") HIGH(TexelDensity.X2),

	/** 4 téxeles por unidad -- default: es la densidad con la que los rasgos chicos (ojos, colmillos, grietas) reciben píxeles suficientes para tener contenido. */
	@JsonProperty("max") MAX(TexelDensity.X4);

	private final TexelDensity texelDensity;

	TextureDensity(TexelDensity texelDensity) {
		this.texelDensity = texelDensity;
	}

	public TexelDensity texelDensity() {
		return texelDensity;
	}

}
