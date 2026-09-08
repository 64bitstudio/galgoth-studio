package com.galgothstudio.backend.domain.model;

/**
 * Serializa como objeto JSON {"north":{...}, "south":{...}, ...} -- los
 * nombres de los componentes del record SON las claves JSON (soporte
 * nativo de Jackson para records), igual que
 * contracts/schemas/mob-project-model.schema.json.
 */
public record CuboidFaces(Face north, Face south, Face east, Face west, Face up, Face down) {
}
