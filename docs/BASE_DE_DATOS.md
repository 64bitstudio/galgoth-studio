# Base de datos — Galgoth Studio

Esquema conceptual completo en `docs/definiciones/galgoth-studio-mvp.md` (Diseño técnico §2). Este archivo documenta el esquema **real ya migrado** (ticket `003-esquema-bd-inicial-migraciones`, `done/`).

Motor de migraciones: **Flyway** (community), archivos SQL versionados en `backend/src/main/resources/db/migration/`. `V1__init_schema.sql` crea las 8 tablas; `V2__mobs_soft_delete.sql` (ticket 039) agrega `mobs.deleted_at`. Se aplican automáticamente al arrancar la app (`spring-boot-starter-flyway`) — sin paso manual. **V1 nunca se edita** una vez aplicado a un ambiente real (Flyway valida checksums) -- cambios de esquema van en un `V*` nuevo.

## Tablas

- **`projects`** — proyectos, cada uno con múltiples mobs. `owner_ref`/`deleted_at` nullable (soft delete).
- **`mobs`** — unidad editable independiente. `base_type` y `status` con `CHECK` (no ENUM nativo, más simple de evolucionar). `current_revision_number integer NOT NULL DEFAULT 0`; `thumbnail_key` nullable (asset derivado). **Sin columna `created_by`** — la autoría vive exclusivamente en `mob_revisions.created_by`. `deleted_at` nullable (soft delete, `V2__mobs_soft_delete.sql`, ticket 039) — mismo criterio que `projects.deleted_at`: ningún FK hacia `mobs` tiene `ON DELETE CASCADE`, así que un hard-delete fallaría por violación de FK en cualquier mob con historial real.
- **`mob_revisions`** — historial append-only, inmutable. `UNIQUE(mob_id, revision_number)` (la numeración es por-mob, no global — y es el target de las FK compuestas de abajo). `created_by CHECK IN ('user','ai','system')`.
- **`mob_drafts`** — `mob_id` es la PK (una fila mutable por mob); inexistente hasta el primer commit. `draft_version` sin default — la app siempre debe pasarlo explícito.
- **`reference_images`** — imágenes de concept art, con `width`/`height`/`content_type`.
- **`ai_jobs`** — `job_type CHECK IN ('generate','edit')`. **`CHECK` a nivel de fila** (`chk_ai_jobs_base_values_by_type`): si `job_type='generate'`, `base_revision_number`/`base_draft_version` deben ser `NULL`; si `job_type='edit'`, ambos deben ser `NOT NULL` — la base de datos rechaza la fila si no se cumple, no queda solo a criterio de la capa de aplicación.
- **`ai_job_events`** — `payload_jsonb` nullable, transporta `preview_snapshot`/`preview_operations`. `UNIQUE(job_id, seq)`.
- **`exports`** — `format_version CHECK IN ('v4','v5')`.

## FK compuestas — decisión de este ticket (resuelve la "Nota abierta" del documento de definición)

- **`ai_jobs.base_revision_number` → `mob_revisions(mob_id, revision_number)`**: FK compuesta real. `mob_revisions` es un historial inmutable — la integridad referencial tiene sentido y Postgres no valida la fila cuando `base_revision_number` es `NULL` (comportamiento `MATCH SIMPLE`, el default), que es exactamente el caso `job_type='generate'`.
- **`exports.revision_number` → `mob_revisions(mob_id, revision_number)`**: FK compuesta real, siempre `NOT NULL` (un export siempre apunta a una revisión que existe).
- **`ai_jobs.base_draft_version` NO es FK**: `mob_drafts` no tiene historial (una sola fila mutable por mob) — ese valor es un snapshot para el chequeo de concurrencia optimista de HU-18 en capa de aplicación, no una referencia a una fila que siga existiendo con ese valor exacto. Una FK real rompería el propio caso de uso (detectar que el draft avanzó).

## Desarrollo local

`docker/docker-compose.yml` levanta Postgres 17 (`docker compose up -d` desde `docker/`). `backend/src/main/resources/application.properties` apunta ahí vía `spring.docker.compose.file` — `spring-boot-docker-compose` detecta el contenedor y autoconfigura la conexión, sin puerto fijo (esta Mac ya tenía 5432 y 5433 ocupados por otros proyectos).

Tests: `backend/src/test/.../SchemaConstraintsTest.java` (Testcontainers + JDBC directo, verifica cada regla de negocio del esquema) y `SchemaMigrationReversibilityTest.java` (migrar → `flyway clean` → volver a migrar — Flyway community no tiene "undo" por versión, pago en Teams/Enterprise; esta es la interpretación real de "reversibilidad" para este proyecto).

Ver el diagrama ER conceptual en `docs/definiciones/galgoth-studio-mvp.md` (sección Diagramas) — el SQL real puede tener columnas/constraints adicionales (índices, `CHECK`) no representadas ahí por simplicidad.
