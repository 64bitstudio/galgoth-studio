package com.galgothstudio.backend.domain.geometry;

import java.util.List;

/**
 * Rechazo atómico de un batch de {@link GeometryOperation}: si CUALQUIER
 * operación del batch falla su validación (payload inválido, referencia
 * no resuelta, dimensión resultante &lt;= 0, etc.), {@link GeometryEngine#apply}
 * lanza esta excepción y NINGUNA operación del batch se aplica -- el
 * modelo de entrada no se toca (AC #2 del ticket 005).
 */
public final class GeometryValidationException extends RuntimeException {

	private final List<String> errors;

	public GeometryValidationException(String error) {
		this(List.of(error));
	}

	public GeometryValidationException(List<String> errors) {
		super(String.join("; ", errors));
		this.errors = List.copyOf(errors);
	}

	public List<String> errors() {
		return errors;
	}

}
