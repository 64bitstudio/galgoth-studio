package com.galgothstudio.backend.project.draft;

/**
 * Defensa en profundidad (ticket 045, `docs/definiciones/galgoth-studio-fase3-textura.md`
 * Diseño técnico §6): el modelo que se está por persistir en una
 * {@code mob_revision} referencia un {@code storageKey} de textura que
 * NO existe en MinIO. El flujo normal del frontend (fuera de alcance de
 * este ticket) debe esperar la respuesta de `PUT /texture` antes de
 * invocar "Guardar" -- esta excepción cubre el caso de un cliente que,
 * por bug, no respete ese orden: nunca se escribe una fila con una
 * referencia colgante.
 */
public class DanglingTextureReferenceException extends RuntimeException {

	public DanglingTextureReferenceException(String storageKey) {
		super("El storageKey de textura '" + storageKey + "' no existe en el almacenamiento -- no se puede crear la revisión con una referencia colgante.");
	}

}
