package com.galgothstudio.backend.project;

/**
 * Body de `POST /api/projects`. HU-01 AC #3: solo se pide nombre -- sin
 * campos técnicos de IA/geometría.
 *
 * <p>Ticket 086 -- `ownerDisplayName` opcional: el frontend real siempre
 * lo manda (conoce `sessionStore.user.nombre`/`apellidos` en este
 * momento), pero no se exige a nivel de contrato -- un caller que no lo
 * envíe (Postman, un test) simplemente deja el proyecto sin autor
 * visible en Explorar, nunca falla la creación por esto.
 */
public record CreateProjectRequest(String name, String ownerDisplayName) {
}
