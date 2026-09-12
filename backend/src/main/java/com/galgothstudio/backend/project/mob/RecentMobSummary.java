package com.galgothstudio.backend.project.mob;

import java.time.Instant;

/**
 * Una tarjeta de "Continuar trabajando" (Inicio, ticket 071). Mismo shape
 * que {@link MobSummary} más `projectId` -- a diferencia del resto del
 * CRUD de mobs (siempre dentro de un proyecto ya conocido por la URL),
 * este resumen cruza TODOS los proyectos, así que el frontend necesita
 * `projectId` para poder navegar al editor (`/projects/{projectId}/mobs/{mobId}/edit`).
 */
public record RecentMobSummary(
		String id, String projectId, String name, String baseType, String status, String thumbnailKey, Instant updatedAt) {
}
