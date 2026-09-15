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
 *
 * Ticket 086 -- `ownerDisplayName` (`null` si no se capturó al crear).
 * El grid de mobs de un proyecto público ajeno se resuelve con el mismo
 * `GET /api/projects/{id}/mobs` que ya usa "Mis proyectos" -- desde el
 * ticket 085 ese endpoint ya es de lectura pública para un proyecto
 * `PUBLIC`, así que no hace falta inlinear los mobs acá también.
 */
public record ProjectDetail(
		String id,
		String name,
		String description,
		int mobCount,
		String visibility,
		String ownerDisplayName,
		Instant createdAt,
		Instant updatedAt) {
}
