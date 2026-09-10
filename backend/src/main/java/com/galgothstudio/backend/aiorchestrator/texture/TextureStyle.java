package com.galgothstudio.backend.aiorchestrator.texture;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Locale;

/**
 * Las 4 opciones de estilo del selector de usuario (mockup 08, HU-36 AC
 * #1, Diseño técnico §11/§21 de
 * `docs/definiciones/galgoth-studio-fase3-textura.md`) -- fiel al mockup,
 * no las 3 del master prompt original (decisión explícita del PO, ver
 * "Incluye" del documento de definición). El valor {@link #wireValue()}
 * es lo que viaja como {@code style} en
 * {@code ImageGenerationProvider.TextureGenerationSheetRequest} (051) --
 * texto libre plegado al final del prompt por el proveedor, nunca un
 * parámetro estructurado de la API de OpenAI (ver Javadoc de esa
 * interfaz).
 */
public enum TextureStyle {

	@JsonProperty("faithful") FAITHFUL("Fiel a la referencia -- respetá lo más posible los colores/formas exactos de la imagen de referencia."),
	@JsonProperty("minecraft_vanilla") MINECRAFT_VANILLA(
			"Minecraft Vanilla -- estilo pixel-art plano y saturado, coherente con las texturas oficiales de Minecraft Java Edition."),
	@JsonProperty("pixel_art") PIXEL_ART("Pixel Art -- pixel-art estilizado de baja resolución, bordes marcados, sin degradados suaves."),
	@JsonProperty("realistic") REALISTIC("Realista -- texturizado con sombreado/detalle realista, más allá del estilo plano de Minecraft.");

	private final String promptInstruction;

	TextureStyle(String promptInstruction) {
		this.promptInstruction = promptInstruction;
	}

	/** Instrucción textual determinista para el prompt -- ver Javadoc de la clase. */
	public String promptInstruction() {
		return promptInstruction;
	}

	/** Valor de wire (`style` de {@code TextureGenerationSheetRequest}) -- mismo literal que {@code @JsonProperty}, expuesto sin reflexión. */
	public String wireValue() {
		return name().toLowerCase(Locale.ROOT);
	}

	public static TextureStyle fromWireValue(String value) {
		for (TextureStyle style : values()) {
			if (style.wireValue().equals(value)) {
				return style;
			}
		}
		throw new InvalidTextureGenerationRequestException(
				"style inválido: '" + value + "' -- valores aceptados: faithful, minecraft_vanilla, pixel_art, realistic.");
	}

}
