# Base de datos — Galgoth Studio

Esquema completo (con nullability y semántica ya corregidas post-VoBo) en `docs/definiciones/galgoth-studio-mvp.md` (Diseño técnico §2). Este archivo se completa con el detalle real de columnas/índices conforme aterrice el ticket `pending/003-esquema-bd-inicial-migraciones.md`.

## Tablas (resumen)

- **`projects`** — proyectos, cada uno con múltiples mobs.
- **`mobs`** — unidad editable independiente. `current_revision_number` arranca en `0` (NOT NULL DEFAULT 0); `thumbnail_key` es un asset derivado, nullable. Sin columna `created_by` (la autoría vive en `mob_revisions`).
- **`mob_revisions`** — historial append-only, inmutable. Cada fila es un snapshot completo de `MobProjectModel`. Se crea solo por "Usar este modelo" (revisión 1), `Guardar`, o `Apply` de una propuesta de IA.
- **`mob_drafts`** — una fila mutable por mob, inexistente hasta el primer commit. `draft_version` se incrementa solo cuando el contenido cambia materialmente.
- **`reference_images`** — imágenes de concept art subidas para la generación por IA.
- **`ai_jobs`** — jobs de generación/edición por IA. `base_revision_number`/`base_draft_version` son nullable (NULL en jobs `generate`, siempre presentes en jobs `edit`).
- **`ai_job_events`** — eventos de progreso vía SSE, incluye `payload_jsonb` opcional para preview no persistente (`preview_operations`/`preview_snapshot`).
- **`exports`** — historial de exportaciones `.bbmodel` (v4/v5), siempre referenciando una `mob_revisions` específica.

Ver el diagrama ER completo en `docs/definiciones/galgoth-studio-mvp.md` (sección Diagramas).
