package com.galgothstudio.backend.aiorchestrator.texture;

import com.galgothstudio.backend.domain.model.Vec4;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Service;

/**
 * Compone cada {@link TextureSlice} EXCLUSIVAMENTE sobre su
 * {@code atlasUvRect} de destino, sobre una COPIA en memoria del atlas
 * actual -- Diseño técnico §11 punto 6 y §21 (aislamiento espacial) de
 * `docs/definiciones/galgoth-studio-fase3-textura.md`, ticket 053.
 *
 * <p><b>Nunca el bitmap persistido en vivo</b>: recibe y devuelve
 * {@code byte[]} -- decodifica una {@link BufferedImage} NUEVA en cada
 * llamada y la vuelca a un buffer ARGB propio antes de dibujar nada
 * encima; el caller (ticket 054) decide cuándo/si persistir el resultado
 * devuelto. El array {@code currentAtlasBytes} recibido nunca se muta.
 *
 * <p><b>Ajuste automático de dimensiones</b>: si un slice no calza
 * exactamente con su {@code atlasUvRect}, se escala automáticamente al
 * tamaño de destino antes de componerlo (decisión ya vigente del PO) --
 * sin reintento, sin error al usuario. {@code Graphics2D.drawImage(img,
 * x, y, width, height, obs)} ya hace este ajuste tanto si el slice es
 * más chico como más grande que el destino.
 *
 * <p><b>Aislamiento por clip (§21)</b>: antes de dibujar cada slice se
 * fija {@code Graphics2D.setClip(...)} EXACTAMENTE al {@code atlasUvRect}
 * (redondeado) de destino -- ningún píxel de un placement puede
 * modificar otra región del atlas, ni siquiera por un artefacto de
 * redondeo del resampling al escalar.
 *
 * <p>Si la decodificación del atlas actual falla técnicamente, se lanza
 * {@link TextureGenerationFailedException} ANTES de dibujar nada sobre
 * la copia -- nunca se devuelve/aplica un atlas parcialmente compuesto.
 */
@Service
public class TextureCompositorService {

	public byte[] compose(byte[] currentAtlasBytes, List<TextureSlice> slices) {
		BufferedImage atlasCopy = decodeAsCopy(currentAtlasBytes);
		Graphics2D g = atlasCopy.createGraphics();
		try {
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			for (TextureSlice slice : slices) {
				composeOne(g, slice);
			}
		} finally {
			g.dispose();
		}
		return encode(atlasCopy);
	}

	private static void composeOne(Graphics2D g, TextureSlice slice) {
		Vec4 dest = slice.placement().atlasUvRect();
		int x = (int) Math.round(dest.a());
		int y = (int) Math.round(dest.b());
		int width = (int) Math.round(dest.c() - dest.a());
		int height = (int) Math.round(dest.d() - dest.b());

		g.setClip(x, y, width, height);
		g.drawImage(slice.image(), x, y, width, height, null);
		g.setClip(null);
	}

	private static BufferedImage decodeAsCopy(byte[] bytes) {
		BufferedImage decoded;
		try {
			decoded = ImageIO.read(new ByteArrayInputStream(bytes));
		} catch (IOException e) {
			throw new TextureGenerationFailedException("No se pudo decodificar el atlas actual: " + e.getMessage(), e);
		}
		if (decoded == null) {
			throw new TextureGenerationFailedException(
					"No se pudo decodificar el atlas actual -- formato no reconocido o bytes corruptos.");
		}
		BufferedImage copy = new BufferedImage(decoded.getWidth(), decoded.getHeight(), BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = copy.createGraphics();
		try {
			g.drawImage(decoded, 0, 0, null);
		} finally {
			g.dispose();
		}
		return copy;
	}

	private static byte[] encode(BufferedImage image) {
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			ImageIO.write(image, "png", out);
			return out.toByteArray();
		} catch (IOException e) {
			throw new TextureGenerationFailedException("No se pudo codificar el atlas compuesto a PNG: " + e.getMessage(), e);
		}
	}

}
