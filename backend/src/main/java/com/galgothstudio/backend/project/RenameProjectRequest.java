package com.galgothstudio.backend.project;

/**
 * Body de `PATCH /api/projects/{id}` (Rename).
 *
 * Ticket 073 -- `description` se agrega SIEMPRE explícita (nunca omitida)
 * para evitar la ambigüedad "campo ausente" vs "campo puesto en null": todo
 * caller (el rename simple de `ProjectsDashboard.vue` y el editor completo
 * de `ProjectDetail.vue`) ya tiene el valor actual a mano (`ProjectSummary`/
 * `ProjectDetail` lo traen) y lo reenvía tal cual si no lo está cambiando --
 * así un rename simple nunca borra la descripción por accidente.
 */
public record RenameProjectRequest(String name, String description) {
}
