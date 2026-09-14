# 084 — Ownership real de proyectos al crear/listar

## Objetivo
Ver `docs/definiciones/proyectos-por-usuario-y-explorar.md` (VoBo de
Marco recibido) — HU-1 y HU-2. Hoy `ProjectEntity.owner_ref` existe en
el esquema pero `ProjectService.create()` siempre lo deja en `null`, y
`ProjectService.list()` devuelve TODOS los proyectos de TODOS los
usuarios sin filtrar. Este ticket es la base de todo lo demás: sin
ownership real poblado, ningún control de acceso (ticket `085`) ni
visibilidad (ticket `086`) tiene sentido.

**Depende de:** nada nuevo — reutiliza el `JwtDecoder` ya construido en
el ticket `077`.

## Alcance
- **Sí incluye:**
  - Migración `V5`: `projects.visibility VARCHAR(10) NOT NULL DEFAULT 'PRIVATE'`
    + `CHECK (visibility IN ('PRIVATE', 'PUBLIC'))` (ver sección "Diseño
    técnico → 1" del documento de definición).
  - `ProjectEntity.visibility` (campo nuevo) + getter/setter.
  - `ProjectController.create`/`ProjectService.create(name, ownerId)`:
    toma el `sub` del JWT (`@AuthenticationPrincipal Jwt jwt`) y lo graba
    como `ownerRef`; `visibility` nace siempre `PRIVATE`.
  - `ProjectService.list(ownerId)`: nuevo método de repositorio
    (`findByOwnerRefAndDeletedAtIsNullOrderByUpdatedAtDesc`) — reemplaza
    el `list()` global. `ProjectController.list` exige `Jwt` no nulo
    (401 si falta).
  - `GET /api/mobs/recent` (`MobRecentController`/servicio detrás):
    mismo criterio — filtra por dueño autenticado, deja de ser global.
  - Limpieza de datos: borrar (soft-delete) el único proyecto de DEV con
    `owner_ref IS NULL` — es dato de prueba anterior a que existiera
    ownership, no hay equivalente en QA.
- **No incluye:** proteger las rutas de mutación/lectura de proyectos
  ajenos (ticket `085`), visibilidad pública/Explorar (ticket `086`),
  nada de frontend (tickets `087`/`088`).

## Criterios de aceptación (TDD)
- `ProjectServiceTest`: crear un proyecto autenticado graba `ownerRef` =
  el `sub` del JWT y `visibility = PRIVATE`.
- `list(ownerId)` solo devuelve proyectos de ese dueño, nunca los de
  otro — test explícito con dos usuarios distintos.
- Suite completa del backend en verde.
- Verificación en vivo contra DEV: crear un proyecto autenticado, leer
  la fila directamente en la base, confirmar `owner_ref`/`visibility`
  reales (no simulados).

## Hecho
- Migración `V5`: `projects.visibility VARCHAR(10) NOT NULL DEFAULT 'PRIVATE'`
  + `CHECK (visibility IN ('PRIVATE', 'PUBLIC'))`. `ProjectEntity.visibility`
  nuevo campo; `ProjectDetail`/`ProjectSummary` lo exponen.
- `ProjectController.create`/`list` y `MobRecentController.list` ahora
  toman `@AuthenticationPrincipal Jwt jwt` y exigen `sub` real —
  `UnauthenticatedRequestException` → `401 UNAUTHENTICATED` si falta
  (chequeo puntual en el controlador, no un cambio de `SecurityConfig`
  todavía — eso es el ticket `085`).
- `ProjectService.create(name, ownerId)` graba `owner_ref`/`visibility =
  PRIVATE` reales; `ProjectService.list(ownerId)` y
  `MobService.listRecentAcrossProjects(limit, ownerId)` filtran por
  dueño — reemplazan el listado global sin filtrar (hallazgo real: hasta
  este ticket cualquiera veía los proyectos/mobs recientes de cualquiera).
  El método de repositorio sin filtro (`findByDeletedAtIsNullOrderByUpdatedAtDesc`)
  se retiró a propósito, mismo criterio que el ticket 039 aplicó en
  `MobRepository`.
- `ProjectService.duplicate()`: la copia siempre nace `PRIVATE`
  (decisión de implementación: duplicar nunca debe publicar nada por
  accidente), conserva el `ownerRef` del original — sin cambios de
  comportamiento más allá de eso.
- Tests: 451/451 en verde (suite completa). Nuevos: `401` sin auth en
  create/list/recientes, un proyecto nuevo nace `PRIVATE`, "Mis
  proyectos" y "mobs recientes" solo incluyen los del dueño autenticado
  (dos dueños distintos, verificado explícitamente). `SchemaConstraintsTest`
  gana 2 tests (`visibility` default y `CHECK`). `SchemaMigrationReversibilityTest`
  actualizado a versión `5`. Se agregó `spring-security-test` (primer
  test de controlador que simula un JWT real).
- `docs/API.md`/`docs/BASE_DE_DATOS.md` actualizados con el nuevo
  contrato y columna.
- **Verificación en vivo contra DEV**: pendiente (se completa junto con
  el deploy de este PR).
