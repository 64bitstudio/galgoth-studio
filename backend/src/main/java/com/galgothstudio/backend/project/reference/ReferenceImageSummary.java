package com.galgothstudio.backend.project.reference;

import java.time.Instant;

/**
 * `url` es la ruta relativa SERVIBLE por la propia API
 * (`/api/mobs/{mobId}/references/{id}`), calculada en el momento de
 * responder -- nunca la key interna de S3 (`reference_images.storage_key`
 * en la BD), mismo criterio que `mobs.thumbnail_key` (ticket 023).
 */
public record ReferenceImageSummary(String id, String url, int width, int height, String contentType, Instant createdAt) {
}
