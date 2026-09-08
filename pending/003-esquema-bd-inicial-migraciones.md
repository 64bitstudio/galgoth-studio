# 003 — Esquema de base de datos inicial + migraciones

**Milestone:** M0 · **Depende de:** 001 · **HUs:** —

## Objetivo
Nace de `docs/definiciones/galgoth-studio-mvp.md` (Diseño técnico §2 y "Estado inicial de un mob"). Crear las migraciones Postgres para `projects`, `mobs`, `mob_revisions`, `mob_drafts`, `reference_images`, `ai_jobs`, `ai_job_events`, `exports`, con la nullability y semántica ya corregidas en el documento — puede avanzar en paralelo a 004 (dominio).

## Criterios de aceptación (TDD)
- Dado un `INSERT` en `mobs` sin especificar `current_revision_number`, cuando se ejecuta, entonces la columna toma el default `0` (NOT NULL DEFAULT 0).
- Dado un `INSERT` en `ai_jobs` con `job_type='generate'`, cuando se omiten `base_revision_number`/`base_draft_version`, entonces la fila se acepta (NULLABLE) — con `job_type='edit'`, un `INSERT` sin esos valores es rechazado por constraint o validado en capa de aplicación (decisión documentada en el ticket de implementación).
- Dado `mob_drafts`, cuando se consulta para un mob recién creado (sin commits), entonces no existe ninguna fila — no hay draft "vacío" pre-creado.
- Dado `ai_job_events`, cuando se inserta una fila con `payload_jsonb`, entonces acepta JSON válido y permite `NULL`.
- Dado `mobs`, cuando se inspecciona su esquema, entonces **no** tiene columna `created_by` (única fuente de autoría: `mob_revisions.created_by`).
- Dado el set de migraciones, cuando se corre en un Postgres limpio y luego se revierte, entonces ambas direcciones (up/down) terminan sin error.
