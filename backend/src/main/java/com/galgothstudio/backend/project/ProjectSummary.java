package com.galgothstudio.backend.project;

import java.time.Instant;
import java.util.List;

/**
 * Una tarjeta del dashboard "Mis proyectos" (HU-02). `mobThumbnails` trae como máximo 3 -- `mobCount` es el total real, para el indicador "+N".
 *
 * Ticket 072 (rediseño de "Mis proyectos") -- `status` ("active"/"draft")
 * es DERIVADO, no una columna nueva en `projects` (decisión del Product
 * Owner): "active" si el proyecto tiene al menos un mob en `in_progress`/
 * `ready`, "draft" si todos sus mobs son `draft` o no tiene ninguno. Ver
 * `ProjectService.deriveStatus`.
 *
 * Ticket 073 -- `description` opcional (`null` si el proyecto no tiene).
 * Viaja también en el listado (no solo en el detalle) para que el flujo de
 * "Renombrar" desde `ProjectsDashboard.vue` pueda reenviarla sin cambios al
 * hacer `PATCH` (evita que un rename simple borre la descripción por no
 * conocerla -- ver `RenameProjectRequest`).
 */
public record ProjectSummary(
		String id,
		String name,
		String description,
		int mobCount,
		List<MobThumbnail> mobThumbnails,
		String status,
		Instant createdAt,
		Instant updatedAt) {
}
