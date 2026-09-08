package com.galgothstudio.backend.domain.export;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import javax.imageio.ImageIO;

/**
 * Textura placeholder auto-generada (checkerboard) -- ticket 011. Sin
 * dependencia de un display real: {@link BufferedImage} + {@link ImageIO}
 * dibujan/codifican en memoria, seguro en un backend headless (Jenkins,
 * contenedor sin X server).
 */
final class PlaceholderTexture {

	private static final int CHECKER_CELL_SIZE = 8;
	private static final int LIGHT_RGB = 0xE0E0E0;
	private static final int DARK_RGB = 0xC0C0C0;

	private PlaceholderTexture() {
	}

	/**
	 * @param width  debe coincidir EXACTAMENTE con {@code MobProjectModel.texture().width()} (AC #1).
	 * @param height debe coincidir EXACTAMENTE con {@code MobProjectModel.texture().height()} (AC #1).
	 * @return bytes PNG del checkerboard generado.
	 */
	static byte[] generatePng(int width, int height) {
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				boolean isLightCell = (x / CHECKER_CELL_SIZE + y / CHECKER_CELL_SIZE) % 2 == 0;
				image.setRGB(x, y, isLightCell ? LIGHT_RGB : DARK_RGB);
			}
		}
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			ImageIO.write(image, "png", out);
			return out.toByteArray();
		} catch (IOException e) {
			throw new UncheckedIOException("No se pudo generar la textura placeholder", e);
		}
	}

}
