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
