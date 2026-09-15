package com.galgothstudio.backend.project;

/** Body de `PATCH /api/projects/{id}/visibility` (ticket 086). {@code visibility} debe ser exactamente `"PRIVATE"` o `"PUBLIC"` (mismo `CHECK` de la columna, `V5`). */
public record ChangeVisibilityRequest(String visibility) {
}
