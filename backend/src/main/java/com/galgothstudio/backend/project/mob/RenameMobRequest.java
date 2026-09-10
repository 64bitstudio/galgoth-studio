package com.galgothstudio.backend.project.mob;

/** Body de `PATCH /api/mobs/{mobId}` (ticket 039, Renombrar). */
public record RenameMobRequest(String name) {
}
