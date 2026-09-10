package com.galgothstudio.backend.aiorchestrator.provider;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import javax.imageio.ImageIO;

/**
 * Doble determinista de {@link ImageGenerationProvider} -- uso EXCLUSIVO
 * en tests/desarrollo (`AI_IMAGE_PROVIDER=mock`, ver {@link AiProviderConfig}).
 *
 * <p>{@link #generateImage(String)} conserva EXACTAMENTE su comportamiento
 * de 025 (bytes vacíos por defecto, configurable vía {@link #setNextImage})
 * -- AC del ticket 051, {@code MockImageProviderTest} sigue en verde sin
 * modificarse. {@link #generateTextureSheet(TextureGenerationSheetRequest)}
 * es la parte nueva/real de 051: sin configurar explícitamente, genera un
 * PNG sintético determinista con las dimensiones EXACTAS pedidas
 * (`sheetWidth`x`sheetHeight`) -- un patrón de tablero de ajedrez cuyo
 * color depende solo de `prompt`/`style` (mismo request -&gt; mismos bytes,
 * distinto request -&gt; bytes distintos), decodificable/verificable con
 * `ImageIO.read` en cualquier test, sin gastar ninguna llamada real.
 */
public class MockImageProvider implements ImageGenerationProvider {

	/** Tamaño de celda del tablero de ajedrez sintético -- valor de diseño arbitrario, solo necesita ser &gt;0 y menor que las dimensiones típicas de un sheet de test. */
	private static final int CHECKER_CELL_SIZE = 8;

	private byte[] nextImage = new byte[0];
	private byte[] nextTextureSheet;

	public void setNextImage(byte[] imageBytes) {
		this.nextImage = imageBytes;
	}

	/** Ver el AC de "doble determinista real" del ticket 051 -- mismo criterio que {@code MockReasoningProvider#setNextResponse}: sin llamar esto, el default sintético ya es determinista y verificable, no hace falta configurarlo para que el mock sea útil. */
	public void setNextTextureSheet(byte[] imageBytes) {
		this.nextTextureSheet = imageBytes;
	}

	@Override
	public byte[] generateImage(String prompt) {
		return nextImage;
	}

	@Override
	public byte[] generateTextureSheet(TextureGenerationSheetRequest request) {
		if (nextTextureSheet != null) {
			return nextTextureSheet;
		}
		return syntheticChecker(request);
	}

	/**
	 * Dos colores derivados de un hash estable de `prompt`+`style` (nunca
	 * `Object.hashCode()` de un String, que SÍ es estable dentro de una
	 * misma JVM/versión pero no está garantizado por contrato entre
	 * versiones -- se usa `String.chars()` sumado a mano para que el
	 * resultado sea reproducible siempre, no solo "hoy").
	 */
	private byte[] syntheticChecker(TextureGenerationSheetRequest request) {
		int seed = stableSeed(request.prompt() + "|" + request.style());
		int colorA = 0xFF000000 | (seed & 0x00FFFFFF);
		int colorB = 0xFF000000 | (~seed & 0x00FFFFFF);

		BufferedImage image = new BufferedImage(request.sheetWidth(), request.sheetHeight(), BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < request.sheetHeight(); y++) {
			for (int x = 0; x < request.sheetWidth(); x++) {
				boolean evenCell = ((x / CHECKER_CELL_SIZE) + (y / CHECKER_CELL_SIZE)) % 2 == 0;
				image.setRGB(x, y, evenCell ? colorA : colorB);
			}
		}
		return encodePng(image);
	}

	private int stableSeed(String value) {
		int seed = 17;
		for (int i = 0; i < value.length(); i++) {
			seed = 31 * seed + value.charAt(i);
		}
		return seed;
	}

	private byte[] encodePng(BufferedImage image) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try {
			ImageIO.write(image, "png", out);
		} catch (IOException e) {
			// No debería ocurrir nunca escribiendo a un ByteArrayOutputStream en memoria
			// (sin I/O real de por medio) -- si pasa, es un bug real del mock, no un caso
			// esperable a tragar en silencio.
			throw new UncheckedIOException("MockImageProvider no pudo codificar el PNG sintético", e);
		}
		return out.toByteArray();
	}

}
