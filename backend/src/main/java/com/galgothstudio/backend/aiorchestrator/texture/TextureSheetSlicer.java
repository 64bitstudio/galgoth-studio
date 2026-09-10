package com.galgothstudio.backend.aiorchestrator.texture;

import com.galgothstudio.backend.domain.model.Vec4;
import java.awt.image.BufferedImage;
import java.awt.image.RasterFormatException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Component;

/**
 * Recorta, de la imagen generada para una {@link TextureGenerationSheet},
 * el slice independiente de cada {@link CuboidFacePlacement} -- Diseño
 * técnico §11 punto 5 y §21 (aislamiento espacial) de
 * `docs/definiciones/galgoth-studio-fase3-textura.md`, ticket 053.
 *
 * <p><b>Clipping obligatorio</b>: usa {@link BufferedImage#getSubimage}
 * acotado EXACTAMENTE al {@code sheetRect} de cada placement -- por
 * construcción, nunca puede leer un píxel fuera de ese rect. Cualquier
 * "bleed" (contenido que la IA generó fuera de los límites esperados de
 * un {@code sheetRect}) queda simplemente FUERA de la subimagen pedida
 * -- se descarta sin ningún branch/caso especial, nunca se compone en el
 * atlas, nunca dispara un error visible al usuario (es el comportamiento
 * normal del recorte, no una excepción). Si el propio {@code sheetRect}
 * no calza dentro de los límites REALES de la imagen decodificada (la
 * API de imagen devolvió algo más chico de lo pedido, por ejemplo),
 * {@link RasterFormatException} se traduce a
 * {@link TextureGenerationFailedException} -- un fallo técnico real, no
 * un caso de bleed.
 *
 * <p>El resultado de {@code getSubimage} comparte el raster de origen --
 * se copia a un {@link BufferedImage} nuevo e independiente
 * ({@link TextureSlice#image()}) antes de devolverlo, para que un caller
 * no pueda mutar por accidente la imagen fuente a través del slice.
 */
@Component
public class TextureSheetSlicer {

	public List<TextureSlice> slice(byte[] sheetImageBytes, TextureGenerationSheet sheet) {
		BufferedImage source = decode(sheetImageBytes);
		List<TextureSlice> slices = new ArrayList<>(sheet.placements().size());
		for (CuboidFacePlacement placement : sheet.placements()) {
			slices.add(new TextureSlice(placement, cropClamped(source, placement)));
		}
		return slices;
	}

	private static BufferedImage decode(byte[] bytes) {
		BufferedImage image;
		try {
			image = ImageIO.read(new ByteArrayInputStream(bytes));
		} catch (IOException e) {
			throw new TextureGenerationFailedException("No se pudo decodificar la imagen generada: " + e.getMessage(), e);
		}
		if (image == null) {
			throw new TextureGenerationFailedException(
					"No se pudo decodificar la imagen generada -- formato no reconocido o bytes corruptos.");
		}
		return image;
	}

	private static BufferedImage cropClamped(BufferedImage source, CuboidFacePlacement placement) {
		Vec4 rect = placement.sheetRect();
		int x = (int) Math.round(rect.a());
		int y = (int) Math.round(rect.b());
		int width = (int) Math.round(rect.c() - rect.a());
		int height = (int) Math.round(rect.d() - rect.b());
		BufferedImage sub;
		try {
			sub = source.getSubimage(x, y, width, height);
		} catch (RasterFormatException e) {
			throw new TextureGenerationFailedException(
					"No se pudo recortar el sheetRect [" + x + "," + y + "," + width + "," + height + "] de cuboid '"
							+ placement.cuboidId() + "' cara " + placement.face() + " -- fuera de los límites de la "
							+ "imagen generada (" + source.getWidth() + "x" + source.getHeight() + ").",
					e);
		}
		BufferedImage copy = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		copy.getGraphics().drawImage(sub, 0, 0, null);
		return copy;
	}

}
