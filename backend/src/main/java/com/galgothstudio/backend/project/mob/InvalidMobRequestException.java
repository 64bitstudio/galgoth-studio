package com.galgothstudio.backend.project.mob;

/** Nombre vacío o `baseType` fuera de la whitelist de `mobs.base_type` (CHECK constraint, ticket 003) al crear un mob. */
public class InvalidMobRequestException extends RuntimeException {

	public InvalidMobRequestException(String message) {
		super(message);
	}

}
