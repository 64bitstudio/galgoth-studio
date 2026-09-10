package com.galgothstudio.backend.aiorchestrator.texture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.galgothstudio.backend.domain.model.FaceName;
import com.galgothstudio.backend.domain.model.Vec3;
import com.galgothstudio.backend.domain.model.Vec4;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/**
 * `TextureCompositorService` -- ticket 053, Diseño técnico §11 punto 6 y
 * §21 (aislamiento espacial): compone EXCLUSIVAMENTE sobre el
 * `atlasUvRect` de destino, sobre una copia en memoria -- ningún píxel
 * de un placement puede modificar otra región del atlas.
 */
class TextureCompositorServiceTest {

	private static final int BACKGROUND = 0xFF808080;
	private static final int RED = 0xFFFF0000;
	private static final int BLUE = 0xFF0000FF;

	private final TextureCompositorService compositor = new TextureCompositorService();

	private static CuboidFacePlacement placementAt(Vec4 atlasUvRect) {
		return new CuboidFacePlacement("cube-a", FaceName.NORTH, new Vec4(0, 0, 8, 8), atlasUvRect, new Vec3(4, 4, 4), "front");
	}

	private static BufferedImage solidImage(int width, int height, int color) {
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		for (int x = 0; x < width; x++) {
			for (int y = 0; y < height; y++) {
				image.setRGB(x, y, color);
			}
		}
		return image;
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

	private static byte[] blankAtlas(int width, int height) {
		return pngOf(solidImage(width, height, BACKGROUND));
	}

	private static BufferedImage decode(byte[] bytes) {
		try {
			return ImageIO.read(new ByteArrayInputStream(bytes));
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	private static void assertRegion(BufferedImage image, int x0, int y0, int x1, int y1, int expectedColor) {
		for (int x = x0; x < x1; x++) {
			for (int y = y0; y < y1; y++) {
				assertThat(image.getRGB(x, y)).as("pixel (%d,%d)", x, y).isEqualTo(expectedColor);
			}
		}
	}

	@Test
	void componeUnSlice_exactamenteSobreSuAtlasUvRect() {
		byte[] atlas = blankAtlas(20, 10);
		TextureSlice slice = new TextureSlice(placementAt(new Vec4(0, 0, 8, 8)), solidImage(8, 8, RED));

		byte[] composed = compositor.compose(atlas, List.of(slice));

		BufferedImage result = decode(composed);
		assertRegion(result, 0, 0, 8, 8, RED);
	}

	@Test
	void componerUnSlice_nuncaModificaLaRegionDeUnPlacementVecino_AC_aislamientoEspacial() {
		byte[] atlas = blankAtlas(20, 10);
		TextureSlice sliceA = new TextureSlice(placementAt(new Vec4(0, 0, 8, 8)), solidImage(8, 8, RED));

		byte[] composed = compositor.compose(atlas, List.of(sliceA));

		BufferedImage result = decode(composed);
		assertRegion(result, 0, 0, 8, 8, RED); // la propia región de A, compuesta
		assertRegion(result, 10, 0, 18, 8, BACKGROUND); // región vecina "B" -- SIN TOCAR
		assertRegion(result, 8, 0, 10, 10, BACKGROUND); // gutter -- SIN TOCAR
		assertRegion(result, 0, 8, 20, 10, BACKGROUND); // fuera de cualquier placement -- SIN TOCAR
	}

	@Test
	void componerDosSlicesAdyacentes_cadaUnoQuedaEnSuPropiaRegion_ningunoInvadeAlOtro() {
		byte[] atlas = blankAtlas(20, 10);
		TextureSlice sliceA = new TextureSlice(placementAt(new Vec4(0, 0, 8, 8)), solidImage(8, 8, RED));
		TextureSlice sliceB = new TextureSlice(
				new CuboidFacePlacement("cube-b", FaceName.NORTH, new Vec4(0, 0, 8, 8), new Vec4(10, 0, 18, 8), new Vec3(4, 4, 4), "front"),
				solidImage(8, 8, BLUE));

		byte[] composed = compositor.compose(atlas, List.of(sliceA, sliceB));

		BufferedImage result = decode(composed);
		assertRegion(result, 0, 0, 8, 8, RED);
		assertRegion(result, 10, 0, 18, 8, BLUE);
		assertRegion(result, 8, 0, 10, 10, BACKGROUND); // gutter entre ambos -- SIN TOCAR
	}

	@Test
	void unSliceQueNoCalzaConSuAtlasUvRect_seEscalaAutomaticamente_sinErrorNiReintento() {
		byte[] atlas = blankAtlas(20, 10);
		// Slice de 4x4 (más chico que su destino de 8x8) -- color sólido, así
		// que el escalado no introduce ambigüedad de píxel esperado.
		TextureSlice slice = new TextureSlice(placementAt(new Vec4(0, 0, 8, 8)), solidImage(4, 4, RED));

		byte[] composed = compositor.compose(atlas, List.of(slice));

		BufferedImage result = decode(composed);
		assertRegion(result, 0, 0, 8, 8, RED);
		assertRegion(result, 8, 0, 20, 10, BACKGROUND);
	}

	@Test
	void nuncaMutaLosBytesDelAtlasOriginal_operaSobreUnaCopiaEnMemoria() {
		byte[] atlas = blankAtlas(20, 10);
		TextureSlice slice = new TextureSlice(placementAt(new Vec4(0, 0, 8, 8)), solidImage(8, 8, RED));

		compositor.compose(atlas, List.of(slice));

		// El array original, re-decodificado, sigue mostrando el fondo -- la
		// composición nunca tocó el bitmap "en vivo", solo devolvió una copia nueva.
		BufferedImage stillOriginal = decode(atlas);
		assertRegion(stillOriginal, 0, 0, 8, 8, BACKGROUND);
	}

	@Test
	void unAtlasConBytesCorruptos_lanzaTextureGenerationFailedException_sinComponerNada() {
		byte[] garbage = {9, 9, 9};
		TextureSlice slice = new TextureSlice(placementAt(new Vec4(0, 0, 8, 8)), solidImage(8, 8, RED));
		List<TextureSlice> slices = List.of(slice);

		assertThatThrownBy(() -> compositor.compose(garbage, slices)).isInstanceOf(TextureGenerationFailedException.class);
	}

}
