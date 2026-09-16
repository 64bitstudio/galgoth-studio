# Base de datos — Galgoth Studio

Esquema conceptual completo en `docs/definiciones/galgoth-studio-mvp.md` (Diseño técnico §2). Este archivo documenta el esquema **real ya migrado** (ticket `003-esquema-bd-inicial-migraciones`, `done/`).

Motor de migraciones: **Flyway** (community), archivos SQL versionados en `backend/src/main/resources/db/migration/`. `V1__init_schema.sql` crea las 8 tablas; `V2__mobs_soft_delete.sql` (ticket 039) agrega `mobs.deleted_at`; `V6__user_profile.sql` (ticket 091) crea `user_profile`; `V7__projects_owner_display_name.sql` (ticket 086) agrega `projects.owner_display_name`. Se aplican automáticamente al arrancar la app (`spring-boot-starter-flyway`) — sin paso manual. **V1 nunca se edita** una vez aplicado a un ambiente real (Flyway valida checksums) -- cambios de esquema van en un `V*` nuevo.

## Tablas

- **`projects`** — proyectos, cada uno con múltiples mobs. `deleted_at` nullable (soft delete). `owner_ref` nullable a nivel de columna (existía desde el ticket 003) pero **siempre poblado en la práctica desde el ticket 084** (el `sub`/user id del JWT de auth-core-mc) -- ningún caller nuevo debería dejarlo null. `visibility VARCHAR(10) NOT NULL DEFAULT 'PRIVATE'` (ticket 084, migración `V5`, `CHECK` a `'PRIVATE'`/`'PUBLIC'`, mismo criterio String-plano-con-CHECK que `mobs.status`/`base_type` -- no un enum nativo). `owner_display_name` nullable (ticket 086, migración `V7`) -- nombre a mostrar en Explorar, denormalizado desde la sesión del frontend al crear (sin FK ni sincronización posterior contra auth-core-mc).
- **`mobs`** — unidad editable independiente. `base_type` y `status` con `CHECK` (no ENUM nativo, más simple de evolucionar). `current_revision_number integer NOT NULL DEFAULT 0`; `thumbnail_key` nullable (asset derivado). **Sin columna `created_by`** — la autoría vive exclusivamente en `mob_revisions.created_by`. `deleted_at` nullable (soft delete, `V2__mobs_soft_delete.sql`, ticket 039) — mismo criterio que `projects.deleted_at`: ningún FK hacia `mobs` tiene `ON DELETE CASCADE`, así que un hard-delete fallaría por violación de FK en cualquier mob con historial real.
- **`mob_revisions`** — historial append-only, inmutable. `UNIQUE(mob_id, revision_number)` (la numeración es por-mob, no global — y es el target de las FK compuestas de abajo). `created_by CHECK IN ('user','ai','system')`.
- **`mob_drafts`** — `mob_id` es la PK (una fila mutable por mob); inexistente hasta el primer commit. `draft_version` sin default — la app siempre debe pasarlo explícito.
- **`reference_images`** — imágenes de concept art, con `width`/`height`/`content_type`.
- **`ai_jobs`** — `job_type CHECK IN ('generate','edit')`. **`CHECK` a nivel de fila** (`chk_ai_jobs_base_values_by_type`): si `job_type='generate'`, `base_revision_number`/`base_draft_version` deben ser `NULL`; si `job_type='edit'`, ambos deben ser `NOT NULL` — la base de datos rechaza la fila si no se cumple, no queda solo a criterio de la capa de aplicación.
- **`ai_job_events`** — `payload_jsonb` nullable, transporta `preview_snapshot`/`preview_operations`. `UNIQUE(job_id, seq)`.
- **`exports`** — `format_version CHECK IN ('v4','v5')`.
- **`user_profile`** (ticket 091, `V6`) — "perfil de producto" por usuario de auth-core-mc: `user_id` (PK) es el `sub` del JWT, sin FK (mismo criterio que `projects.owner_ref`). `avatar_key`/`avatar_content_type` nullable (sin avatar subido todavía); las 3 columnas de preferencias de notificación (`notify_email`/`notify_product_news`/`notify_save_reminders`) `NOT NULL DEFAULT true`. Una fila solo existe si el usuario ya subió avatar o guardó preferencias al menos una vez -- `GET /api/account/profile` devuelve los defaults sin crear ninguna fila. **`public_avatar_id`** (ticket 106, `V8`, hallazgo real de seguridad auth-core-mc#071): `UUID NOT NULL DEFAULT gen_random_uuid()`, único, generado una vez por fila -- el identificador que de verdad viaja en la URL pública del avatar (`GET /api/account/avatar/{publicAvatarId}`), separado a propósito de `user_id` (que es el mismo id de identidad real que auth-core-mc usa en sus JWTs, y por lo tanto nunca debe aparecer en una URL pública sin sesión).

## FK compuestas — decisión de este ticket (resuelve la "Nota abierta" del documento de definición)

- **`ai_jobs.base_revision_number` → `mob_revisions(mob_id, revision_number)`**: FK compuesta real. `mob_revisions` es un historial inmutable — la integridad referencial tiene sentido y Postgres no valida la fila cuando `base_revision_number` es `NULL` (comportamiento `MATCH SIMPLE`, el default), que es exactamente el caso `job_type='generate'`.
- **`exports.revision_number` → `mob_revisions(mob_id, revision_number)`**: FK compuesta real, siempre `NOT NULL` (un export siempre apunta a una revisión que existe).
- **`ai_jobs.base_draft_version` NO es FK**: `mob_drafts` no tiene historial (una sola fila mutable por mob) — ese valor es un snapshot para el chequeo de concurrencia optimista de HU-18 en capa de aplicación, no una referencia a una fila que siga existiendo con ese valor exacto. Una FK real rompería el propio caso de uso (detectar que el draft avanzó).

## Desarrollo local

`docker/docker-compose.yml` levanta Postgres 17 (`docker compose up -d` desde `docker/`). `backend/src/main/resources/application.properties` apunta ahí vía `spring.docker.compose.file` — `spring-boot-docker-compose` detecta el contenedor y autoconfigura la conexión, sin puerto fijo (esta Mac ya tenía 5432 y 5433 ocupados por otros proyectos).

Tests: `backend/src/test/.../SchemaConstraintsTest.java` (Testcontainers + JDBC directo, verifica cada regla de negocio del esquema) y `SchemaMigrationReversibilityTest.java` (migrar → `flyway clean` → volver a migrar — Flyway community no tiene "undo" por versión, pago en Teams/Enterprise; esta es la interpretación real de "reversibilidad" para este proyecto).

Ver el diagrama ER conceptual en `docs/definiciones/galgoth-studio-mvp.md` (sección Diagramas) — el SQL real puede tener columnas/constraints adicionales (índices, `CHECK`) no representadas ahí por simplicidad.
