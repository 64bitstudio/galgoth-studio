package com.galgothstudio.backend.project.geometry;

import com.galgothstudio.backend.domain.geometry.GeometryOperation;
import java.util.List;

/**
 * Body de {@code POST /api/mobs/{mobId}/geometry/apply} (ticket 043,
 * Diseño técnico §2/§15 de `docs/definiciones/galgoth-studio-fase3-textura.md`).
 *
 * @param operations batch de operaciones a aplicar -- whitelist CERRADA de
 *        este endpoint (solo {@code createCuboid}/{@code resizeCuboid}/
 *        {@code removeCuboid}, ver {@link MobGeometryApplyService}); usa el
 *        mismo tipo/deserialización que el Geometry Engine (005), así que
 *        un {@code op} fuera de las 9 operaciones conocidas ya falla en
 *        Jackson antes de llegar acá.
 * @param confirmPaintLoss si {@code true}, confirma explícitamente aplicar
 *        un resize que perdería contenido {@code PAINTED} -- reenvío de la
 *        MISMA operación tras el modal de confirmación del frontend.
 */
public record MobGeometryApplyRequest(List<GeometryOperation> operations, boolean confirmPaintLoss) {
}
