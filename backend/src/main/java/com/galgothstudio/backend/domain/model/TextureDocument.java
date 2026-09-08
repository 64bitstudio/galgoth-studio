package com.galgothstudio.backend.domain.model;

/**
 * Sin lógica/UI funcional este ciclo (Fase 3) -- presente en el esquema
 * por diseño (AC #5 del ticket 004).
 *
 * @param storageKey clave del PNG en MinIO -- null hasta que exista una
 *                    textura real o el placeholder de export (ticket 011).
 */
public record TextureDocument(int width, int height, String storageKey) {
}
