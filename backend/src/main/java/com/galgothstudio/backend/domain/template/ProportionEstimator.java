package com.galgothstudio.backend.domain.template;

import com.galgothstudio.backend.domain.model.ModelIntent;
import com.galgothstudio.backend.domain.model.Proportions;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Ajusta un {@link CanonicalTemplate} según lo que {@link ModelIntent}
 * reporte, dentro de los rangos válidos del template -- ticket 097, HU-3.
 * Función pura, sin red: nunca llama a ningún proveedor de IA.
 *
 * <p>Un valor de proporción fuera de rango se clampa al límite válido y se
 * registra en las advertencias devueltas -- nunca se descarta el intent
 * completo en silencio (regla de equipo #8, "sin parches silenciosos").
 */
public final class ProportionEstimator {

	private ProportionEstimator() {
	}

	public static ProportionAdjustmentResult adjust(CanonicalTemplate template, ModelIntent intent) {
		Map<String, ProportionRange> ranges = template.proportionRanges();
		List<String> warnings = new ArrayList<>();
		Proportions clamped = clamp(intent.proportions(), ranges, warnings);

		ProportionAdjustmentResult applied = template.proportionApplier().apply(template.bones(), template.cuboids(), clamped, ranges);
		warnings.addAll(applied.warnings());
		return new ProportionAdjustmentResult(applied.bones(), applied.cuboids(), List.copyOf(warnings));
	}

	private static Proportions clamp(Proportions proportions, Map<String, ProportionRange> ranges, List<String> warnings) {
		return new Proportions(
				clampField("headScale", proportions.headScale(), ranges, warnings),
				clampField("armLength", proportions.armLength(), ranges, warnings),
				clampField("handScale", proportions.handScale(), ranges, warnings),
				clampField("shoulderWidth", proportions.shoulderWidth(), ranges, warnings));
	}

	private static double clampField(String key, double value, Map<String, ProportionRange> ranges, List<String> warnings) {
		ProportionRange range = ranges.get(key);
		if (range == null) {
			// El template no declara rango para esta clave -- no la usa, se deja pasar tal cual.
			return value;
		}
		if (range.contains(value)) {
			return value;
		}
		double clampedValue = range.clamp(value);
		warnings.add("Proporción '" + key + "' fuera de rango (" + value + "), clampada a " + clampedValue
				+ " [" + range.min() + "," + range.max() + "].");
		return clampedValue;
	}
}
