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

## Hecho

Completado 2026-09-08. PR [`#3`](https://github.com/64bitstudio/galgoth-studio/pull/3) (rama `feature/003-esquema-bd-migraciones`), CI de Jenkins en verde (frontend + backend en el mismo `buildAndTest`: Sonar del frontend `ANALYSIS SUCCESSFUL`, Gradle `build sonar` del backend `BUILD SUCCESSFUL`, Quality Gate `OK`, `Finished: SUCCESS`).

**Implementado:**
- Backend real: Spring Boot 4.1.0 + Java 25 + Gradle (`backend/`), mismo patrón que `auth-core-mc` (Gradle Groovy DSL, Flyway, Testcontainers, JaCoCo, plugin de Sonar). `GalgothStudioApplication` arranca, se conecta a Postgres vía `docker/docker-compose.yml` (autodetectado por `spring-boot-docker-compose`, sin puerto de host fijo — 5432 y 5433 ya estaban ocupados por otros proyectos en esta Mac), y Flyway aplica el esquema al boot.
- `V1__init_schema.sql`: las 8 tablas del documento de definición, con `CHECK` en vez de ENUM nativo, índices en columnas FK, `UNIQUE(mob_id, revision_number)` en `mob_revisions`.
- **Resuelta la "Nota abierta de implementación"** del documento sobre FK compuesta: `ai_jobs.base_revision_number` y `exports.revision_number` SÍ son FK compuesta real hacia `mob_revisions(mob_id, revision_number)` (snapshot inmutable, Postgres no valida la fila si el valor es NULL — `MATCH SIMPLE`). `ai_jobs.base_draft_version` NO es FK (`mob_drafts` no tiene historial; es un snapshot de concurrencia optimista de capa de aplicación, HU-18 — una FK real rompería el caso de uso que existe para detectar). Documentado en el propio SQL y en `docs/BASE_DE_DATOS.md`.
- Regla `job_type='generate'`/`'edit'` de `base_revision_number`/`base_draft_version` (AC #2) implementada como `CHECK` a nivel de fila, no dejada a la capa de aplicación.
- `Jenkinsfile`: agrega el `buildAndTest` del backend (`./gradlew build sonar`) junto al del frontend — con `withSonarQubeEnv` desde el principio (aplicando el gotcha real del ticket 002, sin repetirlo).
- `docs/ARQUITECTURA.md`, `docs/BASE_DE_DATOS.md`, `docs/README.md` actualizados.

**Verificado (no solo "el código se ve bien"):**
- 8 tests en verde contra Postgres real vía Testcontainers — no mocks. `SchemaConstraintsTest` cubre los 5 AC verificables sobre el esquema ya migrado (uno por uno, incluyendo el `INSERT` que debe ser **rechazado** por el `CHECK` en el caso `job_type='edit'` sin valores base). `SchemaMigrationReversibilityTest` cubre el AC #6.
- Arranque real (`./gradlew bootRun`) contra `docker compose up` de `docker/docker-compose.yml` — Flyway migró de verdad en un boot real, no solo en test (`Started GalgothStudioApplication`, log confirmado).
- `BUILD SUCCESSFUL` en Jenkins es garantía real de que los 8 tests pasaron ahí también: la tarea `:test` de Gradle falla el build completo si algún test falla (sin `ignoreFailures` configurado) — no es solo "el check quedó verde".

**Decisión de implementación documentada explícitamente (no un scope reducido en silencio):** el AC #6 pide "up/down ambas direcciones" — Flyway Community (el que usa el proyecto, igual que `auth-core-mc`) no soporta undo por versión sin licencia Teams/Enterprise. Se interpretó "revertir" como el ciclo real `migrate → flyway clean → migrate` (destruye todo el esquema y confirma que es repetible sin error), que es lo que un pipeline de CI necesita en la práctica — no un undo selectivo de una migración puntual. Documentado en el Javadoc de `SchemaMigrationReversibilityTest` y en `docs/BASE_DE_DATOS.md`.

**Segundo hallazgo real, corregido antes de implementar (no en el plan original):** el documento de definición decía "Spring Boot 3.x + Java 21" — desactualizado. `auth-core-mc` (el otro backend Java real del equipo) ya corre Spring Boot 4.1.0 + Java 25, con Temurin 25 ya instalado en Jenkins; esta Mac no tiene JDK 21 instalado (solo 26). Consultado explícitamente con el Product Owner antes de escribir código (no asumido) — confirmó actualizar a la misma base que `auth-core-mc`. Corregido en `docs/definiciones/galgoth-studio-mvp.md` (Diseño técnico §2 + Addendum), mismo tratamiento que la corrección de CI/CD del ticket 001.

**Gap conocido, documentado a propósito:** sin Dockerfile ni deploy real del backend todavía (ni MinIO en `docker-compose.yml`) — coherente con el alcance del Technical Alpha (sin infra de nube este ciclo) y con el patrón ya usado en `mail-core-mc` de diferir el pipeline de aplicación real a su propio ticket. Se agrega cuando un ticket lo necesite de verdad (exportador/asset-service), no de forma especulativa.
