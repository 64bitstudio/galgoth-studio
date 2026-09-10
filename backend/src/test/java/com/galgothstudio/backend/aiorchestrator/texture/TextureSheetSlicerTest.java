package com.galgothstudio.backend.aiorchestrator.texture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.galgothstudio.backend.domain.model.FaceName;
import com.galgothstudio.backend.domain.model.Vec3;
import com.galgothstudio.backend.domain.model.Vec4;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/**
 * `TextureSheetSlicer` -- ticket 053, Diseño técnico §21 (aislamiento
 * espacial): clipping obligatorio por `sheetRect`, "bleed" descartado
 * silenciosamente, fallo técnico explícito si el rect no calza en la
 * imagen real.
 */
class TextureSheetSlicerTest {

	private static final int RED = 0xFFFF0000;
	private static final int BLUE = 0xFF0000FF;
	private static final int BLEED_GREEN = 0xFF00FF00;

	private final TextureSheetSlicer slicer = new TextureSheetSlicer();

	private static CuboidFacePlacement placement(String cuboidId, FaceName face, Vec4 sheetRect) {
		return new CuboidFacePlacement(cuboidId, face, sheetRect, new Vec4(0, 0, 8, 8), new Vec3(4, 4, 4), "front");
	}

	private static byte[] pngOf(BufferedImage image) {
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			ImageIO.write(image, "png", out);
			return out.toByteArray();
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	/**
	 * Imagen sintética 20x8: placement "a" en [0,0]-[8,8] relleno de ROJO;
	 * el "gutter" [8,10)x[0,8) relleno de VERDE (contenido "sangrado"
	 * deliberado, nunca debería aparecer en ningún slice); placement "b" en
	 * [10,0]-[18,8] relleno de AZUL.
	 */
	private static BufferedImage syntheticSheetWithBleed() {
		BufferedImage image = new BufferedImage(20, 8, BufferedImage.TYPE_INT_ARGB);
		for (int x = 0; x < image.getWidth(); x++) {
			for (int y = 0; y < image.getHeight(); y++) {
				int color = x < 8 ? RED : (x < 10 ? BLEED_GREEN : BLUE);
				image.setRGB(x, y, color);
			}
		}
		return image;
	}

	@Test
	void recortaCadaPlacement_conLasDimensionesExactasDeSuSheetRect() {
		byte[] imageBytes = pngOf(syntheticSheetWithBleed());
		TextureGenerationSheet sheet = new TextureGenerationSheet(
				"head-1", "head", List.of(placement("cube-a", FaceName.NORTH, new Vec4(0, 0, 8, 8))), "cabeza", "paleta",
				"", "ref-1", 20, 8);

		List<TextureSlice> slices = slicer.slice(imageBytes, sheet);

		assertThat(slices).hasSize(1);
		BufferedImage slice = slices.getFirst().image();
		assertThat(slice.getWidth()).isEqualTo(8);
		assertThat(slice.getHeight()).isEqualTo(8);
	}

	@Test
	void elContenidoSangradoFueraDelSheetRect_seDescartaSilenciosamente_nuncaApareceEnElSlice_AC() {
		byte[] imageBytes = pngOf(syntheticSheetWithBleed());
		TextureGenerationSheet sheet = new TextureGenerationSheet(
				"head-1", "head", List.of(placement("cube-a", FaceName.NORTH, new Vec4(0, 0, 8, 8))), "cabeza", "paleta",
				"", "ref-1", 20, 8);

		BufferedImage slice = slicer.slice(imageBytes, sheet).getFirst().image();

		for (int x = 0; x < slice.getWidth(); x++) {
			for (int y = 0; y < slice.getHeight(); y++) {
				assertThat(slice.getRGB(x, y)).as("pixel (%d,%d) del slice de 'a'", x, y).isEqualTo(RED);
			}
		}
	}

	@Test
	void dosPlacementsVecinos_cadaUnoSoloContieneSuPropioColor_sinPixelesDelVecinoNiDelGutter() {
		byte[] imageBytes = pngOf(syntheticSheetWithBleed());
		TextureGenerationSheet sheet = new TextureGenerationSheet(
				"head-1", "head",
				List.of(
						placement("cube-a", FaceName.NORTH, new Vec4(0, 0, 8, 8)),
						placement("cube-a", FaceName.UP, new Vec4(10, 0, 18, 8))),
				"cabeza", "paleta", "", "ref-1", 20, 8);

		List<TextureSlice> slices = slicer.slice(imageBytes, sheet);

		BufferedImage sliceA = slices.get(0).image();
		BufferedImage sliceB = slices.get(1).image();
		assertOnlyColor(sliceA, RED);
		assertOnlyColor(sliceB, BLUE);
	}

	@Test
	void bytesCorruptos_lanzaTextureGenerationFailedException() {
		byte[] garbage = {1, 2, 3, 4, 5};
		TextureGenerationSheet sheet = new TextureGenerationSheet(
				"head-1", "head", List.of(placement("cube-a", FaceName.NORTH, new Vec4(0, 0, 8, 8))), "cabeza", "paleta",
				"", "ref-1", 20, 8);

		assertThatThrownBy(() -> slicer.slice(garbage, sheet)).isInstanceOf(TextureGenerationFailedException.class);
	}

	@Test
	void unSheetRectQueExcedeLosLimitesRealesDeLaImagenGenerada_lanzaTextureGenerationFailedException() {
		byte[] imageBytes = pngOf(syntheticSheetWithBleed()); // imagen real de 20x8
		TextureGenerationSheet sheet = new TextureGenerationSheet(
				"head-1", "head", List.of(placement("cube-a", FaceName.NORTH, new Vec4(0, 0, 100, 100))), "cabeza",
				"paleta", "", "ref-1", 100, 100);

		assertThatThrownBy(() -> slicer.slice(imageBytes, sheet)).isInstanceOf(TextureGenerationFailedException.class);
	}

	private static void assertOnlyColor(BufferedImage image, int expectedColor) {
		for (int x = 0; x < image.getWidth(); x++) {
			for (int y = 0; y < image.getHeight(); y++) {
				assertThat(image.getRGB(x, y)).isEqualTo(expectedColor);
			}
		}
	}

}
