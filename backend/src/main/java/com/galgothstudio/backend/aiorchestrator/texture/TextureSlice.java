package com.galgothstudio.backend.aiorchestrator.texture;

import java.awt.image.BufferedImage;

/**
 * Resultado de recortar UN {@link CuboidFacePlacement} de la imagen
 * generada por {@link TextureSheetSlicer} -- {@code image} es una copia
 * independiente (no comparte raster con la imagen fuente), lista para
 * que {@link TextureCompositorService} la componga sobre el atlas.
 */
public record TextureSlice(CuboidFacePlacement placement, BufferedImage image) {
}
