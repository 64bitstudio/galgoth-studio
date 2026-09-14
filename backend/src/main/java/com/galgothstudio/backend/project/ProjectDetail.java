package com.galgothstudio.backend.project;

import java.time.Instant;

/**
 * Detalle mínimo de un proyecto (HU-01 AC #1: redirección tras crear).
 * El grid completo de mobs + búsqueda + estado por mob es HU-04
 * (ticket 022) -- este ticket solo necesita que la redirección
 * "a su detalle" tenga un recurso real al que apuntar.
 *
 * Ticket 073 (rediseño del detalle de proyecto) -- `description` opcional
 * (`null` en un proyecto que todavía no la tiene, la creación no la pide).
 *
 * Ticket 084 -- `visibility` ("PRIVATE"/"PUBLIC") viaja siempre; el
 * control para cambiarla llega en el ticket 086.
 */
public record ProjectDetail(
		String id, String name, String description, int mobCount, String visibility, Instant createdAt, Instant updatedAt) {
}
