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

	/**
	 * Ticket 065 (hallazgo real, verificación en vivo contra `studio-dev`):
	 * el atlas compuesto resultante venía casi 100% negro/transparente --
	 * root cause: `OpenAiImageProvider.sizeParam` (tickets 060/061/063)
	 * infla el tamaño REALMENTE pedido a la API (múltiplo de 16, aspect
	 * ratio, pixel budget de área) muy por encima de
	 * `sheet.sheetWidth()/sheetHeight()` (a veces 10-25x) -- la imagen real
	 * que vuelve es proporcionalmente más grande, con contenido real
	 * distribuido en ese canvas completo, no confinado a una esquina. Recortar
	 * con las coordenadas ORIGINALES (pequeñas) contra la imagen REAL (más
	 * grande) sin escalar extraía la región equivocada.
	 */
	@Test
	void cuandoLaImagenRealEsMasGrandeQueElSheetPlaneado_escalaElSheetRectProporcionalmente_hallazgoRealTicket065() {
		// Imagen real 8x8: mitad izquierda (x<4) ROJO, mitad derecha (x>=4)
		// VERDE -- simula lo que la API real devuelve cuando el sheet se
		// PLANEÓ en 4x4 pero el tamaño realmente pedido (tras sizeParam) fue
		// 8x8 (2x más grande en cada eje).
		BufferedImage real = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
		for (int x = 0; x < 8; x++) {
			for (int y = 0; y < 8; y++) {
				real.setRGB(x, y, x < 4 ? RED : BLEED_GREEN);
			}
		}
		byte[] imageBytes = pngOf(real);
		// El placement ocupa la mitad DERECHA del sheet PLANEADO (4x4): [2,0]-[4,4].
		TextureGenerationSheet sheet = new TextureGenerationSheet(
				"head-1", "head", List.of(placement("cube-a", FaceName.NORTH, new Vec4(2, 0, 4, 4))), "cabeza", "paleta",
				"", "ref-1", 4, 4); // sheetWidth/Height = 4x4 -- la imagen real decodificada es 8x8

		BufferedImage slice = slicer.slice(imageBytes, sheet).getFirst().image();

		// Sin el fix: recorta [2,0]-[4,4] de la imagen real de 8x8 tal cual ->
		// cae dentro de la mitad ROJA (x<4) -- el bug real. Con el fix,
		// [2,0]-[4,4] se escala x2 -> [4,0]-[8,8] -- la mitad correcta.
		assertOnlyColor(slice, BLEED_GREEN);
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
		// Placement inconsistente a propósito: su rect (0,0,200,200) excede
		// las dimensiones DECLARADAS del propio sheet (100x100) -- un dato
		// mal formado que ShelfBinPacker nunca produciría en la práctica,
		// pero que el slicer debe rechazar igual (defensa en profundidad).
		// Ticket 065: ya no basta con que el placement exceda la imagen
		// real SIN escalar -- ahora cada rect se escala proporcionalmente
		// (imagenReal/sheet declarado) antes de recortar, así que un
		// placement que respeta las dimensiones de su sheet SIEMPRE cabe
		// en la imagen real tras escalar (por diseño). Para seguir
		// probando el caso "no cabe ni así", el placement debe exceder el
		// propio sheet declarado, no solo la imagen real.
		TextureGenerationSheet sheet = new TextureGenerationSheet(
				"head-1", "head", List.of(placement("cube-a", FaceName.NORTH, new Vec4(0, 0, 200, 200))), "cabeza",
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
