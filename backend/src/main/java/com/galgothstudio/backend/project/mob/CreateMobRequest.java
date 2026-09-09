package com.galgothstudio.backend.project.mob;

/** Body de `POST /api/projects/{projectId}/mobs` (HU-03, "Agregar mob"). */
public record CreateMobRequest(String name, String baseType) {
}
