package com.galgothstudio.backend.project.texture;

/** Respuesta de `PUT /api/mobs/{mobId}/texture` (ticket 045) -- `storageKey` es SIEMPRE el que el backend calculó, nunca uno propuesto por el cliente. */
public record TextureUploadResponse(String storageKey) {
}
