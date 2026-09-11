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
 *
 * <p><b>Hallazgo real (ticket 065, verificación en vivo contra
 * `studio-dev`): el atlas compuesto resultante venía casi 100% negro/
 * transparente</b> -- confirmado decodificando el PNG real devuelto por
 * un job completado. Root cause: {@code sheet.sheetWidth()/sheetHeight()}
 * son las dimensiones ORIGINALES calculadas por {@link ShelfBinPacker}
 * ANTES de que {@code OpenAiImageProvider.sizeParam} (tickets 060/061/063)
 * las infle para cumplir los requisitos reales de la API (múltiplo de 16,
 * aspect ratio, pixel budget de área) -- una inflación que puede ser de
 * 10-25x (ej. un sheet de 64x32 termina pidiéndose como 816x816 para
 * cumplir el pixel budget mínimo). La API genera contenido real
 * proporcional al canvas completo que se le pide, no confinado a una
 * esquina -- así que recortar con las coordenadas ORIGINALES (pequeñas)
 * contra la imagen REAL (mucho más grande) extraía una esquina
 * mayormente vacía/negra, no el contenido generado real. Corregido:
 * cada {@code sheetRect} se ESCALA proporcionalmente
 * ({@code imagenReal.ancho/alto} ÷ {@code sheet.sheetWidth/Height}) antes
 * de recortar -- el slice resultante queda más grande que el
 * {@code atlasUvRect} de destino, pero eso es SEGURO por diseño:
 * {@link TextureCompositorService} YA soporta ese mismatch (lo
 * reescala automáticamente al tamaño de destino, "decisión ya vigente
 * del PO", ver su Javadoc) -- este fix no le cambia nada a esa clase.
 */
@Component
public class TextureSheetSlicer {

	public List<TextureSlice> slice(byte[] sheetImageBytes, TextureGenerationSheet sheet) {
		BufferedImage source = decode(sheetImageBytes);
		double scaleX = source.getWidth() / (double) sheet.sheetWidth();
		double scaleY = source.getHeight() / (double) sheet.sheetHeight();
		List<TextureSlice> slices = new ArrayList<>(sheet.placements().size());
		for (CuboidFacePlacement placement : sheet.placements()) {
			slices.add(new TextureSlice(placement, cropClamped(source, placement, scaleX, scaleY)));
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

	private static BufferedImage cropClamped(BufferedImage source, CuboidFacePlacement placement, double scaleX, double scaleY) {
		Vec4 rect = placement.sheetRect();
		int x = (int) Math.round(rect.a() * scaleX);
		int y = (int) Math.round(rect.b() * scaleY);
		int width = (int) Math.round((rect.c() - rect.a()) * scaleX);
		int height = (int) Math.round((rect.d() - rect.b()) * scaleY);
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
