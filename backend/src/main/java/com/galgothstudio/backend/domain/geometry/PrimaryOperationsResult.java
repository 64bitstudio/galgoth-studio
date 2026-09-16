package com.galgothstudio.backend.domain.geometry;

import java.util.List;

/**
 * Resultado de {@link PrimaryGeometryGenerator#planOperations} -- ticket
 * 099. Operaciones de anatomía primaria SIN aplicar, más las advertencias de
 * proporciones clampadas (ver {@link com.galgothstudio.backend.domain.template.ProportionEstimator}).
 */
public record PrimaryOperationsResult(List<GeometryOperation> operations, List<String> warnings) {
}
