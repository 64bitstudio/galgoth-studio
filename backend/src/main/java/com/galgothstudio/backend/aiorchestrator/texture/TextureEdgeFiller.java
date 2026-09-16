package com.galgothstudio.backend.aiorchestrator.texture;

import java.awt.image.BufferedImage;
import org.springframework.stereotype.Component;

/**
 * Rellena las bandas NEGRAS pegadas al borde de una cara recortada, con
 * el píxel válido contiguo hacia adentro -- ticket 114
 * (`done/113-lineas-negras-en-los-bordes-de-cada-cara.md`).
 *
 * <p><b>Por qué existe</b>: el generador de imagen deja franjas oscuras
 * en el borde de una parte de las caras. El ticket 113 atacó eso por el
 * lado del prompt (pedirle que llene cada rectángulo de borde a borde) y
 * lo midió contra un atlas real: las bandas bajaron ~45%, pero
 * <b>75 de 202 caras (37%) seguían teniendo banda</b>. Pedírselo al
 * modelo ayuda y no alcanza, porque depende de que obedezca. Esto no
 * depende: es determinista y corre siempre.
 *
 * <p><b>Dónde corre</b>: sobre el atlas YA COMPUESTO, una vista por cara
 * acotada a su {@code atlasUvRect} -- ver el Javadoc de
 * {@code TextureGenerationService.fillEdgesAndLog}, que documenta la
 * medición en vivo por la que se movió hasta acá. En consecuencia, los
 * umbrales de abajo están en <b>píxeles de atlas</b>: en una cara de
 * 16x16, {@link #MAX_BAND_RATIO} son 4 px. La imagen que recibe puede
 * compartir raster con el atlas padre: escribe EN SITIO y nunca fuera de
 * los límites de la imagen recibida, que es lo que garantiza que no
 * pueda pisar la cara vecina.
 *
 * <p><b>Qué NO hace, a propósito</b>:
 * <ul>
 *   <li>No toca el negro del INTERIOR de la cara -- solo bandas que
 *       arrancan en el borde. Un mob puede tener negro legítimo.</li>
 *   <li>No rellena una banda ANCHA (más de {@link #MAX_BAND_RATIO} del
 *       lado): eso ya no es una costura, es una cara que salió mal, y
 *       taparla sería esconder el problema en vez de mostrarlo. Esas se
 *       cuentan aparte y se loguean.</li>
 *   <li>No toca una cara sin bandas -- queda idéntica, sin una sola
 *       escritura.</li>
 * </ul>
 */
@Component
public class TextureEdgeFiller {

	/** Luminancia (Rec. 601) por debajo o igual de la cual un píxel cuenta como negro. Mismo umbral con el que se midió el problema en el atlas real del ticket 113. */
	static final int BLACK_LUMINANCE_THRESHOLD = 8;

	/** Proporción máxima del lado que puede ocupar una banda para considerarse una costura rellenable. Por encima de esto la cara salió mal de verdad. */
	static final double MAX_BAND_RATIO = 0.25;

	/**
	 * @param edgesFilled  bordes (de los 4) que se rellenaron.
	 * @param edgesSkipped bordes cuya banda era demasiado ancha para tratarla como costura -- se dejaron intactos.
	 */
	public record Result(int edgesFilled, int edgesSkipped) {

		public boolean touched() {
			return edgesFilled > 0;
		}
	}

	/** Rellena EN SITIO las bandas negras del borde de {@code image}. */
	public Result fillBlackEdges(BufferedImage image) {
		int w = image.getWidth();
		int h = image.getHeight();
		if (w == 0 || h == 0) {
			return new Result(0, 0);
		}

		int left = blackColumnsFrom(image, 0, 1);
		int right = blackColumnsFrom(image, w - 1, -1);
		int top = blackRowsFrom(image, 0, 1);
		int bottom = blackRowsFrom(image, h - 1, -1);

		// Una cara ENTERAMENTE negra no tiene ningún píxel válido del que
		// copiar: no es una costura, es una cara vacía. Se deja como está.
		if (left >= w || top >= h) {
			return new Result(0, countWide(left, w) + countWide(right, w) + countWide(top, h) + countWide(bottom, h));
		}

		int maxHorizontal = (int) Math.floor(w * MAX_BAND_RATIO);
		int maxVertical = (int) Math.floor(h * MAX_BAND_RATIO);

		// Horizontal primero y vertical después, en este orden explícito: así
		// las esquinas toman el valor ya corregido de su columna en vez del
		// negro original.
		Tally tally = Tally.EMPTY;
		tally = tally.plus(applyEdge(left, maxHorizontal, () -> copyColumnInto(image, left, 0, left)));
		tally = tally.plus(applyEdge(right, maxHorizontal, () -> copyColumnInto(image, w - 1 - right, w - right, w)));
		tally = tally.plus(applyEdge(top, maxVertical, () -> copyRowInto(image, top, 0, top)));
		tally = tally.plus(applyEdge(bottom, maxVertical, () -> copyRowInto(image, h - 1 - bottom, h - bottom, h)));
		return new Result(tally.filled(), tally.skipped());
	}

	/** Sin banda no hay nada que hacer; con banda ancha se cuenta y no se toca; si no, se rellena. */
	private static Tally applyEdge(int band, int maxBand, Runnable fill) {
		if (band <= 0) {
			return Tally.EMPTY;
		}
		if (band > maxBand) {
			return new Tally(0, 1);
		}
		fill.run();
		return new Tally(1, 0);
	}

	private record Tally(int filled, int skipped) {

		static final Tally EMPTY = new Tally(0, 0);

		Tally plus(Tally other) {
			return new Tally(filled + other.filled(), skipped + other.skipped());
		}
	}

	private static int countWide(int band, int size) {
		return band > 0 && band > Math.floor(size * MAX_BAND_RATIO) ? 1 : 0;
	}

	/** Columnas completamente negras contadas desde {@code startX} avanzando en {@code step}. */
	private static int blackColumnsFrom(BufferedImage image, int startX, int step) {
		int count = 0;
		for (int x = startX; x >= 0 && x < image.getWidth(); x += step) {
			if (!isColumnBlack(image, x)) {
				return count;
			}
			count++;
		}
		return count;
	}

	private static int blackRowsFrom(BufferedImage image, int startY, int step) {
		int count = 0;
		for (int y = startY; y >= 0 && y < image.getHeight(); y += step) {
			if (!isRowBlack(image, y)) {
				return count;
			}
			count++;
		}
		return count;
	}

	private static boolean isColumnBlack(BufferedImage image, int x) {
		for (int y = 0; y < image.getHeight(); y++) {
			if (!isBlack(image.getRGB(x, y))) {
				return false;
			}
		}
		return true;
	}

	private static boolean isRowBlack(BufferedImage image, int y) {
		for (int x = 0; x < image.getWidth(); x++) {
			if (!isBlack(image.getRGB(x, y))) {
				return false;
			}
		}
		return true;
	}

	/** Un píxel transparente NO cuenta como negro: la transparencia es un estado legítimo del atlas (caras sin pintar), no una costura. */
	private static boolean isBlack(int argb) {
		if (((argb >>> 24) & 0xFF) < 8) {
			return false;
		}
		int r = (argb >> 16) & 0xFF;
		int g = (argb >> 8) & 0xFF;
		int b = argb & 0xFF;
		return 0.299 * r + 0.587 * g + 0.114 * b <= BLACK_LUMINANCE_THRESHOLD;
	}

	private static void copyColumnInto(BufferedImage image, int sourceX, int fromX, int toX) {
		for (int y = 0; y < image.getHeight(); y++) {
			int argb = image.getRGB(sourceX, y);
			for (int x = fromX; x < toX; x++) {
				image.setRGB(x, y, argb);
			}
		}
	}

	private static void copyRowInto(BufferedImage image, int sourceY, int fromY, int toY) {
		for (int x = 0; x < image.getWidth(); x++) {
			int argb = image.getRGB(x, sourceY);
			for (int y = fromY; y < toY; y++) {
				image.setRGB(x, y, argb);
			}
		}
	}

}
