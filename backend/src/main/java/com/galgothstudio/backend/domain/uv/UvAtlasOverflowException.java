package com.galgothstudio.backend.domain.uv;

/**
 * Error de dominio {@code UV_ATLAS_OVERFLOW}: el conjunto de cuboids del
 * mob no cabe en el atlas configurado (`MobProjectModel.uv`). El atlas
 * NUNCA crece automáticamente -- ver ticket 006.
 */
public final class UvAtlasOverflowException extends RuntimeException {

	private final int currentWidth;
	private final int currentHeight;
	private final int requiredWidth;
	private final int requiredHeight;

	public UvAtlasOverflowException(int currentWidth, int currentHeight, int requiredWidth, int requiredHeight) {
		super(
				"UV_ATLAS_OVERFLOW: el atlas actual (" + currentWidth + "x" + currentHeight
						+ ") no alcanza para el modelo -- se requieren al menos " + requiredWidth + "x" + requiredHeight);
		this.currentWidth = currentWidth;
		this.currentHeight = currentHeight;
		this.requiredWidth = requiredWidth;
		this.requiredHeight = requiredHeight;
	}

	public int currentWidth() {
		return currentWidth;
	}

	public int currentHeight() {
		return currentHeight;
	}

	public int requiredWidth() {
		return requiredWidth;
	}

	public int requiredHeight() {
		return requiredHeight;
	}

}
