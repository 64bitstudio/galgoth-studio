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
	 * Nombre del proveedor activo (`"openai"`/`"mock"`) -- mismo valor que
	 * persiste en `ai_jobs.provider`. Extensión aditiva del ticket 054: a
	 * diferencia de {@link VisionModelProvider}/{@link StructuredReasoningProvider},
	 * cuyas respuestas ya viajan empaquetadas en un {@link AiProviderResponse}
	 * (con `provider`/`model` incluidos), {@link #generateTextureSheet} solo
	 * devuelve {@code byte[]} -- sin esto, `TextureGenerationService` (054)
	 * no tendría forma de saber qué proveedor/modelo REAL persistir en
	 * `ai_jobs` (HU-39) sin un `instanceof` frágil contra la implementación
	 * concreta activa. Cambio interno (ningún contrato HTTP/esquema de
	 * datos se ve afectado), implementado en el mismo PR por AMBOS
	 * implementadores existentes -- reportado explícitamente por
	 * transparencia.
	 */
	String provider();

	/** Modelo REAL configurado en este proveedor (nunca un literal fijo) -- mismo valor que persiste en `ai_jobs.model`. Ver Javadoc de {@link #provider()}. */
	String model();

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
	 * Tamaño real {@code (width, height)} en píxeles que este proveedor va
	 * a solicitarle de verdad a su API subyacente para un sheet
	 * {@code width x height} -- ticket 101 (Diseño técnico, causa raíz de
	 * "colores en regiones incorrectas"/"zonas en blanco" de Texture V2).
	 * Antes de este método, el único lugar del código que conocía la
	 * inflación real de tamaño era el ajuste interno de
	 * {@link OpenAiImageProvider#generateTextureSheet}: ni
	 * {@code TextureSheetPromptComposer} (que describe el grid al modelo)
	 * ni {@code TextureSheetSlicer} (que recorta la respuesta) tenían forma
	 * de saber que el canvas real iba a ser hasta 25x más grande que
	 * {@code TextureGenerationSheet.sheetWidth()/sheetHeight()} -- de ahí
	 * el desalineamiento de coordenadas prompt↔API.
	 *
	 * <p>Default identidad ({@code {width, height}} tal cual) -- correcto
	 * para {@link MockImageProvider}, que siempre genera exactamente el
	 * tamaño pedido (nunca infla). Un proveedor que SÍ infla el tamaño
	 * real solicitado (ver {@link OpenAiImageProvider#inflatedSheetSize})
	 * debe sobreescribirlo -- nunca puede devolver algo MENOR al tamaño
	 * pedido (mismo invariante de "nunca encoge" que ya documenta
	 * {@code OpenAiImageProvider#sizeParam}).
	 */
	default int[] inflatedSheetSize(int width, int height) {
		return new int[] {width, height};
	}

	/**
	 * <b>{@code partialAtlasBytes} (ticket 102, HU-8)</b>: atlas ya
	 * compuesto hasta ese momento DENTRO del mismo job -- contexto de
	 * continuidad visual entre bones (que el brazo empalme con el torso,
	 * etc.). {@code null}/vacío en la primera llamada de un job (todavía no
	 * hay nada compuesto) y en cualquier flujo que no lo provea. Cada
	 * proveedor decide qué hacer con él: {@link OpenAiImageProvider} lo
	 * manda JUNTO a la referencia original (la API real acepta varias
	 * imágenes por llamada, ver su Javadoc); un proveedor que solo admita
	 * UNA imagen debe quedarse con la referencia original y documentar por
	 * qué -- nunca descartar la referencia artística en silencio.
	 *
	 * <p>{@code style} no tiene equivalente directo en el contrato genérico de
	 * `/v1/images/generations`/`/v1/images/edits` de OpenAI (a diferencia
	 * del parámetro `style` específico de DALL-E-3, "vivid"/"natural", que
	 * no aplica a un snapshot de `gpt-image-*`) -- cada proveedor decide
	 * cómo incorporarlo (ver {@link OpenAiImageProvider}, que lo pliega
	 * dentro del prompt compuesto).
	 */
	record TextureGenerationSheetRequest(
			String prompt, byte[] referenceImageBytes, byte[] partialAtlasBytes, int sheetWidth, int sheetHeight, String style) {

		/**
		 * Constructor de compatibilidad para los call sites anteriores al
		 * ticket 102 ({@code partialAtlasBytes} = {@code null}: primera
		 * llamada de un job, o proveedor/flujo sin contexto de continuidad)
		 * -- mismo patrón aditivo ya usado en {@code Cuboid}/{@code CreateCuboid}
		 * (099), cero call sites existentes rotos.
		 */
		public TextureGenerationSheetRequest(String prompt, byte[] referenceImageBytes, int sheetWidth, int sheetHeight, String style) {
			this(prompt, referenceImageBytes, null, sheetWidth, sheetHeight, style);
		}

		// S6218: un record con campos array (`referenceImageBytes`,
		// `partialAtlasBytes`) hereda equals/hashCode/toString por identidad de
		// referencia del array, no por contenido -- dos requests con los mismos
		// bytes de imagen (pero arrays distintos, ej. tras un round-trip de
		// deserialización) compararían como distintos. Se sobreescriben los 3
		// explícitamente con `java.util.Arrays` sobre esos campos.
		@Override
		public boolean equals(Object other) {
			if (this == other) {
				return true;
			}
			if (!(other instanceof TextureGenerationSheetRequest(
					String otherPrompt, byte[] otherBytes, byte[] otherAtlas, int otherWidth, int otherHeight, String otherStyle))) {
				return false;
			}
			return sheetWidth == otherWidth && sheetHeight == otherHeight && Objects.equals(prompt, otherPrompt)
					&& Objects.equals(style, otherStyle) && Arrays.equals(referenceImageBytes, otherBytes)
					&& Arrays.equals(partialAtlasBytes, otherAtlas);
		}

		@Override
		public int hashCode() {
			int result = Objects.hash(prompt, sheetWidth, sheetHeight, style);
			result = 31 * result + Arrays.hashCode(referenceImageBytes);
			return 31 * result + Arrays.hashCode(partialAtlasBytes);
		}

		@Override
		public String toString() {
			return "TextureGenerationSheetRequest[prompt=" + prompt + ", referenceImageBytes="
					+ Arrays.toString(referenceImageBytes) + ", partialAtlasBytes=" + Arrays.toString(partialAtlasBytes)
					+ ", sheetWidth=" + sheetWidth + ", sheetHeight=" + sheetHeight + ", style=" + style + "]";
		}
	}

}
