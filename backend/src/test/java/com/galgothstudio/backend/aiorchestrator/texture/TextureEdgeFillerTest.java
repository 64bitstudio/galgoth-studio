package com.galgothstudio.backend.aiorchestrator.texture;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;

/**
 * `TextureEdgeFiller` -- ticket 114. Lo que más importa acá no es que
 * rellene, sino que NO toque lo que no debe: negro interior legítimo,
 * caras sanas y bandas tan anchas que ya no son una costura.
 */
class TextureEdgeFillerTest {

	private static final int NEGRO = 0xFF000000;
	private static final int VERDE = 0xFF4A5238;
	private static final int VIOLETA = 0xFF8B2EE0;

	private final TextureEdgeFiller filler = new TextureEdgeFiller();

	private static BufferedImage lleno(int w, int h, int argb) {
		BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				img.setRGB(x, y, argb);
			}
		}
		return img;
	}

	private static void columna(BufferedImage img, int x, int argb) {
		for (int y = 0; y < img.getHeight(); y++) {
			img.setRGB(x, y, argb);
		}
	}

	private static void fila(BufferedImage img, int y, int argb) {
		for (int x = 0; x < img.getWidth(); x++) {
			img.setRGB(x, y, argb);
		}
	}

	private static int[] pixeles(BufferedImage img) {
		int[] out = new int[img.getWidth() * img.getHeight()];
		img.getRGB(0, 0, img.getWidth(), img.getHeight(), out, 0, img.getWidth());
		return out;
	}

	@Test
	void unaBandaNegraDeDosPixelesEnElBordeIzquierdoTomaElColorDelPixelValidoContiguo_AC() {
		BufferedImage img = lleno(20, 20, VERDE);
		columna(img, 0, NEGRO);
		columna(img, 1, NEGRO);

		TextureEdgeFiller.Result result = filler.fillBlackEdges(img);

		assertThat(result.edgesFilled()).isEqualTo(1);
		assertThat(img.getRGB(0, 5)).isEqualTo(VERDE);
		assertThat(img.getRGB(1, 5)).isEqualTo(VERDE);
	}

	@Test
	void rellenaLosCuatroBordesYLasEsquinasQuedanCoherentes() {
		BufferedImage img = lleno(20, 20, VERDE);
		columna(img, 0, NEGRO);
		columna(img, 19, NEGRO);
		fila(img, 0, NEGRO);
		fila(img, 19, NEGRO);

		TextureEdgeFiller.Result result = filler.fillBlackEdges(img);

		assertThat(result.edgesFilled()).isEqualTo(4);
		// Las 4 esquinas son el caso delicado: se rellenan en dos pasadas
		// (horizontal y después vertical) para que no queden en negro.
		assertThat(img.getRGB(0, 0)).isEqualTo(VERDE);
		assertThat(img.getRGB(19, 0)).isEqualTo(VERDE);
		assertThat(img.getRGB(0, 19)).isEqualTo(VERDE);
		assertThat(img.getRGB(19, 19)).isEqualTo(VERDE);
	}

	/** AC explícito: una banda ancha no es una costura, es una cara que salió mal -- taparla sería esconder el problema. */
	@Test
	void unaBandaDemasiadoAnchaNoSeRellenaYSeCuentaAparte_AC() {
		BufferedImage img = lleno(20, 20, VERDE);
		for (int x = 0; x < 8; x++) { // 8 de 20 = 40%, por encima del 25%
			columna(img, x, NEGRO);
		}

		TextureEdgeFiller.Result result = filler.fillBlackEdges(img);

		assertThat(result.edgesFilled()).isZero();
		assertThat(result.edgesSkipped()).isEqualTo(1);
		assertThat(img.getRGB(0, 5)).isEqualTo(NEGRO); // intacta
	}

	@Test
	void unaCaraSinBandasQuedaIdenticaPixelAPixel_AC() {
		BufferedImage img = lleno(20, 20, VERDE);
		img.setRGB(9, 9, VIOLETA);
		int[] antes = pixeles(img);

		TextureEdgeFiller.Result result = filler.fillBlackEdges(img);

		assertThat(result.touched()).isFalse();
		assertThat(pixeles(img)).isEqualTo(antes);
	}

	/** AC explícito: el negro que NO arranca en el borde es contenido legítimo (una grieta, una sombra) y no se toca. */
	@Test
	void elNegroDelInteriorSeConserva_AC() {
		BufferedImage img = lleno(20, 20, VERDE);
		for (int y = 5; y < 15; y++) {
			for (int x = 5; x < 15; x++) {
				img.setRGB(x, y, NEGRO);
			}
		}
		int[] antes = pixeles(img);

		filler.fillBlackEdges(img);

		assertThat(pixeles(img)).isEqualTo(antes);
	}

	@Test
	void unaCaraEnteramenteNegraSeDejaComoEsta_noHayPixelValidoDelQueCopiar() {
		BufferedImage img = lleno(20, 20, NEGRO);

		TextureEdgeFiller.Result result = filler.fillBlackEdges(img);

		assertThat(result.edgesFilled()).isZero();
		assertThat(img.getRGB(10, 10)).isEqualTo(NEGRO);
	}

	/**
	 * AC explícito del 114 después de la medición en vivo: el relleno corre
	 * sobre el atlas YA COMPUESTO, a través de una vista {@code getSubimage}
	 * que comparte raster con el padre. Dos cosas tienen que ser ciertas a la
	 * vez: que lo escrito llegue al atlas, y que NO se derrame un solo píxel
	 * sobre la cara de al lado.
	 */
	@Test
	void rellenarUnaCaraDelAtlasEscribeEnElAtlasYNoTocaLaCaraVecina_AC() {
		BufferedImage atlas = lleno(32, 16, VERDE);
		for (int y = 0; y < 16; y++) { // banda negra en el borde izquierdo de la cara IZQUIERDA
			atlas.setRGB(0, y, NEGRO);
			atlas.setRGB(1, y, NEGRO);
		}
		for (int y = 0; y < 16; y++) { // la cara DERECHA (x 16..31) es violeta entera
			for (int x = 16; x < 32; x++) {
				atlas.setRGB(x, y, VIOLETA);
			}
		}

		TextureEdgeFiller.Result result = filler.fillBlackEdges(atlas.getSubimage(0, 0, 16, 16));

		assertThat(result.edgesFilled()).isEqualTo(1);
		assertThat(atlas.getRGB(0, 5)).isEqualTo(VERDE);
		assertThat(atlas.getRGB(1, 5)).isEqualTo(VERDE);
		for (int y = 0; y < 16; y++) {
			for (int x = 16; x < 32; x++) {
				assertThat(atlas.getRGB(x, y)).isEqualTo(VIOLETA);
			}
		}
	}

	/**
	 * La banda negra de una cara NO puede rellenarse copiando el píxel de la
	 * cara contigua: la vista está acotada al rect, así que el único origen
	 * posible es el interior de la propia cara.
	 */
	@Test
	void unaCaraCuyaVecinaEsNegraSeRellenaConSuPropioColor() {
		BufferedImage atlas = lleno(32, 16, VERDE);
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				atlas.setRGB(x, y, NEGRO); // cara izquierda: negra entera
			}
			atlas.setRGB(16, y, NEGRO); // banda negra en el borde izquierdo de la cara DERECHA
		}

		TextureEdgeFiller.Result result = filler.fillBlackEdges(atlas.getSubimage(16, 0, 16, 16));

		assertThat(result.edgesFilled()).isEqualTo(1);
		assertThat(atlas.getRGB(16, 5)).isEqualTo(VERDE);
		assertThat(atlas.getRGB(15, 5)).isEqualTo(NEGRO); // la vecina sigue intacta
	}

	/** La transparencia es un estado legítimo del atlas (cara sin pintar), no una costura que haya que tapar. */
	@Test
	void unaBandaTransparenteNoSeConfundeConNegro() {
		BufferedImage img = lleno(20, 20, VERDE);
		columna(img, 0, 0x00000000);
		int[] antes = pixeles(img);

		TextureEdgeFiller.Result result = filler.fillBlackEdges(img);

		assertThat(result.touched()).isFalse();
		assertThat(pixeles(img)).isEqualTo(antes);
	}

}
