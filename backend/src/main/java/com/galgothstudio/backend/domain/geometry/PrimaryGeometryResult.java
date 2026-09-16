package com.galgothstudio.backend.domain.geometry;

import com.galgothstudio.backend.domain.model.MobProjectModel;
import java.util.List;

/**
 * Resultado de {@link PrimaryGeometryGenerator#generate} -- ticket 098. El
 * modelo con anatomía primaria ya aplicada, más cualquier advertencia
 * de {@link com.galgothstudio.backend.domain.template.ProportionEstimator}
 * (proporciones fuera de rango, clampadas -- nunca se pierde en silencio).
 */
public record PrimaryGeometryResult(MobProjectModel model, List<String> warnings) {
}
