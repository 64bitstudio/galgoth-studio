package com.galgothstudio.backend.aiorchestrator.provider;

import java.util.Arrays;
import java.util.Objects;

/**
 * Genera imágenes de textura por IA -- interfaz definida en el ticket 025
 * (entonces explícitamente sin proveedor real, Fase 3 fuera de alcance
 * del Technical Alpha). Fase 3 ya está en curso: el ticket 051 agrega
 * {@link #generateTextureSheet(TextureGenerationSheetRequest)} de forma
 * ADITIVA (Diseño técnico §11/§12 de
 * `docs/definiciones/galgoth-studio-fase3-textura.md`) y el primer
 * proveedor real, {@link OpenAiImageProvider}. {@link #generateImage(String)}
 * (el método original de 025) no cambia de forma ni de contrato -- sigue
 * sin ningún consumidor real en `ai-orchestrator`/dominio.
 */
public interface ImageGenerationProvider {

	byte[] generateImage(String prompt);

	/**
	 * Genera UNA imagen temporal con el contenido completo de una
	 * {@code TextureGenerationSheet} (Diseño técnico §11 -- una llamada por
	 * bone, nunca por cuboid). {@code referenceImageBytes} nulo/vacío
	 * dispara la primera pasada (sin contenido previo que preservar);
	 * no vacío dispara inpaint/edición sobre esa imagen base. El slicing
	 * (recortar cada {@code CuboidFacePlacement} de la imagen resultante) y
	 * la composición sobre el atlas NO son responsabilidad de esta interfaz
	 * -- eso es {@code TextureSheetSlicer}/{@code TextureCompositorService}
	 * (ticket 053, todavía no existen).
	 *
	 * @param request forma EXACTA fijada por el ticket 051 -- aditiva y
	 *                ajustable por 053 si el pipeline completo necesita
	 *                más campos (nunca de forma que rompa este contrato).
	 */
	byte[] generateTextureSheet(TextureGenerationSheetRequest request);

	/**
	 * {@code style} no tiene equivalente directo en el contrato genérico de
	 * `/v1/images/generations`/`/v1/images/edits` de OpenAI (a diferencia
	 * del parámetro `style` específico de DALL-E-3, "vivid"/"natural", que
	 * no aplica a un snapshot de `gpt-image-*`) -- cada proveedor decide
	 * cómo incorporarlo (ver {@link OpenAiImageProvider}, que lo pliega
	 * dentro del prompt compuesto).
	 */
	record TextureGenerationSheetRequest(String prompt, byte[] referenceImageBytes, int sheetWidth, int sheetHeight, String style) {

		// S6218: un record con un campo array (`referenceImageBytes`) hereda
		// equals/hashCode/toString por identidad de referencia del array, no
		// por contenido -- dos requests con los mismos bytes de imagen (pero
		// arrays distintos, ej. tras un round-trip de deserialización)
		// compararían como distintos. Se sobreescriben los 3 explícitamente
		// con `java.util.Arrays` sobre ese campo.
		@Override
		public boolean equals(Object other) {
			if (this == other) {
				return true;
			}
			if (!(other instanceof TextureGenerationSheetRequest(
					String otherPrompt, byte[] otherBytes, int otherWidth, int otherHeight, String otherStyle))) {
				return false;
			}
			return sheetWidth == otherWidth && sheetHeight == otherHeight && Objects.equals(prompt, otherPrompt)
					&& Objects.equals(style, otherStyle) && Arrays.equals(referenceImageBytes, otherBytes);
		}

		@Override
		public int hashCode() {
			int result = Objects.hash(prompt, sheetWidth, sheetHeight, style);
			return 31 * result + Arrays.hashCode(referenceImageBytes);
		}

		@Override
		public String toString() {
			return "TextureGenerationSheetRequest[prompt=" + prompt + ", referenceImageBytes="
					+ Arrays.toString(referenceImageBytes) + ", sheetWidth=" + sheetWidth + ", sheetHeight=" + sheetHeight
					+ ", style=" + style + "]";
		}
	}

}
