package com.galgothstudio.backend.project.geometry;

import com.galgothstudio.backend.domain.geometry.GeometryOperation;

/**
 * Error de dominio {@code UNSUPPORTED_GEOMETRY_OPERATION} (ticket 043,
 * Diseño técnico §2): {@code POST /api/mobs/{mobId}/geometry/apply} solo
 * acepta {@code createCuboid}/{@code resizeCuboid}/{@code removeCuboid} --
 * las únicas 3 operaciones que afectan UV. {@code moveCuboid}/
 * {@code rotateCuboid}/{@code setBonePivot}/{@code setBoneRotation}/
 * {@code parentBone}/{@code createBone} NUNCA pasan por este endpoint
 * (siguen 100% client-side + autosave debounced, como siempre) -- se
 * rechazan acá explícitamente, nunca en silencio.
 */
public final class UnsupportedGeometryApplyOperationException extends RuntimeException {

	public UnsupportedGeometryApplyOperationException(GeometryOperation operation) {
		super(
				"UNSUPPORTED_GEOMETRY_OPERATION: '" + operation.getClass().getSimpleName()
						+ "' no está permitida en POST /geometry/apply -- solo createCuboid/resizeCuboid/removeCuboid "
						+ "pasan por este endpoint (moveCuboid/rotateCuboid/pivot y el resto de operaciones de bones "
						+ "no afectan UV y siguen 100% client-side).");
	}

}
