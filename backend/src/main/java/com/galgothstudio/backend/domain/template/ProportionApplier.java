package com.galgothstudio.backend.domain.template;

import com.galgothstudio.backend.domain.model.Proportions;
import java.util.List;
import java.util.Map;

/**
 * Traduce {@link Proportions} (ya clampadas por {@link ProportionEstimator})
 * a bones/cuboides ajustados, para un template concreto -- ticket 097, HU-3.
 *
 * <p>Cada {@link CanonicalTemplate} provee su propia implementación (ej.
 * {@link HumanoidCanonicalTemplate} sabe qué significa "brazos más largos"
 * para SU jerarquía) -- así agregar un template nuevo (arachnid/quadruped/
 * flying, iteración 2) no requiere tocar {@link ProportionEstimator} ni
 * ningún otro código compartido.
 */
@FunctionalInterface
public interface ProportionApplier {

	/**
	 * @param baseBones bones base del template, sin ajustar (multiplicador 1.0 implícito).
	 * @param baseCuboids cuboides base del template, sin ajustar.
	 * @param proportions proporciones YA clampadas a los rangos válidos del template.
	 * @param ranges los mismos rangos usados para clampar -- disponibles por si el applier
	 *               necesita reconstruir algún límite derivado (no debería volver a clampar).
	 * @return bones/cuboides con las proporciones aplicadas + advertencias propias del
	 *         applier (ej. una combinación de valores válidos individualmente pero que
	 *         igual requirió un ajuste adicional para mantener la anatomía contigua).
	 */
	ProportionAdjustmentResult apply(
			List<TemplateBoneSpec> baseBones,
			List<TemplateCuboidSpec> baseCuboids,
			Proportions proportions,
			Map<String, ProportionRange> ranges);
}
