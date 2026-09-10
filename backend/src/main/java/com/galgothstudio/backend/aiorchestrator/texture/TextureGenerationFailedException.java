package com.galgothstudio.backend.aiorchestrator.texture;

/**
 * Fallo TÉCNICO (no de contenido/validación de negocio) al decodificar,
 * recortar o componer una {@link TextureGenerationSheet} generada por
 * IA -- {@link TextureSheetSlicer}/{@link TextureCompositorService},
 * ticket 053, Diseño técnico §21 (aislamiento espacial). Mismo estilo
 * simple que {@code com.galgothstudio.backend.domain.uv.UvAtlasOverflowException}
 * (RuntimeException de dominio con mensaje/causa, sin depender de
 * {@code AiProviderResponse} -- a diferencia de
 * {@code InvalidGeometryProposalException}, esto NO es un rechazo de
 * contenido propuesto por un proveedor de IA, es un fallo técnico de
 * imaging/IO).
 *
 * <p>Se lanza SIEMPRE antes de aplicar cualquier cambio visible: ni el
 * slicer devuelve un slice parcial, ni el compositor devuelve un atlas
 * parcialmente compuesto -- el caller nunca ve un resultado a medias,
 * solo esta excepción.
 */
public class TextureGenerationFailedException extends RuntimeException {

	public TextureGenerationFailedException(String message) {
		super(message);
	}

	public TextureGenerationFailedException(String message, Throwable cause) {
		super(message, cause);
	}

}
