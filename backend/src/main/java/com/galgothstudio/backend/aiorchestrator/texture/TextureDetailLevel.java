package com.galgothstudio.backend.aiorchestrator.texture;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Locale;

/**
 * Control de nivel de detalle del generador de textura por IA (mockup
 * 08, HU-36 AC #1: "un control de nivel de detalle (bajo-alto)"). Sin un
 * campo dedicado en {@code ImageGenerationProvider.TextureGenerationSheetRequest}
 * (051, mismo criterio que {@link TextureStyle} -- ver su Javadoc): se
 * pliega como instrucción de texto determinista al final del prompt
 * compuesto (`TextureGenerationService`, 054), nunca un parámetro
 * estructurado de la API de OpenAI.
 */
public enum TextureDetailLevel {

	@JsonProperty("low") LOW("Nivel de detalle BAJO -- formas simples, pocos matices de color, sin micro-detalle."),
	@JsonProperty("medium") MEDIUM("Nivel de detalle MEDIO -- balance entre simplicidad y detalle."),
	@JsonProperty("high") HIGH("Nivel de detalle ALTO -- máximo detalle/textura visible dentro del límite de píxeles del atlas.");

	private final String promptInstruction;

	TextureDetailLevel(String promptInstruction) {
		this.promptInstruction = promptInstruction;
	}

	public String promptInstruction() {
		return promptInstruction;
	}

	public String wireValue() {
		return name().toLowerCase(Locale.ROOT);
	}

	public static TextureDetailLevel fromWireValue(String value) {
		for (TextureDetailLevel level : values()) {
			if (level.wireValue().equals(value)) {
				return level;
			}
		}
		throw new InvalidTextureGenerationRequestException(
				"detailLevel inválido: '" + value + "' -- valores aceptados: low, medium, high.");
	}

}
