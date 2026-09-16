package com.galgothstudio.backend.aiorchestrator.texture;

import com.galgothstudio.backend.domain.model.TexturePalette;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/**
 * Valida el CONTENIDO de cada {@link TextureSlice} recortado antes de
 * componerlo sobre el atlas -- ticket 102 (HU-9 de
 * `docs/definiciones/anatomia-por-capas-generacion-mobs.md`). 100%
 * determinista, sin IA, sin red: solo lee los píxeles ya recortados.
 *
 * <p><b>Ningún chequeo bloquea el job</b> (AC explícito de HU-9): todos
 * devuelven {@link Finding}s que el orquestador loguea como advertencia
 * -- mismo criterio que {@code MobGenerationService.logRejections}/
 * {@code logGenerationWarnings} (099/097), donde exponerlas como
 * `generationWarnings` estructurados en la API queda para el ticket 104.
 * Un slice sospechoso igual se compone: la señal es para diagnóstico, no
 * para descartar contenido que quizá sea legítimo.
 *
 * <p><b>Umbrales, y por qué son estos</b>: se eligen deliberadamente
 * CONSERVADORES (marcan solo lo claramente degenerado) porque el AC de
 * HU-9 exige explícitamente que no haya falsos positivos contra fixtures
 * reales con contenido válido. Un slice de Minecraft legítimo puede ser
 * perfectamente plano en color (una cara interior de un cuboid de un
 * solo tono es normal), así que el chequeo de contraste NO marca
 * "plano" a secas -- solo marca plano CUANDO el nivel de detalle pedido
 * era alto, donde sí es señal de que la IA no generó lo que se le pidió.
 */
@Component
public class TextureContentValidator {

	/**
	 * Un píxel cuenta como "pintado" a partir de este alpha (0-255) --
	 * 8 deja fuera el ruido casi-transparente de un resampling bilineal
	 * (ver {@code TextureCompositorService}) sin descartar contenido
	 * translúcido real.
	 */
	static final int OPAQUE_ALPHA_THRESHOLD = 8;

	/**
	 * Por debajo de esta proporción de píxeles pintados, un slice que se
	 * pidió CON contenido se marca sospechoso. 5% es el piso de "casi
	 * 100% transparente" del AC -- no un "debería estar lleno": hay caras
	 * legítimamente casi vacías (recortes, siluetas finas).
	 */
	static final double MIN_ALPHA_COVERAGE = 0.05;

	/**
	 * Desviación estándar de luminancia (0-255) por debajo de la cual el
	 * contenido se considera prácticamente uniforme. 1.0 solo captura el
	 * caso degenerado real (un color plano, o dos tonos indistinguibles).
	 */
	static final double UNIFORM_LUMINANCE_STDDEV = 1.0;

	/**
	 * Piso de desviación estándar exigido SOLO cuando se pidió
	 * {@code TextureDetailLevel.HIGH} -- "contraste anormalmente bajo
	 * respecto a lo esperado para el nivel de detalle" del AC. Para
	 * LOW/MEDIUM no se exige contraste mínimo alguno (un resultado plano
	 * es una respuesta legítima a "detalle bajo").
	 */
	static final double MIN_LUMINANCE_STDDEV_HIGH_DETAIL = 4.0;

	/**
	 * Distancia euclídea máxima (en RGB 0-255, rango teórico 0-441) entre
	 * el color dominante del slice y el color más cercano de la paleta
	 * declarada. 160 es deliberadamente laxo: la paleta declara
	 * dominante+acento de la referencia COMPLETA, no de esta cara -- una
	 * cara legítima puede alejarse bastante (sombra, material distinto);
	 * solo se marca la divergencia FUERTE que pide el AC.
	 */
	static final double MAX_PALETTE_DISTANCE = 160.0;

	/** Tipo de hallazgo -- enum cerrado (nunca string libre) para que el ticket 104 pueda mapearlos a `generationWarnings` estructurados sin volver a parsear texto. */
	public enum FindingType {
		LOW_ALPHA_COVERAGE,
		UNIFORM_CONTENT,
		LOW_CONTRAST_FOR_DETAIL_LEVEL,
		PALETTE_DIVERGENCE
	}

	/**
	 * @param cuboidId   cuboid dueño de la cara evaluada.
	 * @param face       cara evaluada (nombre legible, ya resuelto por el caller).
	 * @param type       categoría del hallazgo.
	 * @param detail     descripción legible con los números concretos medidos -- para el log de diagnóstico.
	 */
	public record Finding(String cuboidId, String face, FindingType type, String detail) {
	}

	/**
	 * Evalúa un slice. {@code expectedPalette} puede ser {@code null}
	 * (chequeo de paleta omitido sin fallar nada -- AC explícito:
	 * "best-effort").
	 */
	public List<Finding> validate(TextureSlice slice, TextureDetailLevel detailLevel, TexturePalette expectedPalette) {
		BufferedImage image = slice.image();
		String cuboidId = slice.placement().cuboidId();
		String face = slice.placement().face().name();
		List<Finding> findings = new ArrayList<>();

		PixelStats stats = PixelStats.of(image);
		if (stats.totalPixels() == 0) {
			return findings; // Slice de área cero -- `TextureGenerationSheetPlanner` ya los descarta antes (ticket 064); acá simplemente no hay nada que medir.
		}

		double coverage = stats.paintedPixels() / (double) stats.totalPixels();
		if (coverage < MIN_ALPHA_COVERAGE) {
			findings.add(new Finding(cuboidId, face, FindingType.LOW_ALPHA_COVERAGE, String.format(
					"alpha coverage %.1f%% (< %.1f%% esperado para una cara que se pidió pintada)", coverage * 100, MIN_ALPHA_COVERAGE * 100)));
			return findings; // Sin píxeles pintados suficientes, varianza y color dominante no son medidas significativas -- no se acumulan hallazgos derivados del mismo problema.
		}

		double stdDev = stats.luminanceStdDev();
		if (stdDev < UNIFORM_LUMINANCE_STDDEV) {
			findings.add(new Finding(cuboidId, face, FindingType.UNIFORM_CONTENT, String.format(
					"contenido prácticamente uniforme (desviación de luminancia %.2f)", stdDev)));
		} else if (detailLevel == TextureDetailLevel.HIGH && stdDev < MIN_LUMINANCE_STDDEV_HIGH_DETAIL) {
			findings.add(new Finding(cuboidId, face, FindingType.LOW_CONTRAST_FOR_DETAIL_LEVEL, String.format(
					"contraste bajo (desviación de luminancia %.2f) para el nivel de detalle HIGH solicitado", stdDev)));
		}

		addPaletteFindingIfDiverges(findings, cuboidId, face, stats, expectedPalette);
		return findings;
	}

	private static void addPaletteFindingIfDiverges(
			List<Finding> findings, String cuboidId, String face, PixelStats stats, TexturePalette expectedPalette) {
		if (expectedPalette == null) {
			return;
		}
		Rgb dominant = stats.averagePaintedRgb();
		closestPaletteDistance(dominant, expectedPalette)
				.filter(distance -> distance > MAX_PALETTE_DISTANCE)
				.ifPresent(distance -> findings.add(new Finding(cuboidId, face, FindingType.PALETTE_DIVERGENCE, String.format(
						"color dominante #%02X%02X%02X a distancia %.0f de la paleta declarada (máx. %.0f)", dominant.r(), dominant.g(),
						dominant.b(), distance, MAX_PALETTE_DISTANCE))));
	}

	/** Vacío si la paleta no trae ningún color parseable -- chequeo omitido, nunca un fallo (AC "best-effort"). */
	private static Optional<Double> closestPaletteDistance(Rgb dominant, TexturePalette palette) {
		Double best = null;
		for (String hex : Stream.of(palette.dominantColorHex(), palette.accentColorHex()).toList()) {
			Optional<Rgb> parsed = Rgb.parseHex(hex);
			if (parsed.isEmpty()) {
				continue;
			}
			double distance = dominant.distanceTo(parsed.get());
			if (best == null || distance < best) {
				best = distance;
			}
		}
		return Optional.ofNullable(best);
	}

	/** Color RGB 0-255 -- un record, nunca un {@code int[]}: un array como valor de retorno/campo arrastra null-checks y equals/hashCode por referencia (S1168/S6218). */
	private record Rgb(int r, int g, int b) {

		/** Vacío para cualquier valor no parseable como `#RRGGBB` -- el plan de textura viene de un LLM (052), nunca se asume bien formado. */
		static Optional<Rgb> parseHex(String hex) {
			if (hex == null) {
				return Optional.empty();
			}
			String value = hex.startsWith("#") ? hex.substring(1) : hex;
			if (value.length() != 6) {
				return Optional.empty();
			}
			try {
				return Optional.of(new Rgb(
						Integer.parseInt(value.substring(0, 2), 16), Integer.parseInt(value.substring(2, 4), 16),
						Integer.parseInt(value.substring(4, 6), 16)));
			} catch (NumberFormatException _) {
				return Optional.empty();
			}
		}

		double distanceTo(Rgb other) {
			return Math.sqrt(
					Math.pow(r - (double) other.r(), 2) + Math.pow(g - (double) other.g(), 2) + Math.pow(b - (double) other.b(), 2));
		}
	}

	/** Estadísticas de un solo barrido de píxeles -- recorrer la imagen una vez por slice, no una vez por chequeo. */
	private record PixelStats(int totalPixels, int paintedPixels, double luminanceStdDev, Rgb averagePaintedRgb) {

		static PixelStats of(BufferedImage image) {
			int total = image.getWidth() * image.getHeight();
			int painted = 0;
			long sumR = 0;
			long sumG = 0;
			long sumB = 0;
			double sumLum = 0;
			double sumLumSquared = 0;
			for (int y = 0; y < image.getHeight(); y++) {
				for (int x = 0; x < image.getWidth(); x++) {
					int argb = image.getRGB(x, y);
					if (((argb >>> 24) & 0xFF) < OPAQUE_ALPHA_THRESHOLD) {
						continue;
					}
					int r = (argb >> 16) & 0xFF;
					int g = (argb >> 8) & 0xFF;
					int b = argb & 0xFF;
					painted++;
					sumR += r;
					sumG += g;
					sumB += b;
					// Luminancia perceptual estándar (Rec. 601) -- la misma que usa
					// cualquier conversión a escala de grises; medir varianza sobre
					// RGB crudo le daría al verde un peso arbitrario.
					double luminance = 0.299 * r + 0.587 * g + 0.114 * b;
					sumLum += luminance;
					sumLumSquared += luminance * luminance;
				}
			}
			if (painted == 0) {
				return new PixelStats(total, 0, 0, new Rgb(0, 0, 0));
			}
			double meanLum = sumLum / painted;
			double variance = Math.max(0, sumLumSquared / painted - meanLum * meanLum);
			Rgb average = new Rgb((int) (sumR / painted), (int) (sumG / painted), (int) (sumB / painted));
			return new PixelStats(total, painted, Math.sqrt(variance), average);
		}
	}

}
