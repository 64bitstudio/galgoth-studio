-- Ticket 039: "Eliminar" un mob individual (menú de acciones de MobCard,
-- backend nuevo -- ticket 022 nunca lo pidió). Mismo mecanismo EXACTO
-- que `projects.deleted_at` (V1, ticket 021): ningún FK hacia `mobs`
-- (mob_revisions/mob_drafts/reference_images/ai_jobs) tiene
-- `ON DELETE CASCADE` -- un hard-delete fallaría por violación de FK en
-- cualquier mob con historial real. `deleted_at` no nulo == soft-delete,
-- tratado como "no existe" en adelante (mismo criterio que proyectos).
alter table mobs add column deleted_at timestamptz;
