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
 *
 * Ticket 084 -- `visibility` ("PRIVATE"/"PUBLIC") viaja siempre; el
 * control para cambiarla llega en el ticket 086.
 *
 * Ticket 086 -- `ownerDisplayName` (`null` si no se capturó al crear).
 * Este mismo record se reutiliza tal cual para `GET /api/explore/projects`
 * (mismo shape que una tarjeta de "Mis proyectos") -- sin un DTO nuevo.
 *
 * Ticket 092 -- `avatarUrl` (`null` si el dueño no tiene avatar subido,
 * ticket 091): ruta servible (`/api/account/avatar/{publicAvatarId}`,
 * pública -- id de servicio aparte desde el ticket 106, nunca el
 * `userId` real, ver `UserProfileEntity.publicAvatarId`), nunca una URL
 * rota. Ver `ProjectService.avatarUrlFor`/`UserProfileService.avatarUrlIfPresent`.
 */
public record ProjectSummary(
		String id,
		String name,
		String description,
		int mobCount,
		List<MobThumbnail> mobThumbnails,
		String status,
		String visibility,
		String ownerDisplayName,
		String avatarUrl,
		Instant createdAt,
		Instant updatedAt) {
}
