package com.galgothstudio.backend.aiorchestrator.texture;

import static org.assertj.core.api.Assertions.assertThat;

import com.galgothstudio.backend.aiorchestrator.texture.TextureContentValidator.Finding;
import com.galgothstudio.backend.aiorchestrator.texture.TextureContentValidator.FindingType;
import com.galgothstudio.backend.domain.model.FaceName;
import com.galgothstudio.backend.domain.model.TexturePalette;
import com.galgothstudio.backend.domain.model.Vec3;
import com.galgothstudio.backend.domain.model.Vec4;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * `TextureContentValidator` -- ticket 102 (HU-9): alpha coverage,
 * contraste/varianza más allá del caso 100% plano, y comparación
 * best-effort contra la paleta declarada. Todos los hallazgos son
 * advertencias; ninguno hace fallar nada (ver el Javadoc de la clase).
 */
class TextureContentValidatorTest {

	private static final int SIZE = 16;
	private static final TexturePalette VERDE_PALETTE = new TexturePalette("#4A5238", "#8B2E2E");

	private final TextureContentValidator validator = new TextureContentValidator();

	private static TextureSlice sliceOf(BufferedImage image) {
		CuboidFacePlacement placement = new CuboidFacePlacement(
				"cube-a", FaceName.NORTH, new Vec4(0, 0, SIZE, SIZE), new Vec4(0, 0, SIZE, SIZE), new Vec3(4, 4, 4), "front");
		return new TextureSlice(placement, image);
	}

	private static BufferedImage filled(int argb) {
		BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < SIZE; y++) {
			for (int x = 0; x < SIZE; x++) {
				image.setRGB(x, y, argb);
			}
		}
		return image;
	}

	/** Ruido determinista (semilla fija) alrededor de un color base -- el aspecto de un slice REAL con contenido válido. */
	private static BufferedImage noisyAround(int baseR, int baseG, int baseB) {
		BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
		Random random = new Random(42);
		for (int y = 0; y < SIZE; y++) {
			for (int x = 0; x < SIZE; x++) {
				int r = Math.clamp(baseR + random.nextInt(60) - 30, 0, 255);
				int g = Math.clamp(baseG + random.nextInt(60) - 30, 0, 255);
				int b = Math.clamp(baseB + random.nextInt(60) - 30, 0, 255);
				image.setRGB(x, y, 0xFF000000 | (r << 16) | (g << 8) | b);
			}
		}
		return image;
	}

	private static List<FindingType> typesOf(List<Finding> findings) {
		return findings.stream().map(Finding::type).toList();
	}

	@Test
	void unSliceCasiTotalmenteTransparente_seMarcaSospechoso_AC() {
		BufferedImage image = filled(0x00000000); // 100% transparente

		List<Finding> findings = validator.validate(sliceOf(image), TextureDetailLevel.MEDIUM, VERDE_PALETTE);

		assertThat(typesOf(findings)).contains(FindingType.LOW_ALPHA_COVERAGE);
	}

	@Test
	void unSliceCasiTransparente_noAcumulaHallazgosDerivadosDelMismoProblema() {
		BufferedImage image = filled(0x00000000);

		List<Finding> findings = validator.validate(sliceOf(image), TextureDetailLevel.HIGH, VERDE_PALETTE);

		// Sin píxeles pintados, varianza y color dominante no son medidas
		// significativas -- se reporta SOLO la causa real.
		assertThat(typesOf(findings)).containsExactly(FindingType.LOW_ALPHA_COVERAGE);
	}

	@Test
	void unSliceDeColorPlano_seMarcaComoContenidoUniforme_AC() {
		BufferedImage image = filled(0xFF4A5238); // opaco, un solo color

		List<Finding> findings = validator.validate(sliceOf(image), TextureDetailLevel.MEDIUM, VERDE_PALETTE);

		assertThat(typesOf(findings)).contains(FindingType.UNIFORM_CONTENT);
	}

	/** AC: "contraste anormalmente bajo respecto a lo esperado para el nivel de detalle" -- no solo el caso 100% plano. */
	@Test
	void contrasteBajoPeroNoPlano_seMarcaSoloSiSePidioDetalleAlto_AC() {
		BufferedImage lowContrast = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < SIZE; y++) {
			for (int x = 0; x < SIZE; x++) {
				// Variación real pero pobre: 16 niveles en el canal rojo entre
				// columnas -- desviación de luminancia ~2.4, por encima del piso
				// de "uniforme" (1.0) y por debajo del exigido para HIGH (4.0).
				int shade = 0x4A + (x % 2) * 16;
				lowContrast.setRGB(x, y, 0xFF000000 | (shade << 16) | (0x52 << 8) | 0x38);
			}
		}

		List<Finding> conDetalleAlto = validator.validate(sliceOf(lowContrast), TextureDetailLevel.HIGH, null);
		List<Finding> conDetalleBajo = validator.validate(sliceOf(lowContrast), TextureDetailLevel.LOW, null);

		assertThat(typesOf(conDetalleAlto)).contains(FindingType.LOW_CONTRAST_FOR_DETAIL_LEVEL);
		// Con detalle BAJO, un resultado casi plano es una respuesta legítima.
		assertThat(typesOf(conDetalleBajo)).doesNotContain(FindingType.LOW_CONTRAST_FOR_DETAIL_LEVEL);
	}

	@Test
	void unSliceConContenidoRealAcordeALaPaleta_noGeneraNingunHallazgo_AC_sinFalsosPositivos() {
		BufferedImage image = noisyAround(0x4A, 0x52, 0x38); // alrededor del dominante declarado

		List<Finding> findings = validator.validate(sliceOf(image), TextureDetailLevel.HIGH, VERDE_PALETTE);

		assertThat(findings).isEmpty();
	}

	@Test
	void unColorDominanteMuyLejosDeLaPaletaDeclarada_seMarcaComoDivergencia_AC() {
		BufferedImage magenta = noisyAround(0xFF, 0x00, 0xFF); // nada que ver con #4A5238/#8B2E2E

		List<Finding> findings = validator.validate(sliceOf(magenta), TextureDetailLevel.MEDIUM, VERDE_PALETTE);

		assertThat(typesOf(findings)).contains(FindingType.PALETTE_DIVERGENCE);
	}

	@Test
	void sinPaletaDeclarada_elChequeoDePaletaSeOmiteSinFallarNada_AC_bestEffort() {
		BufferedImage magenta = noisyAround(0xFF, 0x00, 0xFF);

		List<Finding> findings = validator.validate(sliceOf(magenta), TextureDetailLevel.MEDIUM, null);

		assertThat(typesOf(findings)).doesNotContain(FindingType.PALETTE_DIVERGENCE);
	}

	@Test
	void unaPaletaConHexMalFormado_seOmiteSinFallar_elPlanVieneDeUnLlmNuncaSeAsumeBienFormado() {
		BufferedImage magenta = noisyAround(0xFF, 0x00, 0xFF);
		TexturePalette rota = new TexturePalette("no-es-un-hex", null);

		List<Finding> findings = validator.validate(sliceOf(magenta), TextureDetailLevel.MEDIUM, rota);

		assertThat(typesOf(findings)).doesNotContain(FindingType.PALETTE_DIVERGENCE);
	}

	@Test
	void cadaHallazgoIdentificaElCuboidYLaCaraConcretos_paraQueElLogSirvaDeDiagnostico() {
		List<Finding> findings = validator.validate(sliceOf(filled(0x00000000)), TextureDetailLevel.MEDIUM, VERDE_PALETTE);

		assertThat(findings).isNotEmpty().first().satisfies(finding -> assertThat(finding.cuboidId()).isEqualTo("cube-a"));
		assertThat(findings.getFirst().face()).isEqualTo(FaceName.NORTH.name());
		assertThat(findings.getFirst().detail()).isNotBlank();
	}

}
