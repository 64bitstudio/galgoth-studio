package com.galgothstudio.backend.project;

import java.time.Instant;

/**
 * Detalle mínimo de un proyecto (HU-01 AC #1: redirección tras crear).
 * El grid completo de mobs + búsqueda + estado por mob es HU-04
 * (ticket 022) -- este ticket solo necesita que la redirección
 * "a su detalle" tenga un recurso real al que apuntar.
 */
public record ProjectDetail(String id, String name, int mobCount, Instant createdAt, Instant updatedAt) {
}
